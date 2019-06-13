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

public class AskCameraRecord extends AsyncTask<NetworkManager, Void, String> {
    private Handler callback;

    private Handler input = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg){
            ZMsg request = new ZMsg();
            request.add("RECORD");
            request.add("STOP");
            request.send(link);
            return true;
        }
    });
    private ZMQ.Socket link;
    private  boolean  is_recording;

    public AskCameraRecord(Handler callback) {
        this.callback = callback;
        input= this.input;
    }

    @Override
    protected void onCancelled() {
        is_recording = false;

        ZMsg request = new ZMsg();
        request.add("RECORD");
        request.add("STOP");
        request.send(link);

        is_recording = false;
        callback.sendMessage(callback.obtainMessage(2, "success"));
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


                // STEP 1: Ask to start the camera recording
                boolean is_recording = false;
                ZMsg request = new ZMsg();
                request.add("RECORD");
                request.add("START");
                request.send(link);

                Log.i("Net", "request record start");
                items.poll(5000);

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
                        return "Done";
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


                    items.poll(3000);
                    if (items.pollin(0)) {
                        ZMsg frame_record = ZMsg.recvMsg(link);

                        String record_statut = frame_record.getLast().toString();
                        Log.i("Net", "Recording: " + record_statut);

                        if(record_statut.contains("STOPPED")){
                            is_recording = false;
                            callback.sendMessage(callback.obtainMessage(2, "success"));
                        }
                    } else {
                        is_recording = false;
                        callback.sendMessage(callback.obtainMessage(1, "fail"));
                    }
                    frame_request.destroy();
                }
            }

            return "Done";
        }

}

