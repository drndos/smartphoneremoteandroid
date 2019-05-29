package com.remote.blender;

import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.google.ar.core.Camera;
import com.google.ar.core.Pose;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.zeromq.ZMsg;

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

	public static ZMsg packCamera(Camera camera) throws IOException {
		ZMsg message_buffer = new ZMsg();
		MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();

		float sqrtHalf = (float) Math.sqrt(0.5f);
		//HEADER
		message_buffer.add("CAMERA");

		float[] rotation = {0,0,0,0};
		float[] raw_rotation = camera.getDisplayOrientedPose().getRotationQuaternion();
		rotation[0] = raw_rotation[3];
		rotation[1] = -raw_rotation[0];
		rotation[2] = raw_rotation[1];
		rotation[3] = -raw_rotation[2];


		packer.packArrayHeader(rotation.length);
		for (float v : rotation) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());
		packer.clear();

		float[] translation = {0,0,0};
		float[] raw_translation = camera.getDisplayOrientedPose().getTranslation();
		translation[0] = raw_translation[0]*10;
		translation[1] = raw_translation[2]*10;
		translation[2] = raw_translation[1]*10;

		packer.packArrayHeader(translation.length);
		for (float v : translation) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());

		packer.close();
		return message_buffer;

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
