package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
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

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

            items.poll(2000);
//            File f = new File();
            if (items.pollin(0)){
                String raw_data =  params[0].mNetSettings.dccChannel.recvStr();
//                Log.i("Net","getting something !");
//                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(raw_data);
//                Log.i("Net","unpacking");
//                try {
//                    scene = unpacker.unpackString();
                    Log.i("Net",raw_data);
                    scene = raw_data;
//                    unpacker.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                try {
                    BufferedWriter bwr = new BufferedWriter(new FileWriter(new File("scene_cache.gltf")));
                    bwr.write(raw_data);
                    bwr.close();
                    callback.sendMessage(callback.obtainMessage(0));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            else{
                Log.i("Net","Nothing");
                callback.sendMessage(callback.obtainMessage(1));
            }
//            InputStream stream = new ByteArrayInputStream(scene.getBytes(StandardCharsets.UTF_8));
            return "Done";
        }

}

