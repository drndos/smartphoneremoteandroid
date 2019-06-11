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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

            ZMsg scene_request = new ZMsg();
            scene_request.add("SCENE");

            scene_request.send(params[0].mNetSettings.dccChannel);
            Log.i("Net","send scene update request");

            items.poll(50000);
//            File f = new File();
            if (items.pollin(0)){
                ZMsg scene_request_response = ZMsg.recvMsg(params[0].mNetSettings.dccChannel);
                byte[] raw_data =  scene_request_response.getLast().getData();//params[0].mNetSettings.dccChannel.recv();
//                Log.i("Net","getting something !");
//                MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(raw_data);
//                Log.i("Net","unpacking");
//                try {
//                    scene = unpacker.unpackString();
//                    unpacker.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                Log.i("Net","Writing cache");
                File path =  params[0].app.getFilesDir();
                File file = new File(path,"scene_cache.glb");

                FileOutputStream stream;
                try {
                    stream = new FileOutputStream(file);
                    stream.write(raw_data);
                    stream.close();
                    file.setExecutable(true,false);
                    file.setReadable(true,false);
                    file.setWritable(true,false);

                    callback.sendMessage(callback.obtainMessage(0));
                    Log.i("Net","Done");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Log.i("Net","??");

//                try {
//                    BufferedWriter bwr = new BufferedWriter(new FileWriter(new File("scene_cache.gltf")));
//                    Log.i("Net","Writing cache");
//                    bwr.write(raw_data);
//                    bwr.close();



//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }
            else{
                Log.i("Net","Nothing");
                callback.sendMessage(callback.obtainMessage(1));
            }

//            InputStream stream = new ByteArrayInputStream(scene.getBytes(StandardCharsets.UTF_8));
            return "Done";
        }

}

