package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.zeromq.ZMQ;

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

            params[0].mNetSettings.dccChannel.send("RECORD");

            Log.i("Net","pushing data");

            items.poll(50000);

            if (items.pollin(0)){
                String raw_data =  params[0].mNetSettings.dccChannel.recvStr();



                Log.i("Net","Recording !");
                callback.sendMessage(callback.obtainMessage(0,raw_data));

                boolean end_record = false;

//                while (!end_record){
//
//                }

            }
            else{
                Log.i("Net","Fail to start camera record");
                callback.sendMessage(callback.obtainMessage(1));
            }

            return "Done";
        }

}

