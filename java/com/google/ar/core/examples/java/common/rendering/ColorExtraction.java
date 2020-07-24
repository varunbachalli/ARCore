package com.google.ar.core.examples.java.common.rendering;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.examples.java.helloar.HelloArActivity;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class ColorExtraction {

    private HandlerThread ColorImageThread = new HandlerThread("ColorImageHandler");
    private Handler ColorImageHandler;
    private File ColorImageDirectory;
    private ContextWrapper cw;
    private int image_number = 0;

    public void onCreate() {
        ColorImageThread.start();

        ColorImageHandler = new Handler(ColorImageThread.getLooper());


        cw = new ContextWrapper(HelloArActivity.getContext());

        ColorImageDirectory = cw.getDir("WallColorsDirectory", Context.MODE_APPEND | Context.MODE_PRIVATE);

    }

    private void SaveColorImage(ArrayList<ByteBuffer> image, int height, int width) {
        ColorImageHandler.post(new Runnable() {
            @Override
            public void run() {
                if (image.size() == 3) {
                    int ySize = image.get(0).remaining();
                    int uSize = image.get(2).remaining();
                    int vSize = image.get(1).remaining();

                    byte[] nv21 = new byte[vSize + uSize + ySize];

                    if (ySize != 0 && uSize != 0 && vSize != 0) {
                        image.get(0).get(nv21, 0, ySize);
                        image.get(2).get(nv21, ySize, uSize);
                        image.get(1).get(nv21, ySize + uSize, vSize);
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();


                    YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
                    yuv.compressToJpeg(new Rect(0, 0,width, height), 100, out);
                    byte[] byteArray = out.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    File file = new File(ColorImageDirectory, Integer.toString(image_number) + "ColorImage.jpg");
                    if (!file.exists()) {
                        FileOutputStream fos1 = null;
                        try {
                            fos1 = new FileOutputStream(file);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos1);
                            fos1.flush();
                            fos1.close();

                            Toast.makeText(HelloArActivity.getContext(), "Wall Image saved", Toast.LENGTH_SHORT).show();
                        } catch (java.io.IOException e) {
                            Toast.makeText(HelloArActivity.getContext(), "Color Image saving error", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                }
            }
        });

    }

    public void DestroyThreadsSafely()
    {
        ColorImageThread.quitSafely();
    }

    public void saveWallColor(final Frame frame)
    {
        try {
            Image colorImage = frame.acquireCameraImage();
            ArrayList<ByteBuffer> color_buff = new ArrayList<>();
            color_buff.add(colorImage.getPlanes()[0].getBuffer());
            color_buff.add(colorImage.getPlanes()[1].getBuffer());
            color_buff.add(colorImage.getPlanes()[2].getBuffer());
            int width = colorImage.getWidth();
            int height = colorImage.getHeight();
            SaveColorImage(color_buff,height, width);
            colorImage.close();
        } catch (NotYetAvailableException e) {
            e.printStackTrace();
        }
    }

}
