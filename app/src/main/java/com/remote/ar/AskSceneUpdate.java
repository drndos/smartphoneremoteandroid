package com.remote.ar;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import org.zeromq.SocketType;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class AskSceneUpdate  extends AsyncTask<NetworkManager, Void, String> {
        private Handler callback;
        private ZMQ.Socket link;
        private boolean isSceneReceived;


        public AskSceneUpdate(Handler callback) {
            this.callback = callback;
            Log.i("Net","AskScene task setup");
            isSceneReceived=false;
        }

        private boolean writeScene(File file,byte[] sceneData){
            FileOutputStream stream;
            try {
                stream = new FileOutputStream(file);
                stream.write(sceneData);
                stream.close();
                file.setExecutable(true,false);
                file.setReadable(true,false);
                file.setWritable(true,false);

                Log.i("Net","Done");

                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        protected String doInBackground(NetworkManager... params) {
            try(ZMQ.Context ctx = ZMQ.context(1)) {
                int total = 0;
                int chunks = 0;
                ByteArrayOutputStream fileStream = new ByteArrayOutputStream( );

                // STEP 0: Setup net wire
                Log.i("Net","AskScene: connecting...");
                link = ctx.socket(SocketType.DEALER);

                String identity = "AskScene";
                link.setIdentity(identity.getBytes(ZMQ.CHARSET));
                link.setImmediate(true);
                link.connect(String.format("tcp://%s:%d",params[0].mAddress,params[0].mPort+2));

                Log.i("Net","AskScene: done.");


                ZMQ.Poller items = ctx.poller(1);
                items.register(link, ZMQ.Poller.POLLIN);

                // STEP 1: Send scene request
                Log.i("Net","AskScene: request send");


                Log.i("Net", "send scene update request");

                while (!isSceneReceived){
                    ZMsg scene_request = new ZMsg();
                    scene_request.add("SCENE");
                    scene_request.add(String.valueOf(total));
                    scene_request.add(String.valueOf(Constants.CHUNK_SIZE));
                    scene_request.send(link);

                    items.poll(25000);
                    if (items.pollin(0)) {
                        ZMsg answer =  ZMsg.recvMsg(link);
                        byte[] chunk = answer.getLast().getData();

                        fileStream.write(chunk);
                        chunks += 1;
                        int size = chunk.length;
                        total += size;

                        Log.i("Net", "Load "+total);

                        if(size < Constants.CHUNK_SIZE){
                            Log.i("Net", "Writine the file");
                            // Generating file dir
                            File path = params[0].app.getFilesDir();
                            File file = new File(path, "scene_cache.obj");

                            if (writeScene(file, fileStream.toByteArray())) {
                                Log.i("Net", "Send msg to callback");
                                callback.sendMessage(callback.obtainMessage(0));

                                link.close();
                                ctx.close();

                                return "Done";
                            }

                            break; // Last chunk received; exit
                        }
                    }
                    else {
                        Log.i("Net", "Nothing");
                        break;

                    }

                }

                callback.sendMessage(callback.obtainMessage(1));
                link.close();
                ctx.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return "Done";
        }

}

