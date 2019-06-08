package com.remote.blender;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import org.zeromq.SocketType;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class NetworkManager {
    private final int STATE_IDLE = 0;
    private final int STATE_OFFLINE = 1;
    private final int STATE_ONLINE = 2;

    public int mState = STATE_IDLE;
    private String mAddress;

    private Handler netHandler;
    private NetworkDaemon mNetDaemon;
    public NetworkSettings mNetSettings;
    public Handler stateHandler = null;
    public static Runnable stateRunnable = null;
    public static Runnable dccRunnable = null;
    public Future longRunningTaskFuture;
    public ExecutorService executorService = Executors.newSingleThreadExecutor();
    private WifiManager wifi;
    private String localeAddr;
    public Context app;

    NetworkManager(Handler handler, WifiManager w, Context app){
        wifi =w;
        netHandler = handler;
        localeAddr = getLocalAddr();
        this.app = app;

    }
    private String getLocalAddr(){
        int ipAddress = wifi.getConnectionInfo().getIpAddress();
        return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
    }

    public void connect(String address){
        Log.i("Net",address);
        mState = STATE_OFFLINE;
        mAddress = address;
        mNetSettings = new NetworkSettings(address);
        stateHandler = new Handler();

        stateRunnable = new Runnable() {
            public void run() {
                if (mState == STATE_IDLE){
                    return;
                }
                if (mState == STATE_OFFLINE){
                    mNetSettings.connect(mAddress);
                }

                    ZMQ.Poller items = mNetSettings.ctx.poller(1);
                    items.register(mNetSettings.stateChannel, ZMQ.Poller.POLLIN);

                mNetSettings.stateChannel.send(localeAddr);
                items.poll(mNetSettings.stateTimout);
                if(items.pollin(0)) {
                    ZMsg  msg = ZMsg.recvMsg(mNetSettings.stateChannel);
                    ZFrame header = msg.pop();
                    switch (header.getString(ZMQ.CHARSET)){
                        case "PING":
                            break;
                        case "SCENE":
                            break;
                        default:
                            break;
                    }
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

    public void disconnect(){
        mState = STATE_IDLE;
        netHandler.sendMessage(netHandler.obtainMessage(0,mState));
        mNetSettings.close();
    }

    public boolean send_data(ZMsg data){
        if(mState == STATE_ONLINE){
            data.send(mNetSettings.arChannel);

            return true;
        }
        return false;
    }
}
