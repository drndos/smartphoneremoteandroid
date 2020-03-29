package com.remote.ar;


import org.zeromq.SocketType;
import org.zeromq.ZMQ;


public class NetworkSettings {
    public ZMQ.Context ctx;
    public ZMQ.Socket arChannel;

    NetworkSettings(String address, Integer port){
        ctx = ZMQ.context(1);

        connect(address, port);

    }

    public void close(){
        arChannel.close();

    }

    public void connect(String address,  Integer port){
        arChannel = ctx.socket(SocketType.PUSH);
        arChannel.connect(String.format("tcp://%s:%d",address,port+1));


    }

}
