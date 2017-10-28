package com.screen.record;

import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;


public class RecordService extends Service {
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;

    private boolean running;
    private int width = 720;
    private int height = 1280;
    private int dpi;


    @Override
    public IBinder onBind(Intent intent) {
        return new RecordBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HandlerThread serviceThread = new HandlerThread("service_thread",
                android.os.Process.THREAD_PRIORITY_BACKGROUND);
        serviceThread.start();
        running = false;
        mediaRecorder = new MediaRecorder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void setMediaProject(MediaProjection project) {
        mediaProjection = project;
    }

    public boolean isRunning() {
        return running;
    }

    public void setConfig(int width, int height, int dpi) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
    }

    public boolean startRecord() {
        if (mediaProjection == null || running) {
            return false;
        }

        initRecorder();
//        initRecorderTest();
        createVirtualDisplay();
        mediaRecorder.start();
        running = true;
        return true;
    }

    public boolean stopRecord() {
        if (!running) {
            return false;
        }
        running = false;
        mediaRecorder.stop();
        mediaRecorder.reset();
        virtualDisplay.release();
        mediaProjection.stop();

        return true;
    }

    private void createVirtualDisplay() {
        try {
            virtualDisplay = mediaProjection.createVirtualDisplay("MainScreen", width, height, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mediaRecorder.getSurface(), null, null);
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("sss", "createVirtualDisplay() Exception : " + e.toString());
        }
    }

    private void initRecorder() {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setOutputFile(getSaveDirectory() + System.currentTimeMillis() + ".mp4");
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
        mediaRecorder.setVideoFrameRate(30);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initRecorderTest() {
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        camcorderProfile.videoFrameWidth = width;
        camcorderProfile.videoFrameHeight = height;
        mediaRecorder.setProfile(camcorderProfile);
//    mediaRecorder.setOrientationHint(0);
        mediaRecorder.setCaptureRate(0.5);

        mediaRecorder.setOutputFile(getSaveDirectory() + FileUtil.FILE_NAME);
//    getProfileInfo();
//    mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

//    mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

//    mediaRecorder.setVideoSize(width, height);
//    mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
//    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//    mediaRecorder.setVideoEncodingBitRate(5 * 1024 * 1024);
//    mediaRecorder.setVideoFrameRate(30);
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getSaveDirectory() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "ScreenRecordDemo" + "/";

            File file = new File(rootDir);
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    return null;
                }
            }

            Toast.makeText(getApplicationContext(), rootDir, Toast.LENGTH_SHORT).show();

            return rootDir;
        } else {
            return null;
        }
    }

    public class RecordBinder extends Binder {
        public RecordService getRecordService() {
            return RecordService.this;
        }
    }

    private void getProfileInfo() {
        CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        if (camcorderProfile != null) {
            int profileHeight = camcorderProfile.videoFrameHeight;
            int profileWidth = camcorderProfile.videoFrameWidth;
            int profileCodec = camcorderProfile.videoCodec;
            int profileFrameRate = camcorderProfile.videoFrameRate;
            int profileBitRate = camcorderProfile.videoBitRate;
            int profileFormat =  camcorderProfile.fileFormat;
            int profileQuality = camcorderProfile.quality;

            Log.d("zzz", "profileHeight = " + profileHeight + "\n profileWidth = " + profileWidth
                    + "\n profileCodec = " + profileCodec + "\n profileFrameRate = " + profileFrameRate
                    + "\n profileBitRate = " + profileBitRate + "\n profileFormat = " + profileFormat
                    + "\n profileQuality = " + profileQuality );
        }
    }
}