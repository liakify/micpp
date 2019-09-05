#include <jni.h>
#include <string>
#include <android/log.h>
#include <AndroidIO/SuperpoweredAndroidAudioIO.h>
#include <SuperpoweredSimple.h>
#include <SuperpoweredRecorder.h>
#include <SuperpoweredAdvancedAudioPlayer.h>
#include <SuperpoweredCompressor.h>
#include <SuperpoweredDecoder.h>
#include <SuperpoweredReverb.h>
#include <SuperpoweredLimiter.h>
#include <Superpowered3BandEQ.h>
#include <SuperpoweredCPU.h>
#include <SuperpoweredFilter.h>
#include <malloc.h>
#include <SLES/OpenSLES_AndroidConfiguration.h>
#include <SLES/OpenSLES.h>
#include <memory>


#define log_write __android_log_write
#define log_print __android_log_print

//Init Recorder and Player IO

static std::unique_ptr<SuperpoweredAndroidAudioIO> audioIO;
static std::unique_ptr<SuperpoweredAndroidAudioIO> playerAudioIO;
static std::unique_ptr<SuperpoweredRecorder> recorder;
static std::unique_ptr<SuperpoweredAdvancedAudioPlayer> player;

//Init Audio FX
static std::unique_ptr<SuperpoweredReverb> reverb;
static std::unique_ptr<SuperpoweredCompressor> outputGain;
static std::unique_ptr<SuperpoweredLimiter> limiter;
static std::unique_ptr<Superpowered3BandEQ> equalizer;
static std::unique_ptr<SuperpoweredFilter> filter;

/*
static SuperpoweredAndroidAudioIO *audioIO;
static SuperpoweredAndroidAudioIO *playerAudioIO;
static SuperpoweredRecorder *recorder;
static SuperpoweredAdvancedAudioPlayer *player;

static SuperpoweredReverb *reverb;
static SuperpoweredCompressor *outputGain;
static SuperpoweredLimiter *limiter;
static Superpowered3BandEQ *equalizer;
static SuperpoweredFilter *filter;
*/



//Init Separate buffers for Recording and Playback
static float *floatBuffer;
static float *playerFloatBuffer;

static short *shortBuffer;
static int frames;

//Init Decoder to write to file
static SuperpoweredDecoder *decoder;

//Playback start and end points in ms
double *start;
double *end;

// Called by the player.
static void playerEventCallback (
        void * __unused clientData,
        SuperpoweredAdvancedAudioPlayerEvent event,
        void *value
) {
    switch (event) {
        case SuperpoweredAdvancedAudioPlayerEvent_LoadSuccess:
            break;
        case SuperpoweredAdvancedAudioPlayerEvent_LoadError:
            log_print(ANDROID_LOG_ERROR, "PlayerExample", "Open error: %s", (char *)value);
            break;
        case SuperpoweredAdvancedAudioPlayerEvent_EOF:
            player->seek(0);    // loop track
            break;
        default:;
    };
}

// This is called periodically by the RECORDING audio engine.
static bool audioProcessing (
        void * __unused clientdata, // custom pointer
        short int *audio,           // buffer of interleaved samples
        int numberOfFrames,         // number of frames to process
        int __unused samplerate     // sampling rate
) {
    SuperpoweredShortIntToFloat(audio, floatBuffer, (unsigned int)numberOfFrames);
    recorder->process(floatBuffer, (unsigned int)numberOfFrames);
    frames = numberOfFrames;
    shortBuffer = audio;
    return false;
}

// Helper function to init all FX parameters
static void initFXParameters() {
        filter->enable(true);
        filter->setParametricParameters(50.0f, 1.388484f, 0.0f);
        equalizer->enable(true);
        equalizer->bands[0] = 1.0f; //Low set to flat
        equalizer->bands[1] = 1.0f; //Mid set to flat
        equalizer->bands[2] = 1.0f; //Hi set to flat
        limiter->enable(true);
        limiter->ceilingDb = 0.0f;
        limiter->thresholdDb = -0.2f;
        outputGain->enable(false);
        reverb->enable(true);
        reverb->setRoomSize(0.5f);
        reverb->setMix(0.0f);
        reverb->setWidth(0.3f);
        reverb->setPredelay(12.0f);
        reverb->setLowCut(100);
        reverb->setDamp(0.2f);
}

// Get buffer of current audio being recorded for drawing waveform in Java
extern "C" JNIEXPORT jshortArray
Java_com_superpowered_recorder_RecordFragment_getBuffer (
        JNIEnv * env,
        jobject __unused obj
) {
    jshortArray result;
    result = env->NewShortArray(frames);
    env->SetShortArrayRegion(result, 0, frames, shortBuffer);
    return result;
}

//This is called periodically by the PLAYBACK audio engine.
static bool playerAudioProcessing (
        void * __unused clientdata, // custom pointer
        short int *audio,           // buffer of interleaved samples
        int numberOfFrames,         // number of frames to process
        int __unused samplerate     // sampling rate
) {

    if (player->process(playerFloatBuffer, false, (unsigned int)numberOfFrames)) {
        equalizer->process(playerFloatBuffer, playerFloatBuffer, (unsigned int)numberOfFrames);
        filter->process(playerFloatBuffer, playerFloatBuffer, (unsigned int)numberOfFrames);
        reverb->process(playerFloatBuffer, playerFloatBuffer, (unsigned int)numberOfFrames);
        outputGain->process(playerFloatBuffer, playerFloatBuffer, (unsigned int)numberOfFrames);
        limiter->process(playerFloatBuffer, playerFloatBuffer, (unsigned int)numberOfFrames);
        SuperpoweredFloatToShortInt(playerFloatBuffer, audio, (unsigned int)numberOfFrames);
        frames = numberOfFrames;
        shortBuffer = audio;
        return true;
    } else {
        return false;
    }
}

// Get buffer of current audio being recorded for drawing waveform in Java
extern "C" JNIEXPORT jshortArray
Java_com_superpowered_recorder_EditFragment_playerGetBuffer (
        JNIEnv * env,
        jobject __unused obj
) {
    jshortArray result;
    result = env->NewShortArray(frames);
    env->SetShortArrayRegion(result, 0, frames, shortBuffer);
    return result;
}


// This is called after the recorder closed the WAV file.
static void recorderStopped (void * __unused clientdata) {
    log_write(ANDROID_LOG_DEBUG, "RecorderExample", "Finished recording.");
    recorder.reset();
    free(floatBuffer);
}

// StartAudio - Start recording audio engine.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_MainActivity_StartAudio (
        JNIEnv *env,
        jobject  __unused obj,
        jint samplerate,
        jint buffersize,
        jstring tempPath,       // path to a temporary file
        jstring destPath        // path to the destination file
) {

    // Get path strings.
    const char *temp = env->GetStringUTFChars(tempPath, 0);
    const char *dest = env->GetStringUTFChars(destPath, 0);

    // Initialize the recorder with a temporary file path.
    recorder.reset(new SuperpoweredRecorder (
            temp,               // The full filesystem path of a temporarily file.
            (unsigned int)samplerate,   // Sampling rate.
            1,                  // The minimum length of a recording (in seconds).
            2,                  // The number of channels.
            false,              // applyFade (fade in/out at the beginning / end of the recording)
            recorderStopped,    // Called when the recorder finishes writing after stop().
            NULL                // A custom pointer your callback receives (clientData).
    ));

    // Start the recorder with the destination file path.
    recorder->start(dest);
    log_write(ANDROID_LOG_DEBUG, "RecorderExample", "SuperPoweredRecorder Object Created");

    // Release path strings.
    env->ReleaseStringUTFChars(tempPath, temp);
    env->ReleaseStringUTFChars(destPath, dest);

    // Initialize float audio buffer.
    floatBuffer = (float *)malloc(sizeof(float) * 2 * buffersize);

    // Initialize audio engine with audio callback function.
    audioIO.reset(new SuperpoweredAndroidAudioIO (
            samplerate,                     // sampling rate
            buffersize,                     // buffer size
            true,                           // enableInput
            false,                          // enableOutput
            audioProcessing,                // process callback function
            NULL                            // clientData
    ));

    log_write(ANDROID_LOG_DEBUG, "RecorderExample", "SuperPoweredAndroidAudioIO Object Created");
}

// StopAudio - Stop audio engine and free audio buffer.
// Called when stop recording
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_MainActivity_StopAudio (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    if (audioIO != NULL) {
        audioIO->stop();
        audioIO.reset();
        audioIO = NULL;
    }
    if (recorder != NULL) {
        recorder->stop();
    }
    log_write(ANDROID_LOG_DEBUG, "StopAudio", "recorder->stop() called");
}

// onBackground - Put audio processing to sleep.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_MainActivity_onBackground (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    audioIO->onBackground();
}

// onForeground - Resume audio processing.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_MainActivity_onForeground (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    audioIO->onForeground();
}

//***PLAYBACK NATIVE LIBRARY IMPLEMENTATIONS***


// OpenFile - Open file in player, without specifying offset and length.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_OpenFile (
        JNIEnv *env,
        jobject __unused obj,
        jstring path       // path to APK file
) {
    const char *str = env->GetStringUTFChars(path, 0);
    player->open(str);
    start = (double*) malloc(sizeof(double));
    end = (double*) malloc(sizeof(double));
    *start = 0;
    *end = -1;
    env->ReleaseStringUTFChars(path, str);
}

// TogglePlayback - Toggle Play/Pause state of the player.

extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_TogglePlayback (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    //check if audio player object initialized
    if (player) {
        player->togglePlayback();
        SuperpoweredCPU::setSustainedPerformanceMode(player->playing);  // prevent dropouts
    }
}

//SeekToPercentage - Seek to percentage progress of track

extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_SeekToPercentage (
        JNIEnv * __unused env,
        jobject __unused obj,
        jdouble percent
) {
    if (player) {
        player->seek(percent);
        player->pause();
        SuperpoweredCPU::setSustainedPerformanceMode(player->playing);  // prevent dropouts
    }
}

//onTrimValue - set the looping playback to start and end (in ms)

extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onTrimValue (
        JNIEnv * __unused env,
        jobject __unused obj,
        jdouble startPos,
        jdouble endPos
) {
    if (player) {
        *start = startPos;
        *end = endPos;
        player->loopBetween(*start, *end, true, 255, false);
        player->pause();
        SuperpoweredCPU::setSustainedPerformanceMode(player->playing);  // prevent dropouts
    }
}


// StartPlayBackEngine - Start audio engine and initialize player.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_StartPlayBackEngine (
        JNIEnv * __unused env,
        jobject  __unused obj,
        jint samplerate,
        jint buffersize
) {
    // Allocate audio buffer.
    playerFloatBuffer = (float *)malloc(sizeof(float) * 2 * buffersize);

    //Initialize reverb settings
    reverb.reset(new SuperpoweredReverb((unsigned int) samplerate));
    reverb->enable(true);
    reverb->setRoomSize(0.5f);
    reverb->setMix(0.0f);
    reverb->setWidth(0.3f);
    reverb->setPredelay(12.0f);
    reverb->setLowCut(100);
    reverb->setDamp(0.2f);

    //Initialize outputGain Compressor settings
    outputGain.reset(new SuperpoweredCompressor((unsigned int) samplerate));
    outputGain->enable(false);

    //Initialize limiter settings, not controlled by user
    limiter.reset(new SuperpoweredLimiter((unsigned int) samplerate));
    limiter->enable(true);
    limiter->ceilingDb = 0.0f;
    limiter->thresholdDb = -0.2f;

    //Initialize 3BandEqualizer settings
    equalizer.reset(new Superpowered3BandEQ((unsigned int) samplerate));
    equalizer->enable(true);
    equalizer->bands[0] = 1.0f; //Low set to flat
    equalizer->bands[1] = 1.0f; //Mid set to flat
    equalizer->bands[2] = 1.0f; //Hi set to flat

    //Initialize Parametric Filter/EQ settings
    filter.reset(new SuperpoweredFilter(SuperpoweredFilter_Parametric, (unsigned int) samplerate));
    filter->enable(true);
    filter->setParametricParameters(50.0f, 1.388484f, 0.0f);

    // Initialize player and pass callback function.
    player.reset(new SuperpoweredAdvancedAudioPlayer (
            NULL,                           // clientData
            playerEventCallback,            // callback function
            (unsigned int)samplerate,       // sampling rate
            0                               // cachedPointCount
    ));

    // Initialize audio with audio callback function.
    playerAudioIO.reset(new SuperpoweredAndroidAudioIO (
            samplerate,                     // sampling rate
            buffersize,                     // buffer size
            false,                          // enableInput
            true,                           // enableOutput
            playerAudioProcessing,                // process callback function
            NULL,                           // clientData
            -1,                             // inputStreamType (-1 = default)
            SL_ANDROID_STREAM_MEDIA         // outputStreamType (-1 = default)
    ));
}

//***FX CONTROL NATIVE LIBRARY IMPLEMENTATIONS***

// InitAllFX - Initialize all FX parameters (when switch from quick to advanced edit)
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_InitAllFX(
         JNIEnv * __unused env,
         jobject __unused obj
) {
    initFXParameters();
}

// onSelectPreset - Select Preset for QuickEdit
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onSelectPreset(
         JNIEnv * __unused env,
         jobject __unused obj,
         jint value
) {
    log_print(ANDROID_LOG_ERROR, "SelectPreset", "preset is: %d", value);
    switch (value)
    {
        case 1: {
            //Rumble Cut
            initFXParameters();
            reverb->setMix(0.0);
            equalizer->bands[0] = 0.668344f; //-3.5db cut lo
            filter->setParametricParameters(180, 1.388484f, -5.0f); //-5db cut at 180Hz
	        outputGain->outputGainDb = 2.5f; //makeup
	        outputGain->enable(true);
            break;
        }
        case 2: {
            //Singing, heavier reverb
            initFXParameters();
            reverb->setMix(0.4);
            equalizer->bands[0] = 0.794328f; //-2db cut lo
            equalizer->bands[1] = 1.778279f; //5db boost mid
            equalizer->bands[2] = 1.412538f; //3db boost hi
            break;
        }
        case 3: {
            //Speech - gentle cut on lo and hi, prioritize mid EQ
            initFXParameters();
            reverb->setMix(0.1);
            equalizer->bands[0] = 0.707946f; //-3db cut lo
            equalizer->bands[1] = 1.778279f; //5db boost mid
            equalizer->bands[2] = 0.707946; //-3db cut hi
            filter->setParametricParameters(2000, 0.944821f, 2.5f); //2.5db boost at 2000Hz
            break;
        }
        default:
            //Resetting parameters
            initFXParameters();
            break;
    }

}

// onReverbValue - Adjust Reverb mix value.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onReverbValue (
        JNIEnv * __unused env,
        jobject __unused obj,
        jint value
) {

    log_print(ANDROID_LOG_ERROR, "reverbMix", "value is: %d", value);
    float mixValue = float(value) * 0.001f;
	reverb->setMix(mixValue);
}

// onPostGainValue - Adjust Compressor output value.
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onPostGainValue (
        JNIEnv * __unused env,
        jobject __unused obj,
        jint value
) {
    float ceilingValue = float(value - 100)  * 1.2 * 0.1f; //+- 12.0db increase
    log_print(ANDROID_LOG_ERROR, "outputGainDb", "Db value is: %f", ceilingValue);
	if(ceilingValue != 0.0f) {
	    outputGain->outputGainDb = ceilingValue;
	    outputGain->enable(true);
	} else {
	    outputGain->enable(false);
	}
}

//***EQ CONTROL NATIVE LIBRARY IMPLEMENTATIONS***

// onHighValue - Adjust EQ Hi freq gain level
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onHighValue (
        JNIEnv * __unused env,
        jobject __unused obj,
        jfloat value
) {
    log_print(ANDROID_LOG_ERROR, "EQ High Db", "Db value is: %f", value);
    equalizer->bands[2] = value;
}

// onMidValue - Adjust EQ Mid freq gain level
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onMidValue (
        JNIEnv * __unused env,
        jobject __unused obj,
        jfloat value
) {

    log_print(ANDROID_LOG_ERROR, "EQ Mid Db", "Db value is: %f", value);
    equalizer->bands[1] = value;
}


// onLowValue - Adjust EQ Low freq gain level
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onLowValue (
        JNIEnv * __unused env,
        jobject __unused obj,
        jfloat value
) {

    log_print(ANDROID_LOG_ERROR, "EQ Low Db", "Db value is: %f", value);
    equalizer->bands[0] = value;
}

// onParaEQAdjust- Adjust ParaEQ filter parameters
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_onParaEQAdjust (
        JNIEnv * __unused env,
        jobject __unused obj,
        jfloat freqValue, //in hz
        jfloat gainValue //in db
) {
    log_print(ANDROID_LOG_ERROR, "ParaEQ", "Freq is %f", freqValue);
    log_print(ANDROID_LOG_ERROR, "ParaEQ", "Gain is %f", gainValue);
	filter->setParametricParameters(freqValue, 1.388484f, gainValue);
}

//CleanUp the audio player
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_CleanupPlayer (
        JNIEnv * __unused env,
        jobject __unused obj
) {
    log_print(ANDROID_LOG_ERROR, "CleanUp", "Deleting playerAudioIO");
    if (playerAudioIO != NULL) {
        playerAudioIO->stop();
        log_print(ANDROID_LOG_ERROR, "CleanUp", "Resetting playerAudioIO");
        playerAudioIO.reset();
        playerAudioIO = NULL;
    }
    log_print(ANDROID_LOG_ERROR, "CleanUp", "Deleting player");
    if (player != NULL) {
        log_print(ANDROID_LOG_ERROR, "CleanUp", "Pausing player");
        player->pause();
        log_print(ANDROID_LOG_ERROR, "CleanUp", "Resetting player");
        player.reset();
        player = NULL;
    }


    log_print(ANDROID_LOG_ERROR, "CleanUp", "Deleting effects");
    if (equalizer != NULL) {
        equalizer.reset();
        equalizer = NULL;
    }
    if (filter != NULL) {
        filter.reset();
        filter = NULL;
    }
    if (reverb != NULL) {
        reverb.reset();
        reverb = NULL;
    }
    if (outputGain != NULL) {
        outputGain.reset();
        outputGain = NULL;
    }
    if (limiter != NULL) {
        limiter.reset();
        limiter = NULL;
    }
    log_print(ANDROID_LOG_ERROR, "CleanUp", "Freeing");
    free(playerFloatBuffer);
    log_print(ANDROID_LOG_ERROR, "CleanUp", "Freed FloatBuffer");
    free(start);
    free(end);
    log_print(ANDROID_LOG_ERROR, "CleanUp", "Exiting CleanUp");
}

//Writing to directory after FX applied
extern "C" JNIEXPORT void
Java_com_superpowered_recorder_EditFragment_WriteToDisk(
        JNIEnv * env,
        jobject __unused obj,
        jstring path,
        jstring pathOutput
) {
    const char *str = env->GetStringUTFChars(path, 0);
    const char *strOutput = env->GetStringUTFChars(pathOutput, 0);

    log_print(ANDROID_LOG_ERROR, "WriteToDisk", "Running");
    decoder = new SuperpoweredDecoder();
    const char *openError = decoder->open(str, false);

    log_print(ANDROID_LOG_ERROR, "Decoder", "Opening");
    if (openError) {
        delete decoder;
        return;
    };
    log_print(ANDROID_LOG_ERROR, "Decoder", "Opened");

    log_print(ANDROID_LOG_ERROR, "WAV", "Creating");
    FILE *fd = createWAV(strOutput, decoder->samplerate, 2);
    if (!fd) {
        delete decoder;
        return;
    };
    log_print(ANDROID_LOG_ERROR, "WAV", "Created");

    // Create a buffer for the 16-bit integer samples coming from the decoder.
    short int *intBuff = (short int *)malloc(decoder->samplesPerFrame * 2 * sizeof(short int) + 16384);
    // Create a buffer for the 32-bit floating point samples required by the effect.
    float *floatBuff = (float *)malloc(decoder->samplesPerFrame * 2 * sizeof(float) + 1024);
    // Processing.
    if (*end == -1) {
        *end = player->durationMs;
    }
    log_print(ANDROID_LOG_ERROR, "Buffers", "Created, entering while loop");
    decoder->seek(*start / 1000 * decoder->samplerate, true);
    int totalSamplesProcessed = 0;
    while (totalSamplesProcessed <= (*end - *start) / 1000 * decoder->samplerate) {
        // Decode one frame. samplesDecoded will be overwritten with the actual decoded number of samples.

        log_print(ANDROID_LOG_ERROR, "Decoder", "getting samples per frame");
        unsigned int samplesDecoded = decoder->samplesPerFrame;
        log_print(ANDROID_LOG_ERROR, "Decoder", "decoding");
        if (decoder->decode(intBuff, &samplesDecoded) == SUPERPOWEREDDECODER_ERROR) {
            log_print(ANDROID_LOG_ERROR, "Decoder", "SUPERPOWEREDDECODER_ERROR returned");
            break;
        }

        log_print(ANDROID_LOG_ERROR, "Decoder", "samplesDecoded: %d", samplesDecoded);
        if (samplesDecoded < 1) {
            break;
        }

        // Apply the effect.

        log_print(ANDROID_LOG_ERROR, "Effect", "Applying");
        // Convert the decoded PCM samples from 16-bit integer to 32-bit floating point.
        SuperpoweredShortIntToFloat(intBuff, floatBuff, samplesDecoded);

        equalizer->process(floatBuff, floatBuff, samplesDecoded);
        filter->process(floatBuff, floatBuff, samplesDecoded);
        reverb->process(floatBuff, floatBuff, samplesDecoded);
        outputGain->process(floatBuff, floatBuff, samplesDecoded);
        limiter->process(floatBuff, floatBuff, samplesDecoded);

        // Convert the PCM samples from 32-bit floating point to 16-bit integer.
        SuperpoweredFloatToShortInt(floatBuff, intBuff, samplesDecoded);

        log_print(ANDROID_LOG_ERROR, "fwrite", "writing to disk");
        // Write the audio to disk.
        fwrite(intBuff, 1, samplesDecoded * 4, fd);
        totalSamplesProcessed += samplesDecoded;
    }

    log_print(ANDROID_LOG_ERROR, "Cleanup", "Running");
    // Cleanup.
    closeWAV(fd);
    delete decoder;
    free(intBuff);
    free(floatBuff);
}