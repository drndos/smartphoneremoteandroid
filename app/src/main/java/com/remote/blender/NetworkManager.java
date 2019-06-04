package com.remote.blender;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class NetworkManager {

    public int mState = 0;
    private String mAddress;

    private Handler netHandler;
    private NetworkDaemon mNetDaemon;
    public NetworkSettings mNetSettings;
    public Handler stateHandler = null;
    public static Runnable stateRunnable = null;

    NetworkManager(Handler handler){
        netHandler = handler;
    }

    public void connect(String address){
        mAddress = address;
        mNetSettings = new NetworkSettings(address);
        stateHandler = new Handler();
        stateRunnable = new Runnable() {
            public void run() {
                if (mState == 1){
                    Log.i("Net","Onpen new connexion");
                    mNetSettings.connect(mAddress);

                }
                Log.i("Net","Done");
                ZMQ.Poller items = mNetSettings.ctx.poller(1);
                items.register(mNetSettings.stateChannel, ZMQ.Poller.POLLIN);

                mNetSettings.stateChannel.send("ping");
                items.poll(mNetSettings.stateTimout);
                if(items.pollin(0)) {
                    byte[] msg = mNetSettings.stateChannel.recv();
                    mState = 0;

                }
                else{
                    mNetSettings.close();
                    mState = 1;
                }
                netHandler.sendMessage( netHandler.obtainMessage(0,mState));
                stateHandler.postDelayed(stateRunnable, 4000);

            }
        };

        stateHandler.postDelayed(stateRunnable, 4000);






//
//        ZMQ.Poller items = mNetSettings.ctx.poller(1);
//        items.register(ping, ZMQ.Poller.POLLIN);

//        ping.send("ping");
//        items.poll(3000);
//        if(items.pollin(0)) {
//            byte[] msg = ping.recv();
//            String message = editText.getText().toString();
//            intent.putExtra(EXTRA_MESSAGE, message);
//            startActivity(intent);
//        }
    }

    public void send(byte[] item){

    }
}
