package com.remote.blender;

import org.zeromq.SocketType;
import org.zeromq.ZMQ;

public class NetworkSettings {
    private final int stateChannelPort = 5557;
    private final int arChannelPort = 5558;
    private final int dccChannelPort = 5559;

    public final int stateTimout = 100;

    public ZMQ.Context ctx;
    public ZMQ.Socket stateChannel;
    public ZMQ.Socket arChannel;
    public ZMQ.Socket dccChannel;

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
        arChannel = ctx.socket(SocketType.PUSH);

        stateChannel.connect(String.format("tcp://%s:%d",address,stateChannelPort));
        arChannel.connect(String.format("tcp://%s:%d",address,arChannelPort));
    }

}
