//package com.screen.gldemo;
//
//import android.Manifest;
//import android.content.Context;
//import android.content.pm.PackageManager;
//import android.graphics.SurfaceTexture;
//import android.hardware.camera2.CameraAccessException;
//import android.hardware.camera2.CameraCaptureSession;
//import android.hardware.camera2.CameraCharacteristics;
//import android.hardware.camera2.CameraDevice;
//import android.hardware.camera2.CameraManager;
//import android.hardware.camera2.CaptureRequest;
//import android.hardware.camera2.params.StreamConfigurationMap;
//import android.os.Handler;
//import android.os.HandlerThread;
//import android.support.annotation.NonNull;
//import android.support.v4.content.PermissionChecker;
//import android.util.Size;
//import android.view.Surface;
//import android.view.SurfaceView;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.Comparator;
//import java.util.List;
//
///**
// * Created by panwenjuan on 17-11-1.
// */
//public class VideoSurfaceView extends SurfaceView {
//
//
//    private EGLUtils mEglUtils;
//    private GLFramebuffer mFramebuffer;
//    private GLRenderer mRenderer;
//    private SurfaceTexture mSurfaceTexture;
//    private Surface mSurface;
//
//    private final Object mObject = new Object();
//
//    private String mCameraId;
//    private CameraManager mCameraManager;
//    private CameraCaptureSession mCameraCaptureSession;
//    private CameraDevice mCameraDevice;
//    private Handler mHandler;
//
//
//    private VideoEncoder mVideoEncoder;
//    private final Object vObject = new Object();
//
//
//    private int screenWidth, screenHeight;
//    private int previewWidth;
//    private int previewHeight;
//
//    public VideoSurfaceView(Context context) {
//        super(context);
//        init(context);
//    }
//    public VideoSurfaceView(Context context, AttributeSet attributeSet) {
//        super(context,attributeSet);
//        init(context);
//    }
//    private void init(Context context){
//        mFramebuffer = new GLFramebuffer(context);
//        mRenderer = new GLRenderer(context);
//        initCamera2();
//        getHolder().addCallback(new SurfaceHolder.Callback() {
//            @Override
//            public void surfaceCreated(SurfaceHolder surfaceHolder) {
//
//            }
//            @Override
//            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
//                screenWidth = i1;
//                screenHeight = i2;
//                Thread thread = new Thread(){
//                    @Override
//                    public void run() {
//                        super.run();
//                        mEglUtils = new EGLUtils();
//                        mEglUtils.initEGL(EGL14.EGL_NO_CONTEXT,getHolder().getSurface());
//
//                        mRenderer.initShader();
//
//                        Size mPreviewSize =  getPreferredPreviewSize(mSizes, screenWidth, screenHeight);
//                        previewWidth = mPreviewSize.getHeight();
//                        previewHeight = mPreviewSize.getWidth();
//                        int left,top,viewWidth,viewHeight;
//                        if(previewHeight > previewWidth){
//                            left = 0;
//                            viewWidth = screenWidth;
//                            viewHeight = (int)(previewHeight*1.0f/previewWidth*viewWidth);
//                            top = (screenHeight - viewHeight)/2;
//                        }else{
//                            top = 0;
//                            viewHeight = screenHeight;
//                            viewWidth = (int)(previewWidth*1.0f/previewHeight*viewHeight);
//                            left = (screenWidth - viewWidth)/2;
//                        }
//                        Rect rect = new Rect();
//                        rect.left = left;
//                        rect.top = top;
//                        rect.right = left + viewWidth;
//                        rect.bottom = top + viewHeight;
//
//                        mFramebuffer.initFramebuffer(previewWidth,previewHeight);
//
//                        mSurfaceTexture = mFramebuffer.getSurfaceTexture();
//                        mSurfaceTexture.setDefaultBufferSize(previewWidth, previewHeight);
//                        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
//                            @Override
//                            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//                                synchronized (mObject) {
//                                    mObject.notifyAll();
//                                }
//                            }
//                        });
//                        openCamera2();
//                        while (true){
//                            synchronized (mObject) {
//                                try {
//                                    mObject.wait();
//                                } catch (InterruptedException e) {
//                                    e.printStackTrace();
//                                }
//                            }
//
//                            if(mSurfaceTexture == null){
//                                break;
//                            }
//                            mFramebuffer.drawFrameBuffer(previewWidth,previewHeight);
//
//                            if(mVideoEncoder != null){
//                                synchronized (vObject){
//                                    vObject.notifyAll();
//                                }
//                            }
//
//                            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
//
//                            GLES20.glViewport(rect.left,rect.top,rect.width(),rect.height());
//
//                            mFramebuffer.drawFrame();
//
//                            mRenderer.drawFrame();
//                            mEglUtils.swap();
//
//                        }
//                        mEglUtils.release();
//
//                    }
//                };
//                thread.start();
//            }
//
//            @Override
//            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
//                if(mCameraCaptureSession != null){
//                    mCameraCaptureSession.getDevice().close();
//                    mCameraCaptureSession.close();
//                    mCameraCaptureSession = null;
//                }
//                if(mSurface != null){
//                    mSurface.release();
//                    mSurface = null;
//                }
//                if(mSurfaceTexture != null){
//                    mSurfaceTexture.release();
//                    mSurfaceTexture = null;
//                    synchronized (mObject) {
//                        mObject.notifyAll();
//                    }
//                }
//            }
//        });
//    }
//
//    private boolean stop = false;
//    public void startVideo(){
//        if(mVideoEncoder != null){
//            return;
//        }
//        mVideoEncoder = new VideoEncoder();
//
//        mVideoEncoder.setVideoSize(previewWidth,previewHeight);
//        mVideoEncoder.initEncoder();
//        stop = false;
//        Thread thread = new Thread(){
//            @Override
//            public void run() {
//                super.run();
//                GLRenderer renderer = new GLRenderer(getContext());
//                EGLUtils eglUtils = new EGLUtils();
//                eglUtils.initEGL(mEglUtils.getContext(),mVideoEncoder.getSurface());
//                renderer.initShader();
//
//
//
//                while (true){
//                    synchronized (vObject) {
//                        try {
//                            vObject.wait();
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                    if(mSurfaceTexture == null){
//                        break;
//                    }
//                    if(stop){
//                        break;
//                    }
//
//                    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
//                    GLES20.glViewport(0,0,mVideoEncoder.getVideoWidth(),mVideoEncoder.getVideoHeight());
//                    mFramebuffer.drawFrame();
//                    renderer.drawFrame();
//
//                    eglUtils.swap();
//                }
//                eglUtils.release();
//
//            }
//        };
//        thread.start();
//        mVideoEncoder.start();
//    }
//
//    public void stopVideo(){
//        mVideoEncoder.stop();
//        stop = true;
//        mVideoEncoder = null;
//    }
//
//    private Size[] mSizes;
//    private void initCamera2() {
//        HandlerThread handlerThread = new HandlerThread("Camera2");
//        handlerThread.start();
//        mHandler = new Handler(handlerThread.getLooper());
//        mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
//        try {
//            String[] CameraIdList = mCameraManager.getCameraIdList();
//            mCameraId = CameraIdList[0];
//            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(mCameraId);
//            characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
//            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//            if(map != null){
//                mSizes = map.getOutputSizes(SurfaceTexture.class);
//            }
//
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//    private void openCamera2(){
//        if (PermissionChecker.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
//            try {
//                mCameraManager.openCamera(mCameraId, stateCallback, mHandler);
//            } catch (CameraAccessException e) {
//                e.printStackTrace();
//            }
//        }
//    }
//    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
//        @Override
//        public void onOpened(@NonNull CameraDevice cameraDevice) {
//            mCameraDevice = cameraDevice;
//            takePreview();
//        }
//
//        @Override
//        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
//            if (mCameraDevice != null) {
//                mCameraDevice.close();
//                mCameraDevice = null;
//            }
//        }
//
//        @Override
//        public void onError(@NonNull CameraDevice cameraDevice, int i) {
//
//        }
//    };
//
//    private void takePreview() {
//        try {
//            mSurface = new Surface(mSurfaceTexture);
//            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            builder.addTarget(mSurface);
//            mCameraDevice.createCaptureSession(Arrays.asList(mSurface), new CameraCaptureSession.StateCallback() {
//                @Override
//                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//                    if (null == mCameraDevice) return;
//                    mCameraCaptureSession = cameraCaptureSession;
//                    builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
//                    CaptureRequest previewRequest = builder.build();
//                    try {
//                        mCameraCaptureSession.setRepeatingRequest(previewRequest, null, mHandler);
//                    } catch (CameraAccessException e) {
//                        e.printStackTrace();
//                    }
//                }
//
//                @Override
//                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//
//                }
//            }, mHandler);
//
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
//    }
//    private Size getPreferredPreviewSize(Size[] sizes, int width, int height) {
//        List<Size> collectorSizes = new ArrayList<>();
//        for (Size option : sizes) {
//            if (width > height) {
//                if (option.getWidth() > width && option.getHeight() > height) {
//                    collectorSizes.add(option);
//                }
//            } else {
//                if (option.getHeight() > width && option.getWidth() > height) {
//                    collectorSizes.add(option);
//                }
//            }
//        }
//        if (collectorSizes.size() > 0) {
//            return Collections.min(collectorSizes, new Comparator<Size>() {
//                @Override
//                public int compare(Size s1, Size s2) {
//                    return Long.signum(s1.getWidth() * s1.getHeight() - s2.getWidth() * s2.getHeight());
//                }
//            });
//        }
//        return sizes[0];
//    }
//
//}