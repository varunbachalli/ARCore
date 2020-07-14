package com.google.ar.core.examples.java.common.rendering;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class PublishPc extends Thread{
    Handler handler;
    public Looper looper;
    private final String logString = "PublishPC";
    @Override
    public void run() {
        Log.d(logString, "Start method called");
        Looper.prepare();
        handler = new Handler();
        Looper.loop();
    }
    public Handler getHandler()
    {
        return handler;
    }
}
