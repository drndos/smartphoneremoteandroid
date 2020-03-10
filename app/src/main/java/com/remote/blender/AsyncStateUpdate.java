package com.remote.blender;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;


public class AsyncStateUpdate extends AsyncTask <String, Void, String>{
    private Handler callback;
    private ZMQ.Socket link;
    private ZMQ.Context ctx;
    private ZMQ.Poller items;
    private  boolean  is_active;
    private String add;
    private Integer mPort;

    public AsyncStateUpdate(Handler cb, String address, Integer port) {

        callback = cb;
        // STEP 0: Setup net wire
        ctx = ZMQ.context(1);
        link = ctx.socket(SocketType.DEALER);
        String identity = "AskState";
        link.setIdentity(identity.getBytes(ZMQ.CHARSET));
        link.setImmediate(true);
        link.setLinger(0);
        link.connect(String.format("tcp://%s:%d",address, port));

        items = ctx.poller(1);
        items.register(link, ZMQ.Poller.POLLIN);
        add = address;
        mPort = port;

    }
    private void reconnect(){
        Log.i("Net","Reconnect");
        link.close();
        items.close();
        ctx.close();


        ctx = ZMQ.context(1);
        link = ctx.socket(SocketType.DEALER);
        String identity = "AskState";
        link.setIdentity(identity.getBytes(ZMQ.CHARSET));
        link.setImmediate(true);
        link.setLinger(0);
        link.connect(String.format("tcp://%s:%d",add, mPort));


        items = ctx.poller(1);
        items.register(link, ZMQ.Poller.POLLIN);
    }

    protected void stop(){
        is_active = false;
    }

    @Override
    protected String doInBackground(String... params){
        is_active = true;

        while (is_active){
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            ZMsg request = new ZMsg();
            request.add("STATE");
            request.add(add);
            request.send(link);
            request.destroy();
            items.poll(2000);

            if (items.pollin(0)) {
                ZMsg raw_data = ZMsg.recvMsg(link);

                raw_data.destroy();
                callback.sendMessage(callback.obtainMessage(0));
            }
            else{
                callback.sendMessage(callback.obtainMessage(1));
                reconnect();

            }



        }
        callback.sendMessage(callback.obtainMessage(2));
        link.close();
        ctx.close();
        Log.i("Net","Stopping heartbeat");

        return "Done";
    }
}
