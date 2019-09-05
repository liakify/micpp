package com.superpowered.recorder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarChangeListener;
import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarFinalValueListener;
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import abak.tr.com.boxedverticalseekbar.BoxedVertical;

public class EditFragment extends Fragment {


    private BottomNavigationView bottomNavigationView;

    //Samplerate and Buffersize
    int samplerate;
    int buffersize;

    //Playback Trim UI elements
    private ImageButton playPauseBtn, stopBtn;
    private Button finishBtn;
    private SeekBar playerTimeline;
    private CrystalRangeSeekbar trimBar;
    private TextView trimBarMinText, trimBarMaxText, timerText;
    private LinearLayout editLayout;
    private EditText currentFileName;
    private WaveformView waveformView;


    //Quick Edit UI elements
    private Spinner enhanceSpinner;
    private HashMap<String, Integer> presetMap = new HashMap<>();

    //Advanced Edit UI elements
    private SeekBar freqSlider, amountSlider, reverbSlider, postGainSlider;
    private Button freqPlusBtn, freqMinusBtn;
    private TextView freqText, amountText, reverbText, postGainText;
    private TextView eqLoText, eqMidText, eqHiText;
    private BoxedVertical eqLoSlider, eqMidSlider, eqHiSlider;

    private int eqMin = -100, eqMax = 100, eqStep = 1; //eq slider parameters - BoxedVertical only can have int step
    private double gainMin = -10, gainMax = 10, gainStep = 0.1; //gain slider parameters
    private double fxMin = 0, fxMax = 100, fxStep = 0.1; //effects sliders parameters
    private double freqMin = 50, freqMax = 6000, freqStep = 1; //freqSlider filter parameters
    private double freqGainMin = 0.0, freqGainMax = 15.0, freqGainStep = 0.1; //amountSlider filter paramters
    private double currFreq = 2600, currFreqGain = 0.0;

    /* Playback
    *  start & end represent the trimmed timeline cut off points
    *  But the view is first created, set end = end of audio file so that
    *  the range seekbar will initialise the full duration range
     */
    private Recording recording;
    private long startTime, timeUnit = 0; //1 timeUnit = 0.1 second
    private long start = 0, end = 0; //start & end time as measured in time units
    private boolean isPlaying = false;

    //Static method called from RecordFragment to initialize new EditFragment with associated
    //recording, samplerate and buffersize
    public static EditFragment newInstance(Recording recording, int samplerate, int buffersize) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("recording", recording);
        EditFragment fragment = new EditFragment();
        fragment.setArguments(bundle);
        fragment.samplerate = samplerate;
        fragment.buffersize = buffersize;
        return fragment;
    }

    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Update parameters if EditFragment was created with a recording
        Bundle args = getArguments();
        setHasOptionsMenu(args != null);
        if (args != null) {
            recording = (Recording)args.getSerializable("recording");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_edit, container, false);
    }

    //Top right menu logic
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.navigation_edit, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.quickEdit:
                initQuickEdit();
                return true;
            case R.id.advancedEdit:
                initAdvancedEdit();
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    //Main logic of this fragment goes here
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        bottomNavigationView = getActivity().findViewById(R.id.navigationMain);
        bottomNavigationView.findViewById(R.id.editTab).setEnabled(false);

        editLayout = getActivity().findViewById(R.id.editLayout);
        if (recording == null) {
            getActivity().setTitle("No Recording Loaded");
        } else {
            initPlaybackTrim();
            initQuickEdit();
        }
    }

    //Drawing the layout specific to quick edit
    private void fillQuickEditLayout() {
        getActivity().setTitle("Quick Edit");
        editLayout.removeViewAt(1);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        float remainingWeight = editLayout.getWeightSum() * 2 / 3;
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, remainingWeight * 9/10);
        editLayout.getChildAt(0).setLayoutParams(param);
        LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.controls_quick_edit, (ViewGroup)getView(), false);
        param = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, remainingWeight * 1/10);
        editLayout.addView(layout, 1, param);
        waveformView.resetCleared();
    }

    //Drawing the layout specific to advanced edit
    private void fillAdvancedEditLayout() {
        getActivity().setTitle("Advanced Edit");
        editLayout.removeViewAt(1);
        LayoutInflater inflater = LayoutInflater.from(getContext());
        float remainingWeight = editLayout.getWeightSum() * 2 / 3;
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, remainingWeight * 1/3);
        editLayout.getChildAt(0).setLayoutParams(param);
        LinearLayout layout = (LinearLayout)inflater.inflate(R.layout.controls_advanced_edit, (ViewGroup)getView() , false);
        param = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, remainingWeight * 2/3);
        editLayout.addView(layout, 1, param);
        waveformView.resetCleared();
    }

    private void initQuickEdit() {
        InitAllFX();
        fillQuickEditLayout();
        enhanceSpinner = getActivity().findViewById(R.id.quickEnhanceSpinner);

        //Setting up the list items
        List<String> enhanceList = new ArrayList<>();
        enhanceList.add("No edits"); //preset 0
        enhanceList.add("Low Cut"); //preset 1
        enhanceList.add("Singing"); //preset 2
        enhanceList.add("Speech"); //preset 3

        presetMap.put("No edits", 0);
        presetMap.put("Low Cut", 1);
        presetMap.put("Singing", 2);
        presetMap.put("Speech", 3);

        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, enhanceList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        enhanceSpinner.setAdapter(dataAdapter);

        //Enhance spinner skeleton
        enhanceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String currPreset = parent.getItemAtPosition(pos).toString();
                Toast.makeText(getActivity(), "Selected " + currPreset,
                        Toast.LENGTH_SHORT).show();
                onSelectPreset(presetMap.get(currPreset));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void initAdvancedEdit() {
        fillAdvancedEditLayout();
        freqSlider = getActivity().findViewById(R.id.freqSlider);
        freqPlusBtn = getActivity().findViewById(R.id.freqPlusBtn);
        freqMinusBtn = getActivity().findViewById(R.id.freqMinusBtn);
        amountSlider = getActivity().findViewById(R.id.amountSlider);
        reverbSlider = getActivity().findViewById(R.id.reverbSlider);
        postGainSlider = getActivity().findViewById(R.id.postGainSlider);
        freqText = getActivity().findViewById(R.id.freqValueText);
        amountText = getActivity().findViewById(R.id.amountValueText);
        reverbText = getActivity().findViewById(R.id.reverbValueText);
        postGainText = getActivity().findViewById(R.id.postGainValueText);
        eqLoSlider = getActivity().findViewById(R.id.eqLoSlider);
        eqMidSlider = getActivity().findViewById(R.id.eqMidSlider);
        eqHiSlider = getActivity().findViewById(R.id.eqHiSlider);
        eqLoText = getActivity().findViewById(R.id.eqLoValueText);
        eqMidText = getActivity().findViewById(R.id.eqMidValueText);
        eqHiText = getActivity().findViewById(R.id.eqHiValueText);

        InitAllFX();
        //EQ SECTION
        //Low slider skeleton
        eqLoSlider.setMax((eqMax - eqMin) / eqStep);
        eqLoSlider.setValue(-eqMin / eqStep);
        eqLoSlider.setOnBoxedPointsChangeListener(new BoxedVertical.OnValuesChangeListener() {
            double value;
            @Override
            public void onPointsChanged(BoxedVertical boxedVertical, int i) {
                value = (eqMin + i * eqStep) / 10.0;
                eqLoText.setText(String.format("%.1f", value));
                onLowValue((float) Math.pow(10, value/10));
            }

            @Override
            public void onStartTrackingTouch(BoxedVertical boxedVertical) {
                value = (eqMin + eqHiSlider.getValue() * eqStep) / 10.0;
                onLowValue((float) Math.pow(10, value/10));
            }

            @Override
            public void onStopTrackingTouch(BoxedVertical boxedVertical) {

            }
        });

        //Mid slider skeleton
        eqMidSlider.setMax((eqMax - eqMin) / eqStep);
        eqMidSlider.setValue(-eqMin / eqStep);
        eqMidSlider.setOnBoxedPointsChangeListener(new BoxedVertical.OnValuesChangeListener() {
            double value;
            @Override
            public void onPointsChanged(BoxedVertical boxedVertical, int i) {
                value = (eqMin + i * eqStep) / 10.0;
                eqMidText.setText(String.format("%.1f", value));
                onMidValue((float) Math.pow(10, value/10));
            }

            @Override
            public void onStartTrackingTouch(BoxedVertical boxedVertical) {
                value = (eqMin + eqHiSlider.getValue() * eqStep) / 10.0;
                onMidValue((float) Math.pow(10, value/10));
            }

            @Override
            public void onStopTrackingTouch(BoxedVertical boxedVertical) {

            }
        });

        //High slider skeleton
        eqHiSlider.setMax((eqMax - eqMin) / eqStep);
        eqHiSlider.setValue(-eqMin / eqStep);
        eqHiSlider.setOnBoxedPointsChangeListener(new BoxedVertical.OnValuesChangeListener() {
            double value;
            @Override
            public void onPointsChanged(BoxedVertical boxedVertical, int i) {
                value = (eqMin + i * eqStep) / 10.0;
                eqHiText.setText(String.format("%.1f", value));
                onHighValue((float) Math.pow(10, value/10));

            }

            @Override
            public void onStartTrackingTouch(BoxedVertical boxedVertical) {
                value = (eqMin + eqHiSlider.getValue() * eqStep) / 10.0;
                onHighValue((float) Math.pow(10, value/10));
            }

            @Override
            public void onStopTrackingTouch(BoxedVertical boxedVertical) {

            }
        });

        //PARA-EQ SECTION
        //ParaEQ Freq skeleton
        freqSlider.setMax((int)((freqMax - freqMin) / freqStep));
        freqSlider.setProgress((int)(-freqMin / freqStep));
        freqSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            double value;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = freqMin + progress * freqStep;
                freqText.setText(String.format("%.1f", value));
                currFreq = value;
                onParaEQAdjust((float) currFreq, (float) currFreqGain);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                value = freqMin + freqSlider.getProgress() * freqStep;
                currFreq = value;
                onParaEQAdjust((float) currFreq, (float) currFreqGain);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //ParaEQ buttons press and hold
        freqPlusBtn.setOnTouchListener(new View.OnTouchListener() {

            private Handler mHandler;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mHandler != null) return true;
                        mHandler = new Handler();
                        mHandler.postDelayed(mAction, 0);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (mHandler == null) return true;
                        mHandler.removeCallbacks(mAction);
                        mHandler = null;
                        break;
                }
                return false;
            }

            Runnable mAction = new Runnable() {
                @Override
                public void run() {
                    freqSlider.setProgress(freqSlider.getProgress() + 1);
                    mHandler.postDelayed(this, 100);
                }
            };

        });

        freqMinusBtn.setOnTouchListener(new View.OnTouchListener() {

            private Handler mHandler;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mHandler != null) return true;
                        mHandler = new Handler();
                        mHandler.postDelayed(mAction, 0);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (mHandler == null) return true;
                        mHandler.removeCallbacks(mAction);
                        mHandler = null;
                        break;
                }
                return false;
            }

            Runnable mAction = new Runnable() {
                @Override
                public void run() {
                    freqSlider.setProgress(freqSlider.getProgress() - 1);
                    mHandler.postDelayed(this, 100);
                }
            };

        });

        //ParaEQ reduction amount skeleton
        amountSlider.setMax((int)((freqGainMax - freqGainMin) / freqGainStep));
        amountSlider.setProgress((int)(-freqGainMin / freqGainStep));
        amountSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            double value;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = - (freqGainMin + progress * freqGainStep);
                amountText.setText(String.format("%.1f", value));
                currFreqGain = value;
                onParaEQAdjust((float) currFreq, (float) currFreqGain);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                value = - (freqGainMin + amountSlider.getProgress() * freqGainStep);
                currFreqGain = value;
                onParaEQAdjust((float) currFreq, (float) currFreqGain);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //EFFECTS SECTION
        //Reverb skeleton
        reverbSlider.setMax((int)((fxMax - fxMin) / fxStep));
        reverbSlider.setProgress((int)(-fxMin / fxStep));
        reverbSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            double value;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = fxMin + progress * fxStep;
                reverbText.setText(String.format("%.1f", value));

                //Set Dry/Wet Mix on Reverb object in C++ library
                onReverbValue(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                onReverbValue(seekBar.getProgress());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //Post gain skeleton
        postGainSlider.setMax((int)((gainMax - gainMin) / gainStep));
        postGainSlider.setProgress((int)(-gainMin / gainStep));
        postGainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            double value;
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value = gainMin + progress * gainStep;
                postGainText.setText(String.format("%.1f", value));
                onPostGainValue(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                onPostGainValue(seekBar.getProgress());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void initPlaybackTrim() {
        //Recording stopped, start the playback engine
        StartPlayBackEngine(samplerate, buffersize);
        //Open recording associated with this EditFragment
        OpenFile(recording.getFilePath());

        playPauseBtn = getActivity().findViewById(R.id.playPauseBtn);
        stopBtn = getActivity().findViewById(R.id.stopBtn);
        finishBtn = getActivity().findViewById(R.id.finishBtn);
        playerTimeline = getActivity().findViewById(R.id.playerTimeline);
        timerText = getActivity().findViewById(R.id.timerText);
        trimBar = getActivity().findViewById(R.id.trimBar);
        trimBarMinText = getActivity().findViewById(R.id.trimBarMinText);
        trimBarMaxText = getActivity().findViewById(R.id.trimBarMaxText);
        currentFileName = getActivity().findViewById(R.id.currentFileName);
        waveformView = getActivity().findViewById(R.id.audioWaveform);

        end = recording.getDuration();
        currentFileName.setText(recording.getName());

        stopPlayback();
        startClock();

        //Play button toggle skeleton
        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPlaying) {
                    if(playerTimeline.getProgress() == playerTimeline.getMax()){
                        playerTimeline.setProgress(0);
                        playerTimeline.refreshDrawableState();
                    }
                    playPauseBtn.setImageResource(R.drawable.ic_pause_36dp);
                    Thread playThread = new Thread(new Runnable(){

                        @Override
                        public void run() {
                            playRecording();
                        }

                    });
                    playThread.start();

                } else {
                    isPlaying = false;
                    TogglePlayback();
                    playPauseBtn.setImageResource(R.drawable.ic_play_36dp);
                }
                setStopButtonEnabled(true);
            }
        });

        //Stop button toggle skeleton
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayback();
                isPlaying = false;
            }
        });

        //Player timeline skeleton
        playerTimeline.setMax((int)(end - start));
        playerTimeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                timeUnit = start + progress;
                if (!isPlaying && progress == 0) {
                    stopPlayback();
                } else {
                    if(playerTimeline.getMax() == progress && isPlaying){
                        isPlaying = false;
                        playPauseBtn.setImageResource(R.drawable.ic_play_36dp);
                        TogglePlayback();
                    }
                    setStopButtonEnabled(true);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(isPlaying) {
                    isPlaying = false;
                    playPauseBtn.setImageResource(R.drawable.ic_play_36dp);
                    TogglePlayback();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                double current = (double) playerTimeline.getProgress();
                double seekPercent = (start + current) / recording.getDuration();
                SeekToPercentage(seekPercent);
            }
        });

        //Trim skeleton
        trimBar.setMaxValue(end);
        trimBar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
            @Override
            public void valueChanged(Number minValue, Number maxValue) {
                trimBarMinText.setText(timeToString(minValue.intValue(), false));
                trimBarMaxText.setText(timeToString(maxValue.intValue(), false));
            }
        });

        //Trim final value skeleton
        trimBar.setOnRangeSeekbarFinalValueListener(new OnRangeSeekbarFinalValueListener() {
            @Override
            public void finalValue(Number minValue, Number maxValue) {
                start = minValue.longValue();
                end = maxValue.longValue();
                playerTimeline.setMax((int)(end - start));
                onTrimValue(start * 100, end * 100);
                stopPlayback();
            }
        });

        //Finish button skeleton
        finishBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                stopPlayback();
                final File folder = new File(((MainActivity)getActivity()).getRootPath());
                final String savePath = folder.getPath() + File.separator + currentFileName.getText();

                AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                if ((new File(savePath).exists())) {
                    builder.setTitle("Confirm overwrite?");
                    builder.setMessage(currentFileName.getText() + " already exists");
                } else {
                    builder.setTitle("Confirm save?");
                    builder.setMessage(currentFileName.getText().toString());
                }

                // Set the alert dialog yes button click listener
                builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Write to Disk
                        if (!folder.exists()) {
                            folder.mkdirs();
                        }
                        WriteToDisk(recording.getFilePath(), savePath);

                        Toast.makeText(getActivity(), currentFileName.getText() + " saved", Toast.LENGTH_SHORT).show();
                        bottomNavigationView.setSelectedItemId(R.id.libraryTab);
                    }
                });

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //Do nothing if cancelled
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });
    }

    private void startClock() {
        final Handler handler = new Handler();
        handler.post(new Runnable() {
                @Override
                public void run() {
                timerText.setText(timeToString(timeUnit, true));

                if (isPlaying) {
                    if (timeUnit < end) {
                        //timeunit definition of 0.1s comes from here
                        timeUnit = start + (SystemClock.uptimeMillis() - startTime) / 100;
                    } else {
                        isPlaying = false;
                        playPauseBtn.setImageResource(R.drawable.ic_play_36dp);
                    }
                    playerTimeline.setProgress((int)(timeUnit - start));

                    short[] temp = playerGetBuffer();
                    if (temp.length > 0) {
                        waveformView.updateAudioData(playerGetBuffer());
                    }
                } else {
                    waveformView.clearWaveform();
                }

                handler.postDelayed(this, 0);
            }
        });
    }

    //Sets enabled and updates stop button image
    private void setStopButtonEnabled(boolean enabled) {
        stopBtn.setEnabled(enabled);
        stopBtn.setImageResource(enabled ? R.drawable.ic_stop_36dp : R.drawable.ic_stop_disabled_36dp);
    }

    private void stopPlayback() {
        startTime = 0;
        timeUnit = start;
        playerTimeline.setProgress(0);
        isPlaying = false;
        timerText.setText(timeToString(timeUnit, true));
        playPauseBtn.setImageResource(R.drawable.ic_play_36dp);
        setStopButtonEnabled(false);
        SeekToPercentage((double) start / (double) recording.getDuration());
    }

    private String timeToString(long time, boolean longFormat) {
        long hours = time/36000;
        long minutes = (time%36000)/600;
        long secs = time%600/10;
        if (longFormat) {
            return String.format("%d:%02d:%02d.%d", hours, minutes, secs, time%10);
        } else if (hours == 0){
            return String.format("%d:%02d", minutes, secs);
        } else {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
    }

    void playRecording(){
        TogglePlayback();
        startTime = SystemClock.uptimeMillis() - playerTimeline.getProgress() * 100;
        isPlaying = true;
    }

    //Cleanup resources
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("onDestroy", "entering cleanup");
        CleanupPlayer();
        Log.d("onDestroy", "cleanup done");
        if (recording != null) {
//            Log.d("Calling getFilePath ", recording.getFilePath());
            recording.getFile().delete();
        }
    }


    // Playback functions implemented in native library
    private native void OpenFile(String path);
    private native void TogglePlayback();
    private native void StartPlayBackEngine(int samplerate, int buffersize);
    private native void SeekToPercentage(double percent);
    private native void onTrimValue(double start, double end);

    // FX manipulation functions implemented in native library
    private native void onReverbValue(int value);
    private native void onPostGainValue(int value);
    private native void onHighValue(float value);
    private native void onMidValue(float value);
    private native void onLowValue(float value);
    private native void onParaEQAdjust(float freqValue, float gainValue);

    //Quick Edit Pre-sets
    private native void onSelectPreset(int presetNum);

    //Init All effects settings
    private native void InitAllFX();

    //Cleanup function implemented in native library
    public static native void CleanupPlayer();

    //Writing back to Disk
    private native void WriteToDisk(String path, String pathOutput);

    //Get audio buffer from SuperpoweredSDK to draw waveform
    private native short[] playerGetBuffer();
}
