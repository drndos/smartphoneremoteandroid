package com.remote.blender;

import android.net.wifi.WifiManager;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;

import static android.content.Context.WIFI_SERVICE;

public class NetworkSettings {
    private final int stateChannelPort = 5557;
    private final int arChannelPort = 5558;
    private final int dccChannelPort = 5559;

    public final int stateTimout = 2000;

    public ZMQ.Context ctx;
    public ZMQ.Socket stateChannel;
    public ZMQ.Socket arChannel;
    public ZMQ.Socket dccChannel;
    public String localIp;

    NetworkSettings(String address){
        ctx = ZMQ.context(1);

        connect(address);

    }

    public void close(){
        stateChannel.close();
        arChannel.close();

    }

    public void connect(String address){


        stateChannel = ctx.socket(SocketType.REQ);
//        dccChannel = ctx.socket(SocketType.REQ);
        arChannel = ctx.socket(SocketType.PUSH);

//        String id = "APP";
//        dccChannel.setIdentity(id.getBytes(ZMQ.CHARSET));

        stateChannel.connect(String.format("tcp://%s:%d",address,stateChannelPort));
//        dccChannel.connect(String.format("tcp://%s:%d",address,dccChannelPort));
        arChannel.connect(String.format("tcp://%s:%d",address,arChannelPort));


    }

}
