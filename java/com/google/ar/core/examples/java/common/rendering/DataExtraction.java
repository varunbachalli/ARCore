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
import android.util.Log;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.examples.java.helloar.HelloArActivity;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public class DataExtraction {

    private final String Dat = "Data_extraction";
    private File AuxFile, AuxFileDirectory;
    private File DepthImageDirectory;
    private File ColorImageDirectory;
    private ContextWrapper cw;


    private HandlerThread ColorImageThread = new HandlerThread("ColorImageHandler");
    private HandlerThread DepthImageThread = new HandlerThread("DepthImageHandler");
    private HandlerThread AuxillaryDataThread = new HandlerThread("AuxillaryDataHandler");

    private Handler ColorImageHandler;
    private Handler DepthImageHandler;
    private Handler AuxillaryDataHandler;


    private class SavedData {
        // https://stackoverflow.com/questions/49026297/convert-3d-world-arcore-anchor-pose-to-its-corresponding-2d-screen-coordinates
        // convert 3d coordinates of pose to 2d
        public ArrayList<ByteBuffer> color_image;
        public ShortBuffer depth_image;
        public int height_color, height_depth, width_color, width_depth;
        public float[] projection_mtx, view_mtx, cam_pose, focalLength; // cam pose 1st 4 elements quaternenion rest 3 are translation
        public long time_stamp;
        public ArrayList<float[]> anchor_model_mtxs;
        public ArrayList<float[]> anchor_position_vecs;


        public SavedData() {
            color_image = new ArrayList<>();
            anchor_position_vecs = new ArrayList<>();
            anchor_model_mtxs = new ArrayList<>();
        }

        public ByteBuffer cloneBuffer(ByteBuffer original) {
            ByteBuffer clone = ByteBuffer.allocate(original.capacity());
            original.rewind();//copy from the beginning
            clone.put(original);
            original.rewind();
            clone.flip();
            return clone;
        }

        public ShortBuffer cloneBuffer(ShortBuffer original) {
            ShortBuffer clone = ShortBuffer.allocate(original.capacity());
            original.rewind();//copy from the beginning
            clone.put(original);
            original.rewind();
            clone.flip();
            return clone;
        }


        public void clone(SavedData data, int parts) // parts = 0 - color, 1- depth , 2 - auxillary, 3 - all
        {
            if (parts == 0 || parts == 3) {
                this.color_image.clear();
                for (ByteBuffer buff : data.color_image) {
                    this.color_image.add(cloneBuffer(buff));
                }
                this.height_color = data.height_color;
                this.width_color = data.width_color;
                Log.d(Dat, "Buffer copied Succesfully");
            }

            if (parts == 1 || parts == 3) {
                this.depth_image = cloneBuffer(data.depth_image);
                this.width_depth = data.width_depth;
                this.height_depth = data.height_depth;
            }

            if (parts == 2 || parts == 3) {
                this.projection_mtx = data.projection_mtx.clone();
                this.view_mtx = data.view_mtx.clone();
                this.cam_pose = data.cam_pose.clone();
                this.focalLength = data.focalLength.clone();


                this.anchor_model_mtxs.clear();
                for (float[] c : data.anchor_model_mtxs) {
                    this.anchor_model_mtxs.add(c.clone());
                }

                this.anchor_position_vecs.clear();
                for (float[] c : data.anchor_position_vecs) {
                    this.anchor_position_vecs.add(c.clone());
                }
            }
            this.time_stamp = data.time_stamp;
        }

    }


    private SavedData Data = new SavedData();

    public void onCreate() {
        ColorImageThread.start();
        DepthImageThread.start();
        AuxillaryDataThread.start();

        ColorImageHandler = new Handler(ColorImageThread.getLooper());
        DepthImageHandler = new Handler(DepthImageThread.getLooper());
        AuxillaryDataHandler = new Handler(AuxillaryDataThread.getLooper());

        cw = new ContextWrapper(HelloArActivity.getContext());
        AuxFileDirectory = cw.getDir("AuxFilesDirectory", Context.MODE_APPEND | Context.MODE_PRIVATE);
        DepthImageDirectory = cw.getDir("DepthMapsDirectory", Context.MODE_APPEND | Context.MODE_PRIVATE);
        ColorImageDirectory = cw.getDir("ColorImageDirectory", Context.MODE_APPEND | Context.MODE_PRIVATE);
        AuxFile = new File(AuxFileDirectory, "Auxillary_data.txt");
    }


    public synchronized SavedData SetGetSavedData(SavedData obj, boolean set_get, int parts) // set = true, get = false
    {
        if (set_get) {
            Data.clone(obj, 3);
            return null;
        }
        obj.clone(Data, parts);
        return obj;
    }

    private void SaveDepthImage() {

        DepthImageHandler.post(new Runnable() {
            @Override
            public void run() {
                SavedData data = new SavedData();
                data = SetGetSavedData(data, false, 1);

                short[] depthImageArray = new short[data.depth_image.limit()];
                data.depth_image.get(depthImageArray);
                int width = data.width_depth;
                int height = data.height_depth;

                String imageName = Long.toString(data.time_stamp) + "DepthImage.txt";
                File depthFile = new File(DepthImageDirectory, imageName);

                String text = "Width =" + Integer.toString(width) + "Height =" + Integer.toString(height) + "\n [";
                for (int i = 0; i < depthImageArray.length; ++i) {
                    text = text + Short.toString(depthImageArray[i]) + " , ";
                }
                text = text + "]\n";
                try {
                    FileOutputStream stream = new FileOutputStream(depthFile, true);
                    stream.write(text.getBytes());
                    stream.close();
//                    Toast.makeText(HelloArActivity.getContext(), "Depth Image saved", Toast.LENGTH_SHORT).show();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        });
    }

    private void SaveAuxillaryData() {
        AuxillaryDataHandler.post(new Runnable() {
            @Override
            public void run() {
                SavedData data = new SavedData();
                data = SetGetSavedData(data, false, 2);
                String text = "";
                text = text +
                        Long.toString(data.time_stamp) + " " + "projection Matrix \n [";
                for (int i = 0; i < data.projection_mtx.length; ++i) {
                    text = text + Float.toString(data.projection_mtx[i]) + " , ";
                }

                text = text + "]\n";

                text = text +
                        Long.toString(data.time_stamp) + " " + "View Matrix \n [";
                for (int i = 0; i < data.view_mtx.length; ++i) {
                    text = text + Float.toString(Data.view_mtx[i]) + " , ";
                }
                text = text + "]\n";

                text = text +
                        Long.toString(data.time_stamp) + " " + "Camera Pose[qx,qy,qz,qw, x,y,z] \n [";


                for (int i = 0; i < data.cam_pose.length; ++i) {
                    text = text + Float.toString(Data.cam_pose[i]) + " , ";
                }

                text = text + "]\n";

                text = text+Long.toString(data.time_stamp)+ " " + "Focal Length \n [";

                for(int i = 0; i < data.focalLength.length; ++i)
                {
                    text = text + Float.toString(data.focalLength[i]) + " , ";
                }
                text = text + "]\n";

                text = text +
                        Long.toString(data.time_stamp) + " " + "Anchor Model matrices \n ";

                for (float[] modelmatrix : data.anchor_model_mtxs) {
                    text = text + "[";
                    for (int i = 0; i < modelmatrix.length; ++i) {
                        text = text + Float.toString(modelmatrix[i]) + " , ";
                    }
                    text = text + "]\n";
                }

                text = text +
                        Long.toString(data.time_stamp) + " " + "Anchor Positions \n ";
                for (float[] posvecs : data.anchor_position_vecs) {
                    text = text + "[";
                    for (int i = 0; i < posvecs.length; ++i) {
                        text = text + Float.toString(posvecs[i]) + " , ";
                    }
                    text = text + "]\n";
                }
                try {
                    FileOutputStream stream = new FileOutputStream(AuxFile, true);
                    stream.write(text.getBytes());
                    stream.close();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void SaveColorImage() {
        ColorImageHandler.post(new Runnable() {
            @Override
            public void run() {
                SavedData data = new SavedData();
                data = SetGetSavedData(data, false, 0);
                if (data.color_image.size() == 3) {
                    int ySize = data.color_image.get(0).remaining();
                    int uSize = data.color_image.get(2).remaining();
                    int vSize = data.color_image.get(1).remaining();

                    byte[] nv21 = new byte[vSize + uSize + ySize];

                    if (ySize != 0 && uSize != 0 && vSize != 0) {
                        data.color_image.get(0).get(nv21, 0, ySize);
                        data.color_image.get(2).get(nv21, ySize, uSize);
                        data.color_image.get(1).get(nv21, ySize + uSize, vSize);
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();


                    YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, data.width_color, data.height_color, null);
                    yuv.compressToJpeg(new Rect(0, 0, data.width_color, data.height_color), 100, out);
                    byte[] byteArray = out.toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    File file = new File(ColorImageDirectory, Long.toString(data.time_stamp) + "ColorImage.jpg");
                    Log.d(Dat, "File saved in " + file.toString());
                    Log.d(Dat, "Directory" + ColorImageDirectory.toString());
                    if (!file.exists()) {
                        FileOutputStream fos1 = null;
                        try {
                            fos1 = new FileOutputStream(file);
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos1);
                            fos1.flush();
                            fos1.close();

                            Toast.makeText(HelloArActivity.getContext(), "Color Image saved", Toast.LENGTH_SHORT).show();
                        } catch (java.io.IOException e) {
                            Toast.makeText(HelloArActivity.getContext(), "Color Image saving error", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                } else {
                    Log.d(Dat, "dimensions don't match" + data.color_image.size());
                }
            }
        });

    }


    public void DestroyThreads() {
        DepthImageThread.quitSafely();
        ColorImageThread.quitSafely();
        AuxillaryDataThread.quitSafely();
    }

    public void saveReceivedData() {

        SaveAuxillaryData();
        Log.d(Dat, "SaveAuxillaryData");

        SaveColorImage();
        Log.d(Dat, "SaveColorImage");

        SaveDepthImage();
        Log.d(Dat, "SaveDepthImage");
    }

    public synchronized void saveData(final Frame frame, ArrayList<Anchor> AnchorList, float[] p_mtx, float[] v_mtx, float[] cam_pose, float[] focalLength) {

        try {
            SavedData data = new SavedData();
            Image depthImage = frame.acquireDepthImage();
            Image colorImage = frame.acquireCameraImage();
            Log.d(Dat, "Depth image widht x height = " + Integer.toString(depthImage.getWidth()) + " , " + Integer.toString(depthImage.getHeight()));
            Log.d(Dat, "Color Image width x height = " + Integer.toString(colorImage.getWidth()) + " , " + Integer.toString(colorImage.getHeight()));

            // add Color image data

            data.color_image.add(colorImage.getPlanes()[0].getBuffer());

            data.color_image.add(colorImage.getPlanes()[1].getBuffer());
            data.color_image.add(colorImage.getPlanes()[2].getBuffer());


            Log.d(Dat, "Color image buffers acquired");

            data.height_color = colorImage.getHeight();
            data.width_color = colorImage.getWidth();

            Log.d(Dat, "Color image width and height acquired");
            // add Depth image data

            data.depth_image = depthImage.getPlanes()[0].getBuffer().asShortBuffer();
            data.height_depth = depthImage.getHeight();
            data.width_depth = depthImage.getWidth();

            depthImage.close();
            colorImage.close();

            // Camera Projection and view Matrices and camera pose

            data.projection_mtx = p_mtx.clone();
            data.view_mtx = v_mtx.clone();
            data.cam_pose = cam_pose.clone();
            data.focalLength = focalLength.clone();
            // Frame time stamp

            data.time_stamp = frame.getTimestamp();

            // Update Anchor positions
            for (Anchor a : AnchorList) {
                float[] model = new float[16];
                a.getPose().toMatrix(model, 0);
                data.anchor_model_mtxs.add(model);
                data.anchor_position_vecs.add(a.getPose().getTranslation());
            }
            SetGetSavedData(data, true, 3);
            saveReceivedData();
        } catch (NotYetAvailableException e) {
            Log.d(Dat, "Data is not yet available.");
        }

    }
}
