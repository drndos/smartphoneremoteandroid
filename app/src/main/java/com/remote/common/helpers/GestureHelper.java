/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.remote.common.helpers;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;

import android.view.View;
import android.view.View.OnTouchListener;
import android.view.GestureDetector.OnGestureListener;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Helper to detect taps using Android GestureDetector, and pass the taps between UI thread and
 * render thread.
 */
public final class GestureHelper implements OnTouchListener , OnDoubleTapListener, OnGestureListener, OnScaleGestureListener, RotationGestureDetector.RotationListener {
  private final GestureDetector gesture;
  private final ScaleGestureDetector gestureScale;
  private final RotationGestureDetector gestureRotation;
  private final BlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);
  public float scaleFactor = 1.0f;
  private boolean inScale = false;
//  private RotationGestureDetector rotationDetector;

  public GestureHelper(Context c){
      gesture = new GestureDetector(c, this);
      gestureScale = new ScaleGestureDetector(c, this);
      gestureRotation = new RotationGestureDetector(this);
  }

    public MotionEvent poll() {
        return queuedSingleTaps.poll();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        gesture.onTouchEvent(event);
        gestureScale.onTouchEvent(event);
        gestureRotation.onTouchEvent(event);
        return true;
    }

    @Override
    public boolean onDown(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onFling(MotionEvent event1, MotionEvent event2, float x, float y) {
        return true;
    }

    @Override
    public void onLongPress(MotionEvent event) {
    }

    @Override
    public boolean onScroll(MotionEvent event1, MotionEvent event2, float x, float y) {
//        Log.i("Net", "SCROLL");
        return true;
    }

    @Override
    public void onShowPress(MotionEvent event) {
    }

    @Override
    public boolean onSingleTapUp(MotionEvent event) {
//      Log.i("Net","SIGNLE TAP");
        queuedSingleTaps.offer(event);
        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
//        view.setVisibility(View.GONE);
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
      float scale = detector.getScaleFactor();

      if(scale > 0.0){
//          Log.i("Net","SCALE:"+scale);
      }
//
        scaleFactor *= detector.getScaleFactor();

        scaleFactor = Math.max(0.05f, Math.min(5.0f, scaleFactor)); // prevent our image from becoming too small
//        scaleFactor = (float) (int) (scaleFactor * 100) / 100; // Change precision to help with jitter when user just rests their fingers //

//        onScroll(null, null, 0, 0); // call scroll to make sure our bounds are still ok //
        return true;
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        inScale = true;
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        inScale = false;
        onScroll(null, null, 0, 0); // call scroll to make sure our bounds are still ok //
    }

    @Override
    public void onRotate(float deltaAngle) {
      if(deltaAngle > 0.0) {
//          Log.i("Net","ROTATE:"+deltaAngle);
      }
    }
}
