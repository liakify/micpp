package com.superpowered.recorder;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.content.FileProvider;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LibraryFragment extends Fragment {
    private BottomNavigationView bottomNavigationView;
    private ListView recordingList;
    private ArrayList<Recording> recordingArrayList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    //Main logic of this fragment goes here
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        bottomNavigationView = getActivity().findViewById(R.id.navigationMain);
        bottomNavigationView.findViewById(R.id.libraryTab).setEnabled(false);
        getActivity().setTitle("Library");

        recordingList = getActivity().findViewById(R.id.recordingList);
        recordingList.setEmptyView(getActivity().findViewById(R.id.emptyText));

        //TODO: populate recordingArrayList with files from directory instead
        recordingArrayList = new ArrayList<>();
        File directory = new File(((MainActivity)getActivity()).getRootPath());
        File[] files = directory.listFiles();
        for (int i = 0; i < files.length; i++) {
            recordingArrayList.add(new Recording(files[i]));
        }
        Collections.sort(recordingArrayList);

        RecordingAdapter adapter = new RecordingAdapter(getContext(), recordingArrayList);
        recordingList.setAdapter(adapter);

        //Recording list item click listener skeleton
        recordingList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Recording recording = (Recording) parent.getItemAtPosition(position);
                //Create temp file to work on
                File src = recording.getFile();
                File dest = new File(((MainActivity)getActivity()).getCachePath() + File.separator + recording.getName());

                try {
                    //Copy file to temp location
                    InputStream is = new FileInputStream(src);
                    OutputStream os = new FileOutputStream(dest);
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = is.read(buffer)) > 0) {
                        os.write(buffer, 0, length);
                    }
                    is.close();
                    os.close();
                    //bottomNavigationView.setSelectedItemId(R.id.editTab);
                    bottomNavigationView.getMenu().findItem(R.id.editTab).setChecked(true);
                    ((MainActivity) getActivity()).displaySelectedScreen(EditFragment.newInstance(new Recording(dest.getPath()), MainActivity.sampleRate, MainActivity.bufferSize));
                } catch (IOException e) {
                    Toast.makeText(getContext(), "Unable to copy file to temp location\n" + e, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private class RecordingAdapter extends ArrayAdapter<Recording> {
        private List<Recording> items;
        private Context context;

        public RecordingAdapter(Context context, List<Recording> items) {
            super(context, -1, items);
            this.items = items;
            this.context = context;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.recording_list_row, parent, false);

            final Recording recording = items.get(position);

            TextView fileName = rowView.findViewById(R.id.fileName);
            TextView fileDate = rowView.findViewById(R.id.fileDate);
            TextView duration = rowView.findViewById(R.id.fileDuration);
            ImageButton deleteBtn = rowView.findViewById(R.id.deleteBtn);
            ImageButton shareBtn = rowView.findViewById(R.id.shareBtn);

            fileName.setText(recording.getName());
            fileDate.setText(recording.getDateString());
            duration.setText(recording.getDurationString());

            //Delete recording button listener skeleton
            deleteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Confirm delete?");
                    builder.setMessage(recording.getName());

                    // Set the alert dialog yes button click listener
                    builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Actual file delete logic goes here
                            File file = new File(recording.getFilePath());
                            if (file.delete()) {
                                Toast.makeText(getContext(), recording.getName() + " deleted", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(getContext(), recording.getName() + " does not exist", Toast.LENGTH_SHORT).show();
                            }
                            //Remove item and update list view
                            recordingArrayList.remove(position);
                            RecordingAdapter adapter = new RecordingAdapter(getContext(), recordingArrayList);
                            recordingList.setAdapter(adapter);
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

            //Share recording button listener skeleton
            shareBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    File file = new File(recording.getFilePath());

                    if (file.exists()) {
                        Intent sendIntent = new Intent();
                        sendIntent.setAction(Intent.ACTION_SEND);
                        sendIntent.setType("audio/wav");
                        Uri fileUri = FileProvider.getUriForFile(getContext(), getString(R.string.file_provider_authority), file);
                        sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                        startActivity(sendIntent);
                    } else {
                        Toast.makeText(getContext(), recording.getName() + " does not exist", Toast.LENGTH_SHORT).show();
                        //Remove item and update list view
                        recordingArrayList.remove(position);
                        RecordingAdapter adapter = new RecordingAdapter(getContext(), recordingArrayList);
                        recordingList.setAdapter(adapter);
                    }
                }
            });

            return rowView;
        }
    }
}
