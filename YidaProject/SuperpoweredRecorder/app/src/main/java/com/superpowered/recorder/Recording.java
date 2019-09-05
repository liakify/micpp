package com.superpowered.recorder;

import android.media.MediaMetadataRetriever;
import android.text.format.DateFormat;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

/*
 *  This class represents a recorded file
 *  TODO: Currently a skeleton, to update to contain audio file / file path etc.
 */
public class Recording implements Serializable, Comparable<Recording> {
    private File file;
    private String name;
    private Date date;
    private long duration; //measured in timeUnits, 1 unit = 0.1 second
    private String filePath;

    public Recording(String filePath) {
        this.file = new File(filePath);
        initFields();
    }

    public Recording(File file) {
        this.file = file;
        initFields();
    }

    private void initFields() {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(file.getPath());
        this.name = file.getName();
        this.date = new Date(file.lastModified());
        this.duration = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 100;
        this.filePath = file.getPath();
    }

    public File getFile() { return file; }

    public String getFilePath(){ return filePath; }

    public long getDuration() {
        return duration;
    }

    public String getName() {
        return name;
    }

    public Date getDate() {
        return date;
    }

    public String getDurationString() {
        return timeToString(duration, false);
    }

    public String getDateString() {
        return DateFormat.format("dd/MM/yyyy", date).toString();
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

    @Override
    public int compareTo(Recording o) {
        return -this.getDate().compareTo(o.getDate());
    }
}
