package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
 
public class ZeroMQMessageTask extends AsyncTask<byte[], Void, String> {
    private final Handler uiThreadHandler;
 
    public ZeroMQMessageTask(Handler uiThreadHandler) {
        this.uiThreadHandler = uiThreadHandler;
    }
 
    @Override
    protected String doInBackground(byte[]... params) {
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket socket = context.socket(SocketType.REQ);
        socket.connect("tcp://192.168.0.10:5556");
 
        socket.send(params[0], 0);
        String result = new String(socket.recv(0));
 
        socket.close();
        context.term();
 
        return result;
    }
 
    @Override
    protected void onPostExecute(String result) {
        uiThreadHandler.sendMessage(Util.bundledMessage(uiThreadHandler, result));
    }
}
