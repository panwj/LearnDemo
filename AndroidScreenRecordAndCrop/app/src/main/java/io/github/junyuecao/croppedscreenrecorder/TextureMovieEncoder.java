/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.junyuecao.croppedscreenrecorder;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;
import io.github.junyuecao.croppedscreenrecorder.gles.EglCore;
import io.github.junyuecao.croppedscreenrecorder.gles.Texture2dProgram;
import io.github.junyuecao.croppedscreenrecorder.gles.WindowSurface;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;


/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 * <p>
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 * <p>
 * To use:
 * <ul>
 * <li>create TextureMovieEncoder object
 * <li>create an EncoderConfig
 * <li>call TextureMovieEncoder#startRecording() with the config
 * <li>call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 * <li>for each frame, after latching it with SurfaceTexture#updateTexImage(),
 * call TextureMovieEncoder#frameAvailable().
 * </ul>
 * <p>
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class TextureMovieEncoder implements Runnable, SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "TextureMovieEncoder";
    private static final boolean VERBOSE = true;

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_UPDATE_SHARED_CONTEXT = 4;
    private static final int MSG_AUDIO_FRAME_AVAILABLE = 5;
    private static final int MSG_QUIT = 6;
    // ----- accessed exclusively by encoder thread -----
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;
    private MainFrameRect mFullScreen;
    private int mTextureId;
    private int mFrameNum;
    private VideoEncoderCore mVideoEncoder;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    //TextureMovieEncoder继承Runnable, 本质是一个线程, 防止多线程破坏状态,起到保护作用
    private final Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;//表示守护线程（mReadyFence）已经运行
    private boolean mPause;
    private Callback mCallback;
    private HandlerThread mVideoFrameSender;
    private Handler mVideoFrameHandler;
    private SurfaceTexture mSurfaceTexture;
    private Runnable mUpdate = new Runnable() {
        @Override
        public void run() {
            if (mSurfaceTexture != null) {
                mSurfaceTexture.updateTexImage();
            }
        }
    };
    private Surface mSurface;
    private float mTopCropped;
    private float mBottomCropped;
    private float[] mTransform;
    private RecordCallback mRecordCallback;
    // Should save first frame as a cover
    private boolean mFirstFrameSaved;
    private int mVideoWidth;
    private int mVideoHeight;
    private File mCoverImageFile;

    public Callback getCallback() {
        return mCallback;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    public void startRecording(EncoderConfig config) {
        Log.d(TAG, "Encoder: startRecording()");
        synchronized(mReadyFence) {
            if (mRunning) {
                Log.w(TAG, "Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(this, "TextureMovieEncoder").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public void stopRecording() {
        synchronized(mReadyFence) {
            if (!mReady) {
                return;
            }
        }

        mHandler.removeCallbacks(mUpdate);
        synchronized(this) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
            mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        }
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    public void pauseRecording() {
        synchronized (mReadyFence) {
            if (isRecording()) {
                if (!mPause) {
                    mPause = true;
                    if (mVideoEncoder != null) {
                        mVideoEncoder.setPause(mPause);
                    }
                } else {
                    mPause = false;
                    if (mVideoEncoder != null) {
                        mVideoEncoder.setPause(mPause);
                    }
                    Log.d(TAG, "pauseRecording() ---------------   pause ---> resume success");
                }
            }
        }
    }

    /**
     * Returns true if recording has been started.
     */
    public boolean isRecording() {
        synchronized(mReadyFence) {
            return mRunning;
        }
    }

    /**
     * Tells the video recorder to refresh its EGL surface.  (Call from non-encoder thread.)
     */
    public void updateSharedContext(EGLContext sharedContext) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_SHARED_CONTEXT, sharedContext));
    }


    /**
     * @see #frameAvailable(SurfaceTexture, long)
     */
    public void frameAvailable(SurfaceTexture st) {
        frameAvailable(st, st.getTimestamp());
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     * @param timestamp present timestamp in nanosecond
     */
    public void frameAvailable(SurfaceTexture st, long timestamp) {
        synchronized(mReadyFence) {
            if (!mReady) {
                return;
            }
        }

        if (mTransform == null) {
            mTransform = new float[16];
        }
        st.getTransformMatrix(mTransform);
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "HEY: got SurfaceTexture with timestamp of zero");
            return;
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, mTransform));
    }

    public void audioFrameAvailable(ByteBuffer buffer, int size, boolean endOfStream) {
        synchronized(mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        if (mVideoEncoder != null) {
            mVideoEncoder.enqueueAudioFrame(buffer, size, endOfStream);
        }
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(int id) {
        synchronized(mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized(mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Log.d(TAG, "Encoder thread exiting");
        synchronized(mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }

    public void setRecordCallback(RecordCallback recordCallback) {
        mRecordCallback = recordCallback;
    }

    public RecordCallback getRecordCallback() {
        return mRecordCallback;
    }

    /**
     * Starts recording.
     */
    private void handleStartRecording(EncoderConfig config) {
        Log.d(TAG, "handleStartRecording " + config);
        mFrameNum = 0;
        prepareEncoder(config);
    }

    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     *
     * @param transform      The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(float[] transform, long timestampNanos) {
        if (VERBOSE) {
            Log.d(TAG, "handleFrameAvailable tr=" + transform);
        }

        mVideoEncoder.drainEncoder(false);
        mFullScreen.drawFrame(mTextureId, transform);

        /*if (BuildConfig.DEBUG) {
            drawBox(mFrameNum++);
        }*/

        // used for save a frame
        // saveFirstFrame();

        mInputWindowSurface.setPresentationTime(timestampNanos);
        mInputWindowSurface.swapBuffers();
    }

    // private void saveFirstFrame() {
    //     if (mFirstFrameSaved) {
    //         return;
    //     }
    //     int width = mInputWindowSurface.getWidth();
    //     int height = mInputWindowSurface.getHeight();
    //     ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
    //     buf.order(ByteOrder.LITTLE_ENDIAN);
    //     buf.rewind();
    //     GLES20.glReadPixels(0, 0, width, height,
    //             GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
    //     new Thread(new ImageSaverThread(buf, mCoverImageFile, width, height)).start();
    //     mVideoEncoder.setCoverPath(mCoverImageFile.getAbsolutePath());
    //
    //     mFirstFrameSaved = true; // 已经保存
    // }

    /**
     *
     * @param mp4 get screenshot file
     * @return screenshot file
     */
    private File getCoverFile(@NonNull File mp4) {
        return new File(mp4.getParent(), "cover_" + mp4.getName().replace(".mp4", "") + ".jpg");
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        Log.d(TAG, "handleStopRecording");

        mVideoEncoder.drainEncoder(true);
        releaseEncoder();
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int id) {
        Log.d(TAG, "handleSetTexture " + id);
        mTextureId = id;
    }

    /**
     * Tears down the EGL surface and context we've been using to feed the MediaCodec input
     * surface, and replaces it with a new one that shares with the new context.
     * <p>
     * This is useful if the old context we were sharing with went away (maybe a GLSurfaceView
     * that got torn down) and we need to hook up with the new one.
     */
    private void handleUpdateSharedContext(EGLContext newSharedContext) {
        Log.d(TAG, "handleUpdatedSharedContext " + newSharedContext);

        // Release the EGLSurface and EGLContext.
        mInputWindowSurface.releaseEglSurface();
        mFullScreen.release(false);
        mEglCore.release();

        // Create a new EGLContext and recreate the window surface.
        mEglCore = new EglCore(newSharedContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface.recreate(mEglCore);
        mInputWindowSurface.makeCurrent();

        // Create new programs and such for the new context.
        mFullScreen = new MainFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mFullScreen.setTopCropped(mTopCropped);
        mFullScreen.setBottomCropped(mBottomCropped);
    }

    private void handleAudioFrameAvailable(boolean endOfStream) {
        mVideoEncoder.drainAudio(endOfStream);
    }

    private void prepareEncoder(EncoderConfig config) {
        Log.d(TAG, "prepareEncoder() enter...");
        mTopCropped = config.mTopCropped;
        mBottomCropped = config.mBottomCropped;
        //对高度做了处理，录屏不包含状态栏和虚拟键的高度
        mVideoHeight = (int) (config.mHeight * (1f - mTopCropped - mBottomCropped));
        if (mVideoHeight % 2 != 0) {
            mVideoHeight += 1; // Pixels must be even
        }
        mVideoWidth = config.mWidth;
        mCoverImageFile = getCoverFile(config.mOutputFile);//保存第一帧图像的位置，相当于截图
        try {
            //init MediaMuxer; video MediaCodec; audio MediaCodec
            mVideoEncoder = new VideoEncoderCore(mVideoWidth, mVideoHeight, config.mBitRate, config.mOutputFile);
            mVideoEncoder.setRecordCallback(mRecordCallback);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        mEglCore = new EglCore(config.mEglContext, EglCore.FLAG_RECORDABLE);
        mInputWindowSurface = new WindowSurface(mEglCore, mVideoEncoder.getInputSurface(), true);
        mInputWindowSurface.makeCurrent();

        mFullScreen = new MainFrameRect(
                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mFullScreen.setTopCropped(config.mTopCropped);
        mFullScreen.setBottomCropped(config.mBottomCropped);

        mTextureId = mFullScreen.createTextureObject();

        Log.d(TAG, "Texture created id: " + mTextureId);

        mVideoFrameSender = new HandlerThread("SurfaceFrameSender");
        mVideoFrameSender.start();
        mVideoFrameHandler = new Handler(mVideoFrameSender.getLooper());
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture.setOnFrameAvailableListener(this, mVideoFrameHandler); // 为了不阻塞TextureMovieEncoder ，需要额外的线程
        mSurfaceTexture.setDefaultBufferSize(config.mWidth, config.mHeight);
        mSurface = new Surface(mSurfaceTexture);

        if (mCallback != null) {
            mCallback.onInputSurfacePrepared(mSurface);
        }

        mFirstFrameSaved = false;
        Log.d(TAG, "prepareEncoder() exit...");
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Log.d(TAG, "onFrameAvailable()...");
        mHandler.postDelayed(mUpdate, 16);

        frameAvailable(surfaceTexture);
    }

    private void releaseEncoder() {
        if (mVideoEncoder != null) {
            mVideoEncoder.release();
            mVideoEncoder = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mInputWindowSurface != null) {
            mInputWindowSurface.release();
            mInputWindowSurface = null;
        }
        if (mFullScreen != null) {
            mFullScreen.release(false);
            mFullScreen = null;
        }
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        if (mVideoFrameHandler != null) {
            mVideoFrameHandler = null;
        }
        if (mVideoFrameSender != null) {
            mVideoFrameSender.quit();
            mVideoFrameSender = null;
        }
    }

    /**
     * Draws a box, with position offset.
     */
    private void drawBox(int posn) {
        final int width = mInputWindowSurface.getWidth();
        int xpos = (posn * 4) % (width - 50);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, 0, 100, 100);
        GLES20.glClearColor(1.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    public interface Callback {
        /**
         * called when surface prepared
         * @param surface a prepared surface
         */
        void onInputSurfacePrepared(Surface surface);
    }

    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     * with reasonable defaults for those and bit rate.
     */
    public static class EncoderConfig {
        final File mOutputFile;
        final int mWidth;
        final int mHeight;
        final float mTopCropped;
        final float mBottomCropped;
        final int mBitRate;
        final EGLContext mEglContext;

        public EncoderConfig(File outputFile, int width, int height,
                             float topCropped, float bottomCropped,
                             int bitRate,
                             EGLContext sharedEglContext) {
            mOutputFile = outputFile;
            mWidth = width;
            mHeight = height;
            mTopCropped = topCropped;
            mBottomCropped = bottomCropped;
            mBitRate = bitRate;
            mEglContext = sharedEglContext;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mWidth + "x" + mHeight
                    + ", Crop with: " + mTopCropped + " and " + mBottomCropped
                    + "@" + mBitRate +
                    " to '" + mOutputFile.toString() + "' ctxt=" + mEglContext;
        }
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<TextureMovieEncoder> mWeakEncoder;

        public EncoderHandler(TextureMovieEncoder encoder) {
            mWeakEncoder = new WeakReference<TextureMovieEncoder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureMovieEncoder encoder = mWeakEncoder.get();
            if (encoder == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    encoder.handleStartRecording((EncoderConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    encoder.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    encoder.handleFrameAvailable((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    encoder.handleSetTexture(inputMessage.arg1);
                    break;
                case MSG_UPDATE_SHARED_CONTEXT:
                    encoder.handleUpdateSharedContext((EGLContext) inputMessage.obj);
                    break;
                case MSG_AUDIO_FRAME_AVAILABLE:
                    encoder.handleAudioFrameAvailable(inputMessage.arg1 == 1);
                    break;
                case MSG_QUIT:
                    Log.d(TAG, "Exit encoder loop");
                    Looper.myLooper().quit();
                    break;
                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }

}
