package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class AskCameraRecord extends AsyncTask<String, Void, String> {
    private Handler callback;
    private ZMQ.Socket link;
    private ZMQ.Context ctx;
    private ZMQ.Poller items;
    private  boolean  is_recording;

    public AskCameraRecord(Handler cb, String address) {
        Log.i("Net","AskCamera: setup");
        callback = cb;
        // STEP 0: Setup net wire
        ctx = ZMQ.context(1);
        link = ctx.socket(SocketType.DEALER);
        String identity = "AskScene";
        link.setIdentity(identity.getBytes(ZMQ.CHARSET));
        link.setImmediate(true);
        link.connect(String.format("tcp://%s:%d",address,5559));

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
        Log.i("Net","AskCamera: setup");

        // STEP 1: Ask to start the camera recording
        is_recording = false;
        ZMsg request = new ZMsg();
        request.add("RECORD");
        request.add("START");
        request.send(link);

        Log.i("Net", "request record start");
        items.poll(2000);

        // STEP 2 : Wait for the response from blender
        if (items.pollin(0)) {
            ZMsg raw_data = ZMsg.recvMsg(link);

            String header = raw_data.getLast().toString();
            Log.i("Net", header);
            raw_data.destroy();
            if (header.contains("STARTED")) {
                is_recording = true;
                callback.sendMessage(callback.obtainMessage(0, "started"));
            } else {
                callback.sendMessage(callback.obtainMessage(1, raw_data));
            }

        } else {
            Log.i("Net", "Fail to start camera record");
            callback.sendMessage(callback.obtainMessage(1));
        }



        //STEP 3: if the record as started, update the status
        while (is_recording) {
            ZMsg frame_request = new ZMsg();
            frame_request.add("RECORD");
            frame_request.add("STATE");
            frame_request.send(link);


            items.poll(1000);
            if (items.pollin(0)) {
                ZMsg frame_record = ZMsg.recvMsg(link);

                String record_statut = frame_record.getLast().toString();
                Log.i("Net", "Recording: " + record_statut);

                if(record_statut.contains("STOPPED")){
                    is_recording = false;
                    callback.sendMessage(callback.obtainMessage(2, "fail"));
                }
                else{
                    callback.sendMessage(callback.obtainMessage(2, record_statut));
                }
            } else {
                is_recording = false;
                callback.sendMessage(callback.obtainMessage(1, "fail"));
            }
            frame_request.destroy();
        }

        ZMsg stop_request = new ZMsg();
        stop_request.add("RECORD");
        stop_request.add("STOP");
        stop_request.send(link);
        items.poll(1000);
        if (items.pollin(0)) {
            ZMsg frame_record = ZMsg.recvMsg(link);
            callback.sendMessage(callback.obtainMessage(1, "fail"));
            Log.i("Net","AskScene: Done");
        }

//        callback.sendMessage(callback.obtainMessage(2, "success"));
        link.close();
        ctx.close();

        return "Done";
    }

}

