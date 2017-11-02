//package com.screen.gldemo;
//
//import android.media.AudioFormat;
//import android.media.AudioRecord;
//import android.media.MediaCodec;
//import android.media.MediaCodecInfo;
//import android.media.MediaFormat;
//import android.media.MediaMuxer;
//import android.media.MediaRecorder;
//import android.view.Surface;
//
//import java.io.IOException;
//import java.nio.ByteBuffer;
//
///**
// * Created by panwenjuan on 17-11-1.
// */
//public class VideoEncoder {
//
//    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO; //音频通道(单声道)
//    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT; //音频格式
//    private static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC; //音频源（麦克风）
//    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm"; //音频类型
//    private static final int SAMPLE_RATE = 44100; //采样率(CD音质)
//    private static final int BIT_RATE = 128000; //比特率
//    private static final int CHANNEL_COUNT = 1;//声道
//
//    private static final String VIDEO_MIME_TYPE = "video/avc";//视频类型
//    private static final int FRAME_RATE = 25; //帧率
//    private static final int FI_FRAME_INTERVAL = 5; //关键帧时间
//    private static final int MAX_WIDTH = 1280;
//    private static final int MAX_HEIGHT = 1280;
//    private int videoWidth = 1280;
//    private int videoHeight = 720;
//
//    private MediaMuxer mediaMuxer;
//    private MediaCodec videoMediaCodec;
//    private MediaCodec audioMediaCodec;
//
//    private Surface surface;
//
//    //初始化
//    public void initEncoder(){
//        micStop = true;
//        muxerVideoStart = false;
//        muxerAudioStart = false;
//        muxerStart = false;
//        initVideo();
//        initAudio();
//        initMuxer();
//    }
//    private void initVideo(){
//        MediaFormat mediaFormat = MediaFormat.createVideoFormat(VIDEO_MIME_TYPE, videoWidth, videoHeight);
//        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
//        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
//        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
//        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, FI_FRAME_INTERVAL);
//        try {
//            videoMediaCodec = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        videoMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        surface = videoMediaCodec.createInputSurface();
//        videoMediaCodec.start();
//    }
//    private void initAudio(){
//        MediaFormat audioFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE,SAMPLE_RATE,CHANNEL_COUNT);
//        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
//        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
//        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK, AudioFormat.CHANNEL_IN_MONO);//立体声
//        try {
//            audioMediaCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        audioMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
//        audioMediaCodec.start();
//    }
//
//    private void initMuxer(){
//        String path = "/storage/emulated/0/360/" + System.currentTimeMillis() + ".mp4";
//        try {
//            mediaMuxer = new MediaMuxer(path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//    public Surface getSurface() {
//        return surface;
//    }
//    //视频尺寸随便写的，可以用摄像头支持尺寸
//    public void setVideoSize(int width,int height){
//        int w = width;
//        int h = height;
//        if(w > h){
//            while (w > MAX_WIDTH){
//                w = w/2;
//                h = h/2;
//            }
//        }else{
//            while (h > MAX_HEIGHT){
//                w = w/2;
//                h = h/2;
//            }
//        }
//        videoWidth = w;
//        videoHeight = h;
//    }
//
//    public int getVideoWidth(){
//        return videoWidth;
//    }
//    public int getVideoHeight(){
//        return videoHeight;
//    }
//
//    public void start(){
//        Thread videoThread = new Thread(new VideoRun());
//        Thread micThread = new Thread(new MicRun());
//        Thread audioThread = new Thread(new AudioRun());
//        micThread.start();
//        videoThread.start();
//        audioThread.start();
//    }
//    public void stop(){
//        videoMediaCodec.signalEndOfInputStream();
//        micStop = false;
//    }
//
//    //因为有多个线程调用mediaMuxer，所以使用synchronized来防止同时操作mediaMuxer
//    private boolean muxerVideoStart = false;
//    private boolean muxerAudioStart = false;
//    private boolean muxerStart = false;
//    private synchronized void muxerStart(){
//        if(muxerVideoStart && muxerAudioStart && !muxerStart){
//            muxerStart = true;
//            mediaMuxer.start();
//        }
//    }
//    private long outputStartTime = 0;
//    //写入数据，音频和视频写入都要调用这个
//    private synchronized void muxerWriteData(int mTrackIndex,ByteBuffer encodedData,MediaCodec.BufferInfo mBufferInfo){
//        if(muxerStart){
//            if(outputStartTime == 0){
//                outputStartTime = System.nanoTime() / 1000L;
//            }
//            //数据写入的时间戳
//            mBufferInfo.presentationTimeUs = System.nanoTime() / 1000L - outputStartTime;
//            mediaMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
//        }
//    }
//    //释放mediaMuxer
//    private synchronized void muxerRelease(){
//        if(audioMediaCodec == null && videoMediaCodec == null && mediaMuxer != null){
//            mediaMuxer.stop();
//            mediaMuxer.release();
//            mediaMuxer = null;
//        }
//    }
//    //录像数据线程，靠surface获取数据
//    public class VideoRun implements Runnable{
//        @Override
//        public void run() {
//            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
//            int mTrackIndex = -1;
//            while (true){
//                //dequeueOutputBuffer:没有数据时阻塞线程，时间1000，有数据时返回一个整数状态
//                int encoderStatus = videoMediaCodec.dequeueOutputBuffer(mBufferInfo, 1000);
//                if (encoderStatus >= 0) {
//                    if(!muxerStart){
//                        continue;
//                    }
//                    ByteBuffer encodedData = videoMediaCodec.getOutputBuffer(encoderStatus);
//                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                        mBufferInfo.size = 0;
//                    }
//                    if (mBufferInfo.size != 0) {
//                        encodedData.position(mBufferInfo.offset);
//                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
//                        muxerWriteData(mTrackIndex, encodedData, mBufferInfo);
//                    }
//                    videoMediaCodec.releaseOutputBuffer(encoderStatus, false);
//                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                        //调用 videoMediaCodec.signalEndOfInputStream();时会进入这里
//                        break;
//                    }
//                }else if(encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER){
//
//                }else if(encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
//                    //录音和录像都会进入这里，可能会多次进入，
//                    // mediaFormat相同而多次调用mediaMuxer.addTrack会报错
//                    if(mTrackIndex == -1){
//                        if(!muxerVideoStart){
//                            muxerVideoStart = true;
//                            MediaFormat mediaFormat = videoMediaCodec.getOutputFormat();
//                            mTrackIndex = mediaMuxer.addTrack(mediaFormat);
//                            muxerStart();
//                        }
//                    }
//                }else{
//
//                }
//            }
//            if(surface != null){
//                surface.release();
//                surface = null;
//            }
//            if (videoMediaCodec != null) {
//                videoMediaCodec.stop();
//                videoMediaCodec.release();
//                videoMediaCodec = null;
//            }
//            muxerRelease();
//        }
//    }
//    //麦克风线程
//    private boolean micStop = true;
//    public class MicRun implements Runnable{
//        @Override
//        public void run() {
//            int bufferSizeInBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
//            AudioRecord audioRecord = new AudioRecord(
//                    AUDIO_SOURCE,//音频源
//                    SAMPLE_RATE,//采样率
//                    CHANNEL_CONFIG,//音频通道
//                    AUDIO_FORMAT,//音频格式
//                    bufferSizeInBytes//缓冲区
//            );
//            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
//                return;
//            }
//            audioRecord.startRecording();
//            byte[] buffer = new byte[bufferSizeInBytes];
//            long audioStartTime = System.nanoTime();
//            while (micStop) {
//                //时间戳随便给，和写入时间戳没有关系
//                long presentationTimeUs = (System.nanoTime() - audioStartTime) / 1000;
//                int bufferReadResult = audioRecord.read(buffer, 0, bufferSizeInBytes);
//                if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION) {
//
//                }
//                //将获得的数据传给audioMediaCodec
//                audioInputBuffer(buffer, presentationTimeUs, false);
//            }
//
//            long presentationTimeUs = (System.nanoTime() - audioStartTime) / 1000;
//            int bufferReadResult = audioRecord.read(buffer, 0, bufferSizeInBytes);
//            if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION) {
//
//            }
//            //停止录音时通知audioMediaCodec
//            audioInputBuffer(buffer, presentationTimeUs, true);
//            audioRecord.stop();
//            audioRecord.release();
//        }
//        public void audioInputBuffer(byte[] buffer, long presentationTimeUs, boolean audioEnd) {
//            int inputBufferIndex = audioMediaCodec.dequeueInputBuffer(-1);
//            if (inputBufferIndex >= 0) {
//                ByteBuffer inputBuffer = audioMediaCodec.getInputBuffer(inputBufferIndex);
//                inputBuffer.clear();
//                inputBuffer.put(buffer);
//                if (audioEnd) {
//                    audioMediaCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
//                } else {
//                    audioMediaCodec.queueInputBuffer(inputBufferIndex, 0, buffer.length, presentationTimeUs, 0);
//                }
//            }
//        }
//    }
//
//    public class AudioRun implements Runnable{
//        @Override
//        public void run() {
//            int mTrackIndex = -1;
//            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
//            while (true) {
//                int encoderIndex = audioMediaCodec.dequeueOutputBuffer(mBufferInfo, 1000);
//                if (encoderIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
//
//                } else if (encoderIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
//
//                    if(!muxerAudioStart){
//                        muxerAudioStart = true;
//                        MediaFormat mediaFormat = audioMediaCodec.getOutputFormat();
//                        mTrackIndex = mediaMuxer.addTrack(mediaFormat);
//                        muxerStart();
//                    }
//                } else if (encoderIndex < 0) {
//
//                } else {
//                    if(!muxerStart){
//                        continue;
//                    }
//                    ByteBuffer encodedData = audioMediaCodec.getOutputBuffer(encoderIndex);
//                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
//                        mBufferInfo.size = 0;
//                    }
//                    if (mBufferInfo.size != 0) {
//                        encodedData.position(mBufferInfo.offset);
//                        encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
//                        muxerWriteData(mTrackIndex, encodedData, mBufferInfo);
//                        audioMediaCodec.releaseOutputBuffer(encoderIndex, false);
//                    }
//                    if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
//                        break;
//                    }
//                }
//            }
//            if (audioMediaCodec != null) {
//                audioMediaCodec.stop();
//                audioMediaCodec.release();
//                audioMediaCodec = null;
//            }
//            muxerRelease();
//        }
//    }
//}
