package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class AskSceneUpdate  extends AsyncTask<NetworkManager, Void, String> {
        private Handler callback;
        private ZMQ.Socket link;

        public AskSceneUpdate(Handler callback) {
            this.callback = callback;
            Log.i("Net","AskScene task setup");
        }

        private boolean writeScene(File file,byte[] sceneData){
            FileOutputStream stream;
            try {
                stream = new FileOutputStream(file);
                stream.write(sceneData);
                stream.close();
                file.setExecutable(true,false);
                file.setReadable(true,false);
                file.setWritable(true,false);

                callback.sendMessage(callback.obtainMessage(0));
                Log.i("Net","Done");

                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected String doInBackground(NetworkManager... params) {
            try(ZMQ.Context ctx = ZMQ.context(1)) {

                // STEP 0: Setup net wire
                Log.i("Net","AskScene socket");
                link = ctx.socket(SocketType.DEALER);
                String identity = "AskScene";
                link.setIdentity(identity.getBytes(ZMQ.CHARSET));
                link.connect(String.format("tcp://%s:%d",params[0].mAddress,5559));
                link.setImmediate(true);
                ZMQ.Poller items = ctx.poller(1);
                items.register(link, ZMQ.Poller.POLLIN);

                // STEP 1: Send scene request
                Log.i("Net","AskScene: request send");
                ZMsg scene_request = new ZMsg();
                scene_request.add("SCENE");
                scene_request.send(link);

                Log.i("Net", "send scene update request");


                items.poll(10000);
                if (items.pollin(0)) {

                    // Read raw data
                    Log.i("Net", "recv something");
                    ZMsg answer =  ZMsg.recvMsg(link);
                    byte[] raw_data = answer.getLast().getData();

                    // Generating file dir
                    File path = params[0].app.getFilesDir();
                    File file = new File(path, "scene_cache.glb");

                    if (writeScene(file, raw_data)) {
                        callback.sendMessage(callback.obtainMessage(0));
                    }

                } else {
                    Log.i("Net", "Nothing");

                    callback.sendMessage(callback.obtainMessage(0));
                }

                link.close();
                ctx.close();
            }

            callback.sendMessage(callback.obtainMessage(1));
            return "Done";
        }

}

