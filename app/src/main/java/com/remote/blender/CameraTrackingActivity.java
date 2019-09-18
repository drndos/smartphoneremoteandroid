package com.remote.blender;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.Image;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import com.example.blenderremote.R;
import com.google.ar.core.Anchor;
import com.google.ar.core.Session;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.TransformableNode;
import org.zeromq.ZMsg;

import java.io.File;
import java.io.IOException;


public class CameraTrackingActivity extends AppCompatActivity
        implements Scene.OnUpdateListener {
    private static final String TAG = CameraTrackingActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    //AR vars
    private Anchor sceneAnchor;
    private AnchorNode sceneNode;
    private TransformableNode sceneTransform;
    private boolean isSceneLoaded;
    private boolean isSceneUpdating;
    private boolean isRecording;
    private File sceneChache;
    private boolean isStreamingData;
    private int interactionMode = Constants.CAMERA_MODE;
    private AskCameraRecord recordTask;
    private AsyncTask cameraRecordTask;

    // UI vars
    private CameraTrackingFragment arFragment;
    private ImageButton cameraModeButton;
    private ImageButton objectModeButton;
    private ImageButton connectButton;
    private ImageButton recordButton;
    private ImageButton updateSceneButton;
    private ModelRenderable originRenderable;
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
            switch (msg.what){
                // STATE MESSAGE
                case 0:
                    if(!isSceneUpdating) {
                        switch ((int)msg.obj){
                            case 0:
                                setcameraStream(false);

                                break;
                            case 1:
                                setcameraStream(false);
                                break;
                            case 2:
                                if (!isSceneLoaded && !isSceneUpdating){
                                    isSceneLoaded = true;
                                    requestSceneUpdate(requireViewById(R.id.connect));
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
                int length = (int) sceneChache.length();
                Log.i("Net",String.valueOf(length));
                loadScene(sceneChache);
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
        ModelRenderable.builder()
                .setSource(this, RenderableSource.builder().setSource(
                        this,
                        Uri.fromFile(file),
                        RenderableSource.SourceType.GLB)
                        .setRecenterMode(RenderableSource.RecenterMode.NONE)
                        .build())
                .setRegistryId(file)
                .build()
                .thenAccept(
                        (renderable) -> {
                            originRenderable = renderable;
                            if (sceneTransform != null){
                                sceneTransform.setRenderable(originRenderable);
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Log.i("Net","LOAD MODEL ERROR");
                            return null;
                        });

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
                        netManager.disconnect();

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
            recordTask = new AskCameraRecord(recordingUpdateHandler, netManager.mAddress);
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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);



        // General setup
        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }


        // UI Setup
        setContentView(R.layout.activity_camera_tracking);
        arFragment = (CameraTrackingFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
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


        // Net setup
        netManager = new NetworkManager(netHandler,wifiManager,this);


        // ASSETS SETUP
        File path =  netManager.app.getFilesDir();
        sceneChache = new File(path,"scene_cache.glb");

        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        ModelRenderable.builder()
            .setSource(this, R.raw.gizmo)
            .build()
            .thenAccept(renderable -> originRenderable = renderable)
            .exceptionally(
                    throwable -> {
                        Toast toast =
                                Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.CENTER, 0, 0);
                        toast.show();
                        return null;
                    });

        // AR
        isSceneUpdating = false;
        isSceneLoaded = false;
        isRecording = false;


        arFragment.getArSceneView().getScene().addOnUpdateListener(this);
        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (originRenderable == null) {
                        return;
                    }

                    if (sceneNode!=null && sceneTransform!=null){
                        sceneNode.removeChild(sceneTransform);

                    }
                    if (sceneAnchor!=null){
                        sceneAnchor.detach();
                    }
                    // Create the Anchor.
                    sceneAnchor = hitResult.createAnchor();

                    hitResult.getHitPose().getTranslation();
                    sceneNode = new AnchorNode(sceneAnchor);
                    sceneNode.setWorldRotation(Quaternion.eulerAngles(new Vector3(0,0,0)));

                    sceneNode.setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable and add it to the anchor.
                    sceneTransform = new TransformableNode(arFragment.getTransformationSystem());
                    sceneTransform.getScaleController().setMaxScale(10);
                    sceneTransform.getScaleController().setMinScale((float)0.1);
                    sceneTransform.setParent(sceneNode);
                    sceneTransform.getRotationController().setRotationRateDegrees(0);
                    sceneTransform.setRenderable(originRenderable);
                    originRenderable.setShadowCaster(false);
                    sceneTransform.select();
                    sceneTransform.setWorldRotation(Quaternion.eulerAngles(new Vector3(0,180,0)));

                });

    }


    @RequiresApi(api = Build.VERSION_CODES.P)
    @Override
    public void onUpdate(FrameTime frameTime) {

        // QRCode detection
        //TODO: Export the code
        if(netManager.mState == Constants.STATE_IDLE){
            try {

                Image screen = null;
                screen = arFragment.getArSceneView().getArFrame().acquireCameraImage();

                Frame frame = new Frame.Builder().setImageData(screen.getPlanes()[0].getBuffer(),screen.getWidth(),screen.getHeight(), ImageFormat.NV21).build();
                SparseArray<Barcode> barcodes = ipDetector.detect(frame);
                if(barcodes.size()>0){
                    Barcode thisCode = barcodes.valueAt(0);
                    Log.i("Net",thisCode.rawValue);
                    updateConnectButtonStatus(3);
                    netManager.connect(thisCode.rawValue);
                }
                screen.close();

            } catch (NotYetAvailableException e) {
                e.printStackTrace();
            }
        }

        if(sceneAnchor != null){

            Camera camera = arFragment.getArSceneView().getArFrame().getCamera();

            if (camera.getTrackingState() == TrackingState.TRACKING && isStreamingData) {
                ZMsg message_buffer = new ZMsg();

                // Compose data stream information
                try {
                    Util.packArState(message_buffer,interactionMode);

                    Util.packTransformableNode(message_buffer, sceneTransform);

                    switch (interactionMode){
                        case Constants.CAMERA_MODE:
                            break;
                        case Constants.OBJECT_MODE:
                            break;
                    }

                    Util.packCamera(message_buffer,camera);


                    netManager.send_data(message_buffer);

                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

    }

    /**
     * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }
}

