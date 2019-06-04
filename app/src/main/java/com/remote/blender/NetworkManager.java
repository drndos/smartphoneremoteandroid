package com.remote.blender;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class NetworkManager {
    private final int STATE_OFFLINE = 0;
    private final int STATE_ONLINE = 1;

    public int mState = STATE_OFFLINE;
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
                    mState = STATE_ONLINE;

                }
                else{
                    mNetSettings.close();
                    mState = STATE_OFFLINE;
                }
                netHandler.sendMessage(netHandler.obtainMessage(0,mState));
                stateHandler.postDelayed(stateRunnable, 4000);

            }
        };

        stateHandler.postDelayed(stateRunnable, 4000);

    }

    public boolean send_data(ZMsg data){
        if(mState == STATE_ONLINE){
            data.send(mNetSettings.arChannel);

            return true;
        }
        return false;
    }
}
