package com.remote.ar;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Point;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.remote.common.helpers.CameraPermissionHelper;
import com.remote.common.helpers.DisplayRotationHelper;
import com.remote.common.helpers.FullScreenHelper;
import com.remote.common.helpers.SnackbarHelper;
import com.remote.common.helpers.GestureHelper;
import com.remote.common.helpers.TrackingStateHelper;

import com.remote.common.rendering.ObjectRenderer;
import com.remote.common.rendering.PlaneRenderer;
import com.remote.common.rendering.BackgroundRenderer;
import com.remote.common.rendering.PointCloudRenderer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.net.wifi.WifiManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.os.StrictMode;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.barcode.BarcodeDetector;

import com.google.ar.core.PointCloud;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingState;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;


public class CameraTrackingActivity extends AppCompatActivity
        implements GLSurfaceView.Renderer  {
    private static final String TAG = CameraTrackingActivity.class.getSimpleName();

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;

    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private GestureHelper gestureHelper;

    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final ObjectRenderer virtualObjectShadow = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];
    private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";

    // Anchors created from taps used for object placing with a given color.
    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }

    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    //OLD
//    private static final double MIN_OPENGL_VERSION = 3.0;

    //AR vars
    private Anchor sceneAnchor;
    private boolean isSceneLoaded;
    private boolean isSceneUpdating;
    private boolean isRecording;
    private File sceneChache;
    private boolean isStreamingData;
    private int interactionMode = Constants.CAMERA_MODE;
    private AskCameraRecord recordTask;
    private AsyncTask cameraRecordTask;

    // UI vars
    private ImageButton cameraModeButton;
    private ImageButton objectModeButton;
    private ImageButton connectButton;
    private ImageButton recordButton;
    private ImageButton updateSceneButton;
    private ProgressBar taskProgress;
    private TextView currentRecordedFrame;
    private TextView scanMessage;
    private BarcodeDetector ipDetector;
    private SurfaceTexture text;
    private Surface mirror;

    // Net vars
    private NetworkManager netManager;
    private Handler netHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                // STATE MESSAGE
                case 0:
                    if (!isSceneUpdating) {
                        switch ((int) msg.obj) {
                            case 0:
                                setcameraStream(false);

                                break;
                            case 1:
                                setcameraStream(false);
                                break;
                            case 2:
                                if (!isSceneLoaded && !isSceneUpdating) {
                                    isSceneLoaded = true;
                                    requestSceneUpdate(findViewById(R.id.connect));
                                }

                                break;
                        }

                        updateConnectButtonStatus((int) msg.obj);
                    }
                    break;
            }

            return false;
        }
    });
    private Handler sceneUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
                setSceneUpdateStatus(msg.what);

                return false;
            }
    });
    private Handler recordingUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {

            switch (msg.what){
                //RECORDING
                case 0:
                    recordButton.setImageResource(R.drawable.round_stop_white_18dp);
                    recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorError));
                    taskProgress.setVisibility(View.VISIBLE);
                    taskProgress.setMax(250);
                    currentRecordedFrame.setVisibility(View.VISIBLE);
                    break;
                //STOPPED
                case 1:
                    recordButton.setImageResource(R.drawable.round_play_arrow_white_18dp);
                    recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
                    isRecording = false;
                    cameraRecordTask.cancel(true);
                    taskProgress.setVisibility(View.GONE);
                    currentRecordedFrame.setVisibility(View.GONE);

                    break;
                //RECORDING UPDATE
                case 2:
                    try{
                        taskProgress.setProgress(Integer.parseInt((String)msg.obj));
                        currentRecordedFrame.setText((String)msg.obj);
                    }catch (Exception e){
                        Log.i("Net","Cast frame int error");
                    }

                    break;
            }


            return false;
        }
    });

    private void setSceneUpdateStatus(int status){
        switch (status){
            // STATE MESSAGE
            case 0:
//                int length = (int) sceneChache.length();
//                Log.i("Net",String.valueOf(length));
//                loadScene(sceneChache);
                updateSceneButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
                isSceneUpdating = false;

                break;
            case 1:
                updateSceneButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorError));
                isSceneUpdating = false;
                break;

            case 2:
                updateSceneButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorWaiting));
                isSceneUpdating = true;
                break;
        }
    }


    private void loadScene(File file){
        String path = "scene_cache.gltf";
        Log.i("Net","Load: "+  file);

    }


    private void setInteractionMode(int newMode) {
        Log.i("Net","current"+ String.valueOf(interactionMode)+" // new interactionMode: "+ String.valueOf(newMode));
        //Set new interactionMode
        if(newMode != interactionMode){
            switch (newMode) {
                case Constants.CAMERA_MODE:
                    setObjectStream(false);

                    objectModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
                    cameraModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorIdle));

                    break;
                case Constants.OBJECT_MODE:
                    setcameraStream(false);

                    objectModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorIdle));
                    cameraModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
                    break;
            }

            interactionMode = newMode;
        }
        //Start selected interactionMode
        else{
            switch (newMode) {
                case Constants.CAMERA_MODE:
                    setcameraStream(true);
                    break;
                case Constants.OBJECT_MODE:
                    setObjectStream(true);
                    break;
            }
        }

    }


    public void showConnexionDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setCancelable(true);
        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.button_connect);
        alertDialogBuilder.setView(input);

        alertDialogBuilder.setMessage("Connexion");
        alertDialogBuilder.setPositiveButton("yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Log.i("Net","Trying to connect");
                        netManager.connect(input.getText().toString());
                        updateConnectButtonStatus(3);
                    }
                });

        alertDialogBuilder.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }


    public void showDeconnexionDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setMessage("Disconnect ?");
        alertDialogBuilder.setPositiveButton("yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Log.i("Net","Trying to disconnect");
                        if(netManager != null){
                            netManager.disconnect();
                        }

                        // Reset scene vars
                        // TODO: make a func ?
                        isSceneLoaded = false;
                        isSceneUpdating = false;

                    }
                });

        alertDialogBuilder.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }


    public void onClickButtonConnexion(View v)
    {
        if(netManager.mState == 0){
            showConnexionDialog();
        }
        else{
            showDeconnexionDialog();
        }
    }

    public void setcameraStream(boolean state){
        if(state == false){
            isStreamingData = false;
            cameraModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorIdle));
            recordButton.setVisibility(View.GONE);
        }
        else if(netManager.mState == Constants.STATE_ONLINE){
            isStreamingData = true;
            cameraModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorOnline));
            recordButton.setVisibility(View.VISIBLE);
        }
        else{
            Toast.makeText(CameraTrackingActivity.this,
                    "Cannot stream the camera !", Toast.LENGTH_LONG).show();
        }
    }


    public void setObjectStream(boolean state){
        if(state == false){
            isStreamingData = false;
            objectModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorIdle));
            recordButton.setVisibility(View.GONE);
        }
        else if(netManager.mState == Constants.STATE_ONLINE){
            isStreamingData = true;
            objectModeButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorOnline));
            recordButton.setVisibility(View.VISIBLE);
        }
        else{
            Toast.makeText(CameraTrackingActivity.this,
                    "Cannot stream object !", Toast.LENGTH_LONG).show();
        }
    }


    public void updateConnectButtonStatus(int status){
        switch (status){
            case 0:
                scanMessage.setVisibility(View.VISIBLE);
                connectButton.setImageResource(R.drawable.round_cast_white_18dp);
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorIdle));
                break;
            case 1:
                connectButton.setImageResource(R.drawable.round_cast_white_18dp);
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorError));
                break;
            case 2:
                connectButton.setImageResource(R.drawable.round_cast_connected_white_18dp);
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorOnline));
                break;
            case 3:
                connectButton.setImageResource(R.drawable.round_cast_connected_white_18dp);
                connectButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorWaiting));
                scanMessage.setVisibility(View.GONE);
                break;
        }
    }


    public void onClickButtoncameraModeButton(View v)
    {

        if(isStreamingData && interactionMode == Constants.CAMERA_MODE){

            setcameraStream(false);
        }
        else{
            setInteractionMode(Constants.CAMERA_MODE);
        }
    }

    public void onClickButtonObjectMode(View v){
        if(isStreamingData && interactionMode == Constants.OBJECT_MODE){

            setObjectStream(false);
        }
        else{
            setInteractionMode(Constants.OBJECT_MODE);
        }

    }


    public void requestRecordCamera(View v){
        if(netManager.mState == 2 && !isSceneUpdating &&  isStreamingData && !isRecording) {
            isRecording = true;
            recordTask = new AskCameraRecord(recordingUpdateHandler, netManager.mAddress, netManager.mPort);
            cameraRecordTask = recordTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,"sdasd");
        }
        else if(netManager.mState == 2 && !isSceneUpdating &&  isStreamingData && isRecording){
            recordTask.stopRecord();
            Log.i("Net","Try to stop recording");
        }
        else{
            Toast.makeText(CameraTrackingActivity.this, "Cannot record now", Toast.LENGTH_LONG).show();
        }
    }


    public void requestSceneUpdate(View v){
        if(netManager.mState == 2 && !isSceneUpdating ) {
            setSceneUpdateStatus(2);
            new AskSceneUpdate(sceneUpdateHandler).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,netManager);
        }
        else{
            Toast.makeText(CameraTrackingActivity.this, "Cannot request scene update now", Toast.LENGTH_LONG).show();
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_tracking);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        // Set up tap listener.
        gestureHelper = new GestureHelper(/*context=*/ this);
        surfaceView.setOnTouchListener(gestureHelper);

        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);

        installRequested = false;

        // Networking
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        // UI Setup
        cameraModeButton = (ImageButton)findViewById(R.id.cameraModeButton);
        objectModeButton = (ImageButton)findViewById(R.id.objectModeButton);
        connectButton = (ImageButton)findViewById(R.id.connect);
        recordButton = (ImageButton)findViewById(R.id.recordButton);
        updateSceneButton = (ImageButton)findViewById(R.id.syncSceneButton);
        taskProgress = (ProgressBar)findViewById(R.id.taskProgress);
        currentRecordedFrame = (TextView)findViewById(R.id.currentFrame);
        ipDetector =  new BarcodeDetector.Builder(getApplicationContext())
                        .setBarcodeFormats(Barcode.DATA_MATRIX | Barcode.QR_CODE)
                        .build();
        scanMessage = (TextView)findViewById(R.id.scanMessage);

        isSceneUpdating = false;
        isSceneLoaded = false;
        isRecording = false;
        netManager = new NetworkManager(netHandler,wifiManager,this);


    }

    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ this);
                Config config = new Config(session);
                config.setFocusMode(Config.FocusMode.AUTO);
                session.configure(config);

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }


    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            backgroundRenderer.createOnGlThread(/*context=*/ this);
            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

            virtualObjectShadow.createOnGlThread(
                    /*context=*/ this, "models/andy_shadow.obj", "models/andy_shadow.png");
            virtualObjectShadow.setBlendMode(ObjectRenderer.BlendMode.Shadow);
            virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }


    public void handleBarcode(com.google.ar.core.Frame arFrame ){
            try {
                Image screen = null;

                screen = arFrame.acquireCameraImage();

                Frame frame = new Frame.Builder().setImageData(screen.getPlanes()[0].getBuffer(),screen.getWidth(),screen.getHeight(), ImageFormat.NV21).build();
                SparseArray<Barcode> barcodes = ipDetector.detect(frame);
                if(barcodes.size()>0){
                    Barcode thisCode = barcodes.valueAt(0);
//                    updateConnectButtonStatus(3);

                    String[] parts = thisCode.rawValue.split(":");
                    netManager.connect(thisCode.rawValue);
                }
                screen.close();

            } catch (NotYetAvailableException e) {
                e.printStackTrace();
            }
    }
    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (session == null) {
            return;
        }
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            com.google.ar.core.Frame frame = session.update();
            Camera camera = frame.getCamera();

            // Handle one tap per frame.
            handleTap(frame, camera);

            if(netManager != null && netManager.mState == Constants.STATE_IDLE){
                handleBarcode(frame);
            }

            // If frame is ready, render camera preview image to the GL surface.
            backgroundRenderer.draw(frame);

            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());

            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
            if (camera.getTrackingState() == TrackingState.PAUSED) {
                messageSnackbarHelper.showMessage(
                        this, TrackingStateHelper.getTrackingFailureReasonString(camera));
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            camera.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

            // Visualize tracked points.
            // Use try-with-resources to automatically release the point cloud.
            try (PointCloud pointCloud = frame.acquirePointCloud()) {
                pointCloudRenderer.update(pointCloud);
                pointCloudRenderer.draw(viewmtx, projmtx);
            }

            // No tracking error at this point. If we detected any plane, then hide the
            // message UI, otherwise show searchingPlane message.
            if (hasTrackingPlane()) {
                messageSnackbarHelper.hide(this);
            } else {
                messageSnackbarHelper.showMessage(this, SEARCHING_PLANE_MESSAGE);
            }

            // Visualize planes.
            planeRenderer.drawPlanes(
                    session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

            long time = SystemClock.uptimeMillis() % 10000L;
            float angleInDegrees = (360.0f / 10000.0f) * ((int) time);

            // Visualize anchors created by touch.
            float scaleFactor = gestureHelper.scaleFactor;
            float rotationFactor = gestureHelper.rotationFactor;

            for (ColoredAnchor coloredAnchor : anchors) {
                if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                    continue;
                }
                // Get the current pose of an Anchor in world space. The Anchor pose is updated
                // during calls to session.update() as ARCore refines its estimate of the world.
                coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

                // Update and draw the model and its shadow.
                virtualObject.updateModelMatrix(anchorMatrix, scaleFactor, rotationFactor);
                virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor, rotationFactor);
                virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
                virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
            }


        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(com.google.ar.core.Frame frame, Camera camera) {
        MotionEvent tap = gestureHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if (((trackable instanceof Plane)
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || ((trackable instanceof Point)
                        && (((Point) trackable).getOrientationMode()
                        == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL))) {
                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 20) {
                        anchors.get(0).anchor.detach();
                        anchors.remove(0);
                    }

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    float[] objColor;
                    if (trackable instanceof Point) {
                        objColor = new float[] {66.0f, 133.0f, 244.0f, 255.0f};
                    } else if (trackable instanceof Plane) {
                        objColor = new float[] {139.0f, 195.0f, 74.0f, 255.0f};
                    } else {
                        objColor = DEFAULT_COLOR;
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.clear();
                    anchors.add(new ColoredAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }

        }
    }


    /** Checks if we detected at least one plane. */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }
}
