package com.remote.blender;

public final class Constants {
    private Constants (){}
    public static final int CAMERA_MODE = 0;
    public static final int OBJECT_MODE = 1;
    public static final int CHUNK_SIZE = 250000;

    public static final int STATE_IDLE = 0;
    public static final int STATE_OFFLINE = 1;
    public static final int STATE_ONLINE = 2;

    public static final int CLIENT_PORT = 5560;
}
