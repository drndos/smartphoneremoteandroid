package com.remote.blender;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.example.blenderremote.R;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.math.Matrix;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.ux.TransformableNode;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

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
    private File sceneChache;
    private boolean isStreamingCamera;

    // UI vars
    private CameraTrackingFragment arFragment;
    private ImageButton cameraStreamButton;
    private ImageButton connectButton;
    private ImageButton recordButton;
    private ImageButton updateSceneButton;
    private ModelRenderable originRenderable;


    // Net vars
    private NetworkManager netManager;
    private Handler netHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                // STATE MESSAGE
                case 0:
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
                    updateConnectButtonStatus((int)msg.obj);

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

    private void setSceneUpdateStatus(int status){
        switch (status){
            // STATE MESSAGE
            case 0:
                int length = (int) sceneChache.length();
                Log.i("Net",String.valueOf(length));

                loadScene(sceneChache);
                updateSceneButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
                Log.i("Net","Received scene !");
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
    private Handler recordingUpdateHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                // RECORDING
                case 0:
                    recordButton.setImageResource(R.drawable.round_stop_white_18dp);
                    recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorError));
                    break;
                case 1:
                    recordButton.setImageResource(R.drawable.round_play_arrow_white_18dp);
                    recordButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));

            }


            return false;
        }
    });


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
                .thenAccept(renderable -> originRenderable = renderable)
                .exceptionally(
                        throwable -> {
                            Log.i("Net","LOAD MODEL ERROR");
                            return null;
                        });

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
            isStreamingCamera = false;
            cameraStreamButton.setImageResource(R.drawable.round_videocam_off_white_18dp);
            cameraStreamButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
            recordButton.setVisibility(View.GONE);
        }
        else if(netManager.mState == 2){
            isStreamingCamera = true;
            cameraStreamButton.setImageResource(R.drawable.round_videocam_white_18dp);
            cameraStreamButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorOnline));
            recordButton.setVisibility(View.VISIBLE);
        }
        else{
            Toast.makeText(CameraTrackingActivity.this,
                    "Cannot stream the camera !", Toast.LENGTH_LONG).show();
        }
    }

    public void updateConnectButtonStatus(int status){
        switch (status){
            case 0:
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
        }
    }

    public void onClickButtoncameraStreamButton(View v)
    {
        if(isStreamingCamera){
            setcameraStream(false);
        }
        else{
            setcameraStream(true);
        }
    }

    public void requestRecordCamera(View v){
        if(netManager.mState == 2 && !isSceneUpdating &&  isStreamingCamera) {
            new AskCameraRecord(recordingUpdateHandler).execute(netManager);
        }
        else{

        }
    }

    public void requestSceneUpdate(View v){
        if(netManager.mState == 2 && !isSceneUpdating ) {

            new AskSceneUpdate(sceneUpdateHandler).execute(netManager);

            setSceneUpdateStatus(2);
        }
        else{
            Toast.makeText(CameraTrackingActivity.this, "Cannot request scene update now", Toast.LENGTH_LONG).show();
        }
    }
    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        // UI Setup
        setContentView(R.layout.activity_camera_tracking);
        arFragment = (CameraTrackingFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        cameraStreamButton = (ImageButton)findViewById(R.id.stream_camera);
        connectButton = (ImageButton)findViewById(R.id.connect);
        recordButton = (ImageButton)findViewById(R.id.recordButton);
        updateSceneButton = (ImageButton)findViewById(R.id.syncSceneButton);

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

                    // Create the transformable andy and add it to the anchor.

                    sceneTransform = new TransformableNode(arFragment.getTransformationSystem());
                    sceneTransform.getScaleController().setMaxScale(10);
                    sceneTransform.getScaleController().setMinScale(1);
                    sceneTransform.setParent(sceneNode);
                    sceneTransform.getRotationController().setRotationRateDegrees(0);
                    sceneTransform.setRenderable(originRenderable);
                    sceneTransform.select();
                    sceneTransform.setWorldRotation(Quaternion.eulerAngles(new Vector3(0,180,0)));

                });

    }


    @Override
    public void onUpdate(FrameTime frameTime) {
        if(sceneAnchor != null){
            Camera camera = arFragment.getArSceneView().getArFrame().getCamera();

            if (camera.getTrackingState() == TrackingState.TRACKING && isStreamingCamera) {
                try {
                    ZMsg message_buffer = new ZMsg();

                    Util.packCamera(message_buffer,camera);
                    Util.packTransformableNode(message_buffer, sceneTransform);

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

