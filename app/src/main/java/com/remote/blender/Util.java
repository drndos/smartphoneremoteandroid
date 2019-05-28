package com.remote.blender;

import android.os.Handler;
import android.os.Bundle;
import android.os.Message;

import com.google.ar.core.Camera;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;

import java.io.IOException;

public class Util {
	public static final String MESSAGE_PAYLOAD_KEY = "jeromq-service-payload";
	
	public static char[] reverseInPlace(byte[] input){
		
		char[] string = new char[input.length];
		for(int i=0;i < input.length;i++){
			string[i] = (char)input[i];
		};
		
		for(int i = 0, j = string.length - 1; i < string.length / 2; i++, j--) {
		    char c = string[i];
		    string[i] = string[j];
		    string[j] = c;
		}
		return string;
	};
	
	public static Message bundledMessage(Handler uiThreadHandler, String msg) {
		Message m = uiThreadHandler.obtainMessage();
	    prepareMessage(m, msg);
	    return m;
	};
	
	public static void prepareMessage(Message m, String msg){
	      Bundle b = new Bundle();
	      b.putString(MESSAGE_PAYLOAD_KEY, msg);
	      m.setData(b);
	      return ;      
	}

	public static byte[] packCamera(Camera camera) throws IOException {
		MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
		packer.packString("CAMERA");

//		float[] rotation = camera.getPose().getRotationQuaternion();
//		float[] translation = camera.getPose().getTranslation();
//
//		packer.packArrayHeader(rotation.length);
//		for (float v : rotation) {
//			packer.packFloat(v);
//		}
//
//		packer.packArrayHeader(translation.length);
//		for (float v : translation) {
//			packer.packFloat(v);
//		}


		return packer.toByteArray();

	}
	public static byte[] packFloatArray(float[] rotation) throws IOException {
		MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
		packer.packArrayHeader(rotation.length);
		for (float v : rotation) {
			packer.packFloat(v);
		}



		return packer.toByteArray();
	}
}
