package com.remote.blender;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;

import com.example.blenderremote.R;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_MESSAGE = "com.example.blenderremote.MESSAGE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /** Called when the user taps the Send button */
    public void sendMessage(View view){
        Intent intent = new Intent(this, CameraTrackingActivity.class);
        EditText editText = (EditText) findViewById(R.id.editText);

        String server_address = String.format("tcp://%s:%d",editText.getText().toString(),5557);
        ZMQ.Context ctx = ZMQ.context(1);
        ZMQ.Socket ping = ctx.socket(SocketType.REQ);
        Log.i("Net",server_address);
        ping.connect(server_address);

        ZMQ.Poller items = ctx.poller(1);
        items.register(ping, ZMQ.Poller.POLLIN);

        ping.send("ping");
        items.poll(3000);
        if(items.pollin(0)) {
            byte[] msg = ping.recv();
            String message = editText.getText().toString();
            intent.putExtra(EXTRA_MESSAGE, message);
            startActivity(intent);
        }
        Log.i("Net","No response to the ping request");

    }
}
