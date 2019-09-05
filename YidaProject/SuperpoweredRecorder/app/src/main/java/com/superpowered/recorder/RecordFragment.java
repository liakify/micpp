package com.superpowered.recorder;

import java.lang.Runnable;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class RecordFragment extends Fragment {

    private WaveformView waveformView;
    private ImageButton recordBtn;
    private TextView timerText;
    private long startTime, timeUnit = 0; //1 timeUnit = 0.1 second
    private boolean isRecording = false;
    private BottomNavigationView bottomNavigationView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_record, container, false);
    }

    //Main logic of this fragment goes here
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        bottomNavigationView = getActivity().findViewById(R.id.navigationMain);
        bottomNavigationView.findViewById(R.id.recordTab).setEnabled(false);
        getActivity().setTitle("Record");

        waveformView = getView().findViewById(R.id.audioWaveform);
        recordBtn = getView().findViewById(R.id.recordBtn);
        timerText = getView().findViewById(R.id.timerText);

        startClock();


        //Recording button toggle skeleton
        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isRecording) {
                    recordBtn.setImageResource(R.drawable.ic_stop_36dp);
                    Thread recordThread = new Thread(new Runnable(){

                        @Override
                        public void run() {
                            isRecording = true;
                            startTime = SystemClock.uptimeMillis();
                            MainActivity.StartRecord();
                            //Toast.makeText(getActivity(), getBuffer().length, Toast.LENGTH_SHORT).show();
                        }

                    });
                    recordThread.start();
                } else if (timeUnit < 10) {
                    Toast.makeText(getActivity(), "Recordings must be at least 1 second", Toast.LENGTH_SHORT).show();
                } else {
                    isRecording = false;
                    recordBtn.setImageResource(R.drawable.ic_record_48dp);

                    MainActivity.StopAudio();


                    bottomNavigationView.getMenu().findItem(R.id.editTab).setChecked(true);

                    ((MainActivity) getActivity())
                            .displaySelectedScreen(EditFragment.newInstance(createRecording(), MainActivity.sampleRate, MainActivity.bufferSize));
                }

            }
        });
    }

    //Stopwatch method
    private void startClock() {
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                long hours = timeUnit/36000;
                long minutes = (timeUnit%36000)/600;
                long secs = timeUnit%600/10;

                String time = String.format("%d:%02d:%02d.%01d", hours, minutes, secs, timeUnit%10);
                timerText.setText(time);

                if (isRecording) {
                    timeUnit = (SystemClock.uptimeMillis() - startTime) / 100;
                    short[] temp = getBuffer();
                    if (temp.length > 0) {
                        waveformView.updateAudioData(getBuffer());
                    }
                } else {
                    waveformView.clearWaveform();
                }
                handler.postDelayed(this, 0);
            }
        });
    }

    private Recording createRecording() {
        return new Recording(MainActivity.destPath + ".wav");
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isRecording) MainActivity.onBackground();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isRecording) MainActivity.onForeground();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isRecording) MainActivity.StopAudio();
    }

    //Get audio buffer from SuperpoweredSDK to draw waveform
    private native short[] getBuffer();
}
