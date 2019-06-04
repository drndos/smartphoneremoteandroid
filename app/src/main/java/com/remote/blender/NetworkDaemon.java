package com.remote.blender;

import android.os.Handler;

public class NetworkDaemon implements Runnable{
    private final Handler uiThreadHandler;
    private final NetworkSettings mNetSettings;

    public final int mState = 0;


    NetworkDaemon( Handler uiThreadHandler, NetworkSettings netSettings){
            this.uiThreadHandler = uiThreadHandler;
            this.mNetSettings = netSettings;
    }

    @Override
    public void run() {

    }



}
