package com.remote.blender;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
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

import java.io.IOException;

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
    private ModelRenderable andyRenderable;
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

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        // UI Setup
        setContentView(R.layout.activity_camera_tracking);
        arFragment = (CameraTrackingFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        cameraStreamButton = (ImageButton)findViewById(R.id.stream_camera);
        connectButton = (ImageButton)findViewById(R.id.connect);


        // Net setup
        netManager = new NetworkManager(netHandler);


        // ASSETS SETUP

        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        ModelRenderable.builder()
            .setSource(this, R.raw.andy)
            .build()
            .thenAccept(renderable -> andyRenderable = renderable)
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
                    if (andyRenderable == null) {
                        return;
                    }

                    if (sceneAnchor!=null){
                        sceneAnchor.detach();
                    }
                    // Create the Anchor.
                    sceneAnchor = hitResult.createAnchor();
                    sceneNode = new AnchorNode(sceneAnchor);
                    sceneNode.setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable andy and add it to the anchor.

                    sceneTransform = new TransformableNode(arFragment.getTransformationSystem());

                    sceneTransform.setParent(sceneNode);
                    sceneTransform.setRenderable(andyRenderable);
                    sceneTransform.select();


                });

    }


    @Override
    public void onUpdate(FrameTime frameTime) {
       ;

        if(sceneAnchor != null){
            float[] cameraMatrix = new float[16];
            Camera camera = arFragment.getArSceneView().getArFrame().getCamera();
            camera.getPose().toMatrix(cameraMatrix,0);

            Matrix cam = new Matrix(cameraMatrix);
            Matrix anchor =  sceneTransform.getWorldModelMatrix();
            Matrix out= new Matrix();

            Matrix.multiply(out,cam,anchor);
            Log.i("Net",out.toString());


            float trans[] =  sceneAnchor.getPose().transformPoint(camera.getPose().getTranslation());
//            Pose changed = new Pose(trans,camera.getPose().getRotationQuaternion());
            Vector3 transl = new Vector3();
            out.decomposeTranslation(transl);
            Quaternion rot = new Quaternion();
            out.extractQuaternion(rot);

            Pose changed = new Pose(transl.,rot);
            if (camera.getTrackingState() == TrackingState.TRACKING && send_position) {
                try {
                    netManager.send_data(Util.packPose(changed));

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

