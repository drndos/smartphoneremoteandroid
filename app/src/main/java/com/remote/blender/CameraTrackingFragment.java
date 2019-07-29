package com.remote.blender;

import com.google.ar.core.Config;
import com.google.ar.core.Session;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.core.Frame;


public class CameraTrackingFragment extends ArFragment {
    @Override
    protected Config getSessionConfiguration(Session session) {
        Config config = super.getSessionConfiguration(session);

        // Use setFocusMode to configure auto-focus.
        config.setFocusMode(Config.FocusMode.AUTO);

        return config;
    }
}
