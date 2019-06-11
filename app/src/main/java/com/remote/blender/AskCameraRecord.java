package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class AskCameraRecord extends AsyncTask<NetworkManager, Void, String> {
        private Handler callback;
        public AskCameraRecord(Handler callback) {
            this.callback = callback;
        }

        @Override
        protected String doInBackground(NetworkManager... params) {
            String scene = "NONE";
            ZMQ.Poller items = params[0].mNetSettings.ctx.poller(1);
            items.register(params[0].mNetSettings.dccChannel, ZMQ.Poller.POLLIN);
            boolean is_recording = false;

            ZMsg request = new ZMsg();
            request.add("RECORD");
            request.add("START");
            request.send(params[0].mNetSettings.dccChannel);

            Log.i("Net","request record start");


            items.poll(5000);

            if (items.pollin(0)){
                ZMsg raw_data =  ZMsg.recvMsg(params[0].mNetSettings.dccChannel);

                String header =  raw_data.pop().toString();
                Log.i("Net",header);

                if (header.contains("RECORD")){
                    is_recording = true;
                }
                else{
                    callback.sendMessage(callback.obtainMessage(1,raw_data));
                    return "Done";
                }

            }
            else{
                Log.i("Net","Fail to start camera record");
                callback.sendMessage(callback.obtainMessage(1));
            }

            callback.sendMessage(callback.obtainMessage(0,"started"));
            is_recording = true;
            while (is_recording){
                ZMsg frame_request = new ZMsg();
                frame_request.add("RECORD");
                frame_request.add("STATE");
                frame_request.send(params[0].mNetSettings.dccChannel);

                items.poll(2000);
                if (items.pollin(0)) {
                    ZMsg frame_record = ZMsg.recvMsg(params[0].mNetSettings.dccChannel);

                    String record_statut =  frame_record.getLast().toString();
                    Log.i("Net", "Recording: " + record_statut);

                }
                else{
                    is_recording = false;
                    callback.sendMessage(callback.obtainMessage(1,"fail"));
                }
            }

            return "Done";
        }

}

