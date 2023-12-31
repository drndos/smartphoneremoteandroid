package com.remote.ar;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import org.zeromq.ZMsg;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class NetworkManager {
    private final int STATE_IDLE = 0;
    private final int STATE_OFFLINE = 1;
    private final int STATE_ONLINE = 2;

    public int mState = STATE_IDLE;
    public String mAddress;
    private Handler ttlHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what){
                case 0:
                    mState = STATE_ONLINE;
                    mNetSettings.connect(mAddress, mPort);
                    break;
                case 1:
                    mState = STATE_OFFLINE;
                    break;
                case 2:
                    ttlTask.cancel(true);
                    mState = STATE_IDLE;
                    break;
            }
            netHandler.sendMessage(netHandler.obtainMessage(0,mState));

            return false;
        }
    });
    private AsyncStateUpdate ttl;
    private AsyncTask ttlTask;

    private Handler netHandler;
    public NetworkSettings mNetSettings;
    public Handler stateHandler = null;
    public static Runnable stateRunnable = null;
    public static Runnable dccRunnable = null;
    public Future longRunningTaskFuture;
    public ExecutorService executorService = Executors.newSingleThreadExecutor();
    private WifiManager wifi;
    private String localeAddr;
    public Integer mPort;
    public Context app;

    NetworkManager(Handler handler, WifiManager w, Context app){
        wifi =w;
        mPort= 61520;
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

        mState = STATE_OFFLINE;

        String[] parts = address.split(":");
        mPort = Integer.valueOf(parts[1]);
        mAddress = parts[0];
        Log.i("Net","connect "+mAddress +":"+ mPort);
        mNetSettings = new NetworkSettings(mAddress, mPort);
        stateHandler = new Handler(Looper.getMainLooper());

        ttl = new AsyncStateUpdate(ttlHandler,mAddress, mPort);
        ttlTask = ttl.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[]{"go"});
    }

    public void disconnect(){
        ttl.stop();

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
