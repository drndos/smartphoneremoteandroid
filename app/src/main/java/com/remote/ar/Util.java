package com.remote.ar;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Pose;
import com.google.ar.sceneform.math.Matrix;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.TransformableNode;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.zeromq.ZMsg;

import java.io.IOException;

import static android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE;
import static android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE;

public class Util {
	private static final float RADIANS_TO_DEGREES = (float) (180 / Math.PI);
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

	public static  ZMsg packArState(ZMsg message_buffer,int state){
		message_buffer.add("STATE");

		switch (state){
			case Constants.CAMERA_MODE:
				message_buffer.add("CAMERA");
				break;
			case Constants.OBJECT_MODE:
				message_buffer.add("OBJECT");
				break;
		}

		return message_buffer;

	}

	public static ZMsg packTransformableNode(ZMsg message_buffer, TransformableNode node) throws IOException {
		MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();


		float sqrtHalf = (float) Math.sqrt(0.5f);
		//HEADER
		message_buffer.add("NODE");

		float[] rotation = {0,0,0,0};
		Quaternion raw_rotation = node.getWorldRotation();
		rotation[0] = raw_rotation.w;
		rotation[1] = raw_rotation.x;
		rotation[2] = raw_rotation.y;
		rotation[3] = raw_rotation.z;


		packer.packArrayHeader(rotation.length);
		for (float v : rotation) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());
		packer.clear();

		float[] translation = {0,0,0};
		Vector3 raw_translation = node.getWorldPosition();
		translation[0] = raw_translation.x;
		translation[1] = raw_translation.z;
		translation[2] = raw_translation.y;

		packer.packArrayHeader(translation.length);
		for (float v : translation) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());
		packer.clear();

		float[] scale = {0,0,0};
		Vector3 raw_scale = node.getWorldScale();
		scale[0] = raw_scale.x;
		scale[1] = raw_scale.z;
		scale[2] = raw_scale.y;
		packer.packArrayHeader(scale.length);
		for (float v : scale) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());
		packer.clear();

		//MATRIX
		float[] world =  new float[16];

		world = node.getWorldModelMatrix().data;

		packer.packArrayHeader(world.length);
		for (float v : world) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());

		packer.close();
		return message_buffer;

	}

	public static ZMsg packCamera(ZMsg message_buffer,Camera camera) throws IOException {
		MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();

		float sqrtHalf = (float) Math.sqrt(0.5f);
		//HEADER
		message_buffer.add("CAMERA");

		//INTRINSICS
		CameraIntrinsics intrinsics = camera.getImageIntrinsics();
		float[] focalLength = intrinsics.getFocalLength();
		int[] imageSize = intrinsics.getImageDimensions();

		float fovX = (float) (2 * Math.atan2((double) imageSize[0], (double) (2 * focalLength[0])));
		float fovY = (float) (2 * Math.atan2((double) imageSize[1], (double) (2 * focalLength[1])));

		packer.packArrayHeader(2);
		packer.packFloat(fovX);
		packer.packFloat(fovY);
		message_buffer.add(packer.toByteArray());
		packer.clear();

		// ROTATION
		float[] rotation = {0,0,0,0};
		float[] raw_rotation = camera.getDisplayOrientedPose().getRotationQuaternion();
		rotation[0] = raw_rotation[3];
		rotation[1] = raw_rotation[0];
		rotation[2] = raw_rotation[1];
		rotation[3] = raw_rotation[2];

		packer.packArrayHeader(rotation.length);
		for (float v : rotation) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());
		packer.clear();

		// TRANSLATION
		float[] translation = {0,0,0};
		float[] raw_translation = camera.getDisplayOrientedPose().getTranslation();

		translation[0] = raw_translation[0];
		translation[1] = raw_translation[2];
		translation[2] = raw_translation[1];

		packer.packArrayHeader(translation.length);
		for (float v : translation) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());
		packer.clear();

		//MATRIX
		float[] projection =  new float[16];
		camera.getDisplayOrientedPose().toMatrix(projection,0);
		packer.packArrayHeader(projection.length);
		for (float v : projection) {
			packer.packFloat(v);
		}
		message_buffer.add(packer.toByteArray());

		packer.close();
		return message_buffer;

	}

}
