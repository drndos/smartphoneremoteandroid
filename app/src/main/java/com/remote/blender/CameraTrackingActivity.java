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
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
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

    // UI vars
    private CameraTrackingFragment arFragment;
    private ImageButton cameraStream;
    private ModelRenderable andyRenderable;
    private LinearLayout connexionPannel;

    // Net vars
    private boolean send_position;
    private ZMQ.Context ctx;
    private ZMQ.Socket push_socket;
    private NetworkManager netManager;


    public void showConnexionDialog(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setTitle("Connexion");
        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        alertDialogBuilder.setView(input);

        alertDialogBuilder.setMessage("Enter server address");
        alertDialogBuilder.setPositiveButton("yes",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        Toast.makeText(CameraTrackingActivity.this,"You clicked yesbutton",Toast.LENGTH_LONG).show();
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
        if(netManager != null){
            showConnexionDialog();
        }
        else{

        }
    }
    public void onClickButtonCameraStream(View v)
    {
        if(send_position){
            send_position = false;
            cameraStream.setImageResource(R.drawable.round_videocam_off_white_18dp);
            cameraStream.setBackgroundTintList(v.getResources().getColorStateList(R.color.colorPrimary));

        }
        else{
            send_position = true;
            cameraStream.setImageResource(R.drawable.round_videocam_white_18dp);
            cameraStream.setBackgroundTintList(v.getResources().getColorStateList(R.color.colorOnline));
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
        // Get the previous activity message
        String server_ip = "192.168.0.10";

        setContentView(R.layout.activity_camera_tracking);
        arFragment = (CameraTrackingFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        cameraStream = (ImageButton)findViewById(R.id.stream_camera);
        connexionPannel = findViewById(R.id.connexion_pannel);


        ctx = ZMQ.context(1);
        push_socket = ctx.socket(SocketType.PUSH);

        push_socket.connect(String.format("tcp://%s:%d",server_ip,5558));
        //Start to ping the server
//        handler.postDelayed(runnable,2000);



        //3d scene setup

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

        arFragment.getArSceneView().getScene().addOnUpdateListener(this);

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if (andyRenderable == null) {
                        return;
                    }

                    // Create the Anchor.
                    Anchor anchor = hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    anchorNode.setParent(arFragment.getArSceneView().getScene());

                    // Create the transformable andy and add it to the anchor.
                    TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
                    andy.setParent(anchorNode);
                    andy.setRenderable(andyRenderable);
                    andy.select();
                });

    }


    @Override
    public void onUpdate(FrameTime frameTime) {
        Camera camera = arFragment.getArSceneView().getArFrame().getCamera();
        if (camera.getTrackingState() == TrackingState.TRACKING && send_position) {
                try {
                    //                new SendMessageTask(clientMessageHandler).execute(Util.packCamera(frame.getCamera()));

                    Util.packCamera(camera).send(push_socket);

                } catch (IOException e) {
                    e.printStackTrace();
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

//public class MainActivity extends AppCompatActivity {
//    public static final String EXTRA_MESSAGE = "com.example.blenderremote.MESSAGE";
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//    }
//
//    /** Called when the user taps the Send button */
//    public void sendMessage(View view){
//        Intent intent = new Intent(this, CameraTrackingActivity.class);
//        EditText editText = (EditText) findViewById(R.id.editText);
//
//        String server_address = String.format("tcp://%s:%d",editText.getText().toString(),5557);
//        ZMQ.Context ctx = ZMQ.context(1);
//        ZMQ.Socket ping = ctx.socket(SocketType.REQ);
//        Log.i("Net",server_address);
//        ping.connect(server_address);
//
//        ZMQ.Poller items = ctx.poller(1);
//        items.register(ping, ZMQ.Poller.POLLIN);
//
//        ping.send("ping");
//        items.poll(3000);
//        if(items.pollin(0)) {
//            byte[] msg = ping.recv();
//            String message = editText.getText().toString();
//            intent.putExtra(EXTRA_MESSAGE, message);
//            startActivity(intent);
//        }
//        Log.i("Net","No response to the ping request");
//
//    }
//}
