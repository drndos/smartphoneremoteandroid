package com.remote.blender;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
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
import com.google.ar.sceneform.math.Matrix;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.IOException;
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

    // UI vars
    private CameraTrackingFragment arFragment;
    private ImageButton cameraStreamButton;
    private ImageButton connectButton;
    private ModelRenderable originRenderable;
    private LinearLayout connexionPannel;

    // Net vars
    private boolean send_position;
    private ZMQ.Context ctx;
    private ZMQ.Socket push_socket;
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
            switch (msg.what){
                // STATE MESSAGE
                case 0:
                    Log.i("Net","Received scene !");
                    isSceneUpdating = false;
                default:
                    Toast.makeText(CameraTrackingActivity.this, "ERROR", Toast.LENGTH_LONG).show();
                    break;
            }


                return false;
            }
    });
    private  boolean isSceneUpdating = false;

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
            send_position = false;
            cameraStreamButton.setImageResource(R.drawable.round_videocam_off_white_18dp);
            cameraStreamButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorPrimary));
        }
        else if(netManager.mState == 2){
            send_position = true;
            cameraStreamButton.setImageResource(R.drawable.round_videocam_white_18dp);
            cameraStreamButton.setBackgroundTintList(getResources().getColorStateList(R.color.colorOnline));
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
        if(send_position){
            setcameraStream(false);
        }
        else{
            setcameraStream(true);
        }
    }

    public void requestSceneUpdate(View v){
        if(netManager.mState == 2 && !isSceneUpdating ) {
            new AskSceneUpdate(sceneUpdateHandler).execute(netManager);
            Toast.makeText(CameraTrackingActivity.this, "request scene update launched", Toast.LENGTH_LONG).show();
            isSceneUpdating = true;
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
        String ip = "none";

        try {
            Enumeration networkInterfaces = NetworkInterface.getNetworkInterfaces();  // gets All networkInterfaces of your device
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface inet = (NetworkInterface) networkInterfaces.nextElement();
                Enumeration address = inet.getInetAddresses();
                while (address.hasMoreElements()) {
                    InetAddress inetAddress = (InetAddress) address.nextElement();
                    if (inetAddress.isSiteLocalAddress()) {
                        System.out.println("Your ip: " + inetAddress.getHostAddress());  /// gives ip address of your device
                    }
                }
            }
        } catch (Exception e) {
            // Handle Exception
        }
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


        // Net setup
        netManager = new NetworkManager(netHandler,wifiManager);


        // ASSETS SETUP

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

        // AR EVENTS
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
                    sceneTransform.setWorldRotation(new Quaternion(0,0,0,1));

                });

    }


    @Override
    public void onUpdate(FrameTime frameTime) {
       ;

        if(sceneAnchor != null){
            float[] cameraMatrix = new float[16];
            Camera camera = arFragment.getArSceneView().getArFrame().getCamera();
              Log.i("Net", Float.toString(sceneTransform.getWorldRotation().y));
//            camera.getPose().toMatrix(cameraMatrix,0);

//            Matrix cam = new Matrix(cameraMatrix);
//            Matrix anchor =  sceneTransform.getWorldModelMatrix();
//            Matrix out= new Matrix();
//
//            Matrix.multiply(out,cam,anchor);

//            float came_trans[] =  camera.getPose().getTranslation();
//            Vector3 camera_translation = new Vector3(came_trans[0],came_trans[1],came_trans[2]) ;
//            Vector3 result = Vector3.cross( camera_translation, sceneTransform.getWorldPosition());
//            double scale =   (sceneTransform.getWorldScale().x * 100) / 1.7;
//            result.scaled((float)scale);
//            Log.i("Net",Float.toString(sceneTransform.getWorldScale().x));
//            float t[] = {result.x,result.y,result.z};
//            Pose output = new Pose(t,camera.getPose().getRotationQuaternion());

//            Vector3 transl = new Vector3();
//            out.decomposeTranslation(transl);
//            Quaternion rot = new Quaternion();
//            out.extractQuaternion(rot);

//            Pose changed = new Pose(transl,rot);
            if (camera.getTrackingState() == TrackingState.TRACKING && send_position) {
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

