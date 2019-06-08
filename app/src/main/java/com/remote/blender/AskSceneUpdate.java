package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;


import android.os.AsyncTask;
import android.os.Handler;

import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class AskSceneUpdate  extends AsyncTask<NetworkManager, Void, String> {
        private Handler callback;
        public AskSceneUpdate(Handler callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(NetworkManager... params) {
            String scene = "NONE";
            ZMQ.Poller items = params[0].mNetSettings.ctx.poller(1);
            items.register(params[0].mNetSettings.dccChannel, ZMQ.Poller.POLLIN);

            params[0].mNetSettings.dccChannel.send("SCENE");

            Log.i("Net","pushing data");

            items.poll(100);

            if (items.pollin(0)){
                byte[] raw_data =  params[0].mNetSettings.dccChannel.recv();
                Log.i("Net","getting something !");
                scene = String.valueOf(raw_data);

            }
            else{
                Log.i("Net","Nothing");
            }

            callback.sendMessage(callback.obtainMessage(0,scene));
            return "Done";
        }

}

