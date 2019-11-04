package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.msgpack.value.ArrayValue;
import org.msgpack.value.Value;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.core.MessagePack;

import java.io.IOException;

public class AskViewportStream extends AsyncTask<String, Void, String> {
    private Handler callback;
    private ZMQ.Socket link;
    private ZMQ.Context ctx;
    private ZMQ.Poller items;
    private  boolean  is_recording;

    public AskViewportStream(Handler cb, String address) {
        Log.i("Net","AskViewport: setup"+address);
        callback = cb;
        // STEP 0: Setup net wire
        ctx = ZMQ.context(1);
        link = ctx.socket(SocketType.DEALER);
        String identity = "AskViewport";
        link.setIdentity(identity.getBytes(ZMQ.CHARSET));
        link.setImmediate(true);
        link.connect(String.format("tcp://%s:%d",address,Constants.CLIENT_PORT+2));

        items = ctx.poller(1);
        items.register(link, ZMQ.Poller.POLLIN);

    }

    @Override
    protected void onCancelled() {
        Log.i("Net","AskScene:Cencelling");
    }

    protected void stopRecord(){
        is_recording = false;
    }

    @Override
    protected String doInBackground(String... params) {
        Log.i("Net","AskViewport: start");

        // STEP 1: Ask to start the camera recording
        is_recording = false;
        ZMsg request = new ZMsg();
        request.add("VSTREAM");
        request.send(link);

        Log.i("Net", "request stream start");
        items.poll(42000);

        // STEP 2 : Wait for the response from blender
        if (items.pollin(0)) {
            ZMsg raw_data = ZMsg.recvMsg(link);
            byte[] chunk = raw_data.getLast().getData();
            Log.i("Net", String.valueOf(chunk.length));
            raw_data.destroy();
//            if (header.contains("STARTED")) {
//                is_recording = true;
//                callback.sendMessage(callback.obtainMessage(0, "started"));
//            } else {
//                callback.sendMessage(callback.obtainMessage(1, raw_data));
//            }

        } else {
            Log.i("Net", "Fail to start camera record");
            callback.sendMessage(callback.obtainMessage(1));
        }



        //STEP 3: if the record as started, update the status
//        while (is_recording) {
//            ZMsg frame_request = new ZMsg();
//            frame_request.add("RECORD");
//            frame_request.add("STATE");
//            frame_request.send(link);
//
//
//            items.poll(1000);
//            if (items.pollin(0)) {
//                ZMsg frame_record = ZMsg.recvMsg(link);
//
//                String record_statut = frame_record.getLast().toString();
//                Log.i("Net", "Recording: " + record_statut);
//
//                if(record_statut.contains("STOPPED")){
//                    is_recording = false;
//                    callback.sendMessage(callback.obtainMessage(2, "fail"));
//                }
//                else{
//                    callback.sendMessage(callback.obtainMessage(2, record_statut));
//                }
//            } else {
//                is_recording = false;
//                callback.sendMessage(callback.obtainMessage(1, "fail"));
//            }
//            frame_request.destroy();
//        }

//        ZMsg stop_request = new ZMsg();
//        stop_request.add("RECORD");
//        stop_request.add("STOP");
//        stop_request.send(link);
//        items.poll(1000);
//        if (items.pollin(0)) {
//            ZMsg frame_record = ZMsg.recvMsg(link);
//            callback.sendMessage(callback.obtainMessage(1, "fail"));
//            Log.i("Net","AskScene: Done");
//        }

//        callback.sendMessage(callback.obtainMessage(2, "success"));
        link.close();
        ctx.close();

        return "Done";
    }

}

