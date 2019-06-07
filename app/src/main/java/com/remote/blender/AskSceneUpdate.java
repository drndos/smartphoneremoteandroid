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

public class AskSceneUpdate  extends AsyncTask<ZMQ.Socket, Void, String> {
        public AskSceneUpdate() {
        }

        @Override
        protected String doInBackground(ZMQ.Socket... params) {

            params[0].send("SCENE");
            Log.i("Net","pushing data");

            return "Done";
        }

}

