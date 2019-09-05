package com.superpowered.recorder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.view.MenuItem;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    final int REQUEST_PERMISSION_CODE = 1000;
    private static String tempPath;
    public static String destPath;
    public static int sampleRate;
    public static int bufferSize;
    private String rootPath;
    private String cachePath;

    public String getRootPath() { return rootPath; }
    public String getCachePath() { return cachePath; }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rootPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + File.separator + "micpp";
        cachePath = this.getCacheDir().getAbsolutePath();

        setContentView(R.layout.activity_main);
        displaySelectedScreen(new RecordFragment());
        Toolbar tb = findViewById(R.id.toolbar);
        setSupportActionBar(tb);


        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
        };
        for (String s:permissions) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                // Some permissions are not granted, ask the user.
                ActivityCompat.requestPermissions(this, permissions, 0);
                return;
            }
        }

        initialize();


        BottomNavigationView bottomNavigationView = findViewById(R.id.navigationMain);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment fragment = null;
                switch (item.getItemId()) {
                    case R.id.recordTab:
                        fragment = new RecordFragment();
                        break;
                    case R.id.editTab:
                        fragment = new EditFragment();
                        break;
                    case R.id.libraryTab:
                        fragment = new LibraryFragment();
                        break;
                }
                return displaySelectedScreen(fragment);
            }
        });
    }

    //displaying the selected screen
    boolean displaySelectedScreen(Fragment fragment) {
        if (fragment != null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragmentContainer, fragment);
            ft.commit();
            return true;
        }
        return false;
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        // Called when the user answers to the permission dialogs.
        if ((requestCode != 0) || (grantResults.length < 1) || (grantResults.length != permissions.length)) return;
        boolean hasAllPermissions = true;

        for (int grantResult:grantResults) if (grantResult != PackageManager.PERMISSION_GRANTED) {
            hasAllPermissions = false;
            Toast.makeText(getApplicationContext(), "Please allow all permissions for the app.", Toast.LENGTH_LONG).show();
        }

        if (hasAllPermissions) initialize();
    }

    //Initialize AudioEngine, set Sample/Buffer rates, set save directory
    private void initialize() {
        // Get the device's sample rate and buffer size to enable
        // low-latency Android audio output, if available.
        String samplerateString = null, buffersizeString = null;
        if (Build.VERSION.SDK_INT >= 17) {
            AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
                buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
            }
        }
        if (samplerateString == null) samplerateString = "48000";
        if (buffersizeString == null) buffersizeString = "480";
        sampleRate = Integer.parseInt(samplerateString);
        bufferSize = Integer.parseInt(buffersizeString);


        // load native library
        System.loadLibrary("RecorderExample");

        tempPath = cachePath + File.separator + "temp.wav";  // temporary file path
        destPath = cachePath + File.separator + "Untitled_Recording";       // destination file path

        Log.d("Recorder", "Temporary file: " + tempPath);
        Log.d("Recorder", "Destination file: " + destPath + ".wav");
    }

    protected void onDestroy() {
        super.onDestroy();
        EditFragment.CleanupPlayer();
    }

    public static void StartRecord(){
        StartAudio(sampleRate, bufferSize, tempPath, destPath);
    }
    public static void StopRecord(){
        StopAudio();
    }

    public static native void StartAudio(int sampleRate, int bufferSize, String tempPath, String destPath);
    public static native void onForeground();
    public static native void onBackground();
    public static native void StopAudio();
}
