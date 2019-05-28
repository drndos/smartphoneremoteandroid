package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;

import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

public class SendMessageTask extends AsyncTask<ZMsg, Void, String> {
    private final Handler uiThreadHandler;

    public static final String MESSAGE_PAYLOAD_KEY = "jeromq-service-payload";

    public SendMessageTask(Handler uiThreadHandler) {
        this.uiThreadHandler = uiThreadHandler;
    }

//    public static void prepareMessage(Message m, String msg){
//        Bundle b = new Bundle();
//        b.putString(MESSAGE_PAYLOAD_KEY, msg);
//        m.setData(b);
//        return ;
//    }
//    public static Message bundledMessage(Handler uiThreadHandler, String msg) {
//        Message m = uiThreadHandler.obtainMessage();
//        prepareMessage(m, msg);
//        return m;
//    };

    @Override
    protected String doInBackground(ZMsg... params) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket socket = context.socket(SocketType.PUSH);
        socket.connect("tcp://192.168.0.10:5556");
        params[0].send(socket);
        Log.i("Net","pushing data");


        socket.close();
        context.term();

        return "Done";
    }

//    @Override
//    protected void onPostExecute(String result) {
//        uiThreadHandler.sendMessage(bundledMessage(uiThreadHandler, result));
//    }
}
