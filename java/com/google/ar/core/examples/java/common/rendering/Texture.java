/*
 * Copyright 2020 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.common.rendering;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_MAG_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_UNSIGNED_BYTE;
import static android.opengl.GLES20.glBindTexture;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glTexImage2D;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES30.GL_LINEAR;
import static android.opengl.GLES30.GL_RG;
import static android.opengl.GLES30.GL_RG8;

import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Handler;
import android.util.Log;

import com.google.ar.core.Frame;
import com.google.ar.core.examples.java.helloar.HelloArActivity;
import com.google.ar.core.exceptions.NotYetAvailableException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Handle the creation and update of a GPU texture. */
public final class Texture {
  // Stores the latest provided texture id.
  private int textureId = -1;
  private int width = -1;
  private int height = -1;
  private int saveImages = 0;
  private final String image_saving_tag = "SavingInTexture";
  PublishPc publishPc;
  Handler pcHandler;
  /**
   * Creates and initializes the texture. This method needs to be called on a thread with a EGL
   * context attached.
   */
  public void createOnGlThread() {

    int[] textureIdArray = new int[1];
    glGenTextures(1, textureIdArray, 0);
    textureId = textureIdArray[0];
    glBindTexture(GL_TEXTURE_2D, textureId);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    publishPc = new PublishPc();
    publishPc.start();
    pcHandler = publishPc.getHandler();
  }

  /**
   * Updates the texture with the content from acquireDepthImage, which provides an image in DEPTH16
   * format, representing each pixel as a depth measurement in millimeters. This method needs to be
   * called on a thread with a EGL context attached.
   */
  public void SaveImage(Image image, String imageName)
  {
    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
    Log.d(image_saving_tag,"In second thread, called get planes");
    byte[] bytes = new byte[buffer.capacity()];
    buffer.get(bytes);
    Bitmap bitmapImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    Log.d(image_saving_tag,"converted image to bitmap");
    ContextWrapper cw = new ContextWrapper(HelloArActivity.getContext());
    // path to /data/data/yourapp/app_data/imageDir
    File directory = cw.getFilesDir();
    // Create imageDir
    imageName = imageName+".jpg";
    File mypath=new File(directory,imageName);
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(mypath);
      // Use the compress method on the BitMap object to write image to the OutputStream
      bitmapImage.compress(Bitmap.CompressFormat.PNG, 100, fos);
      Log.d(image_saving_tag,"bitmap compressed and saved in the fileoutputstream path");

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        fos.close();
        Log.d(image_saving_tag,"File saved at"+(String)mypath.toString());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }




  private void Saveimages(Image colorImage, Image depthImage)
  {
      Log.d(image_saving_tag,"save image called");
//        Toast.makeText(this, "Save Image called", Toast.LENGTH_LONG).show();
    publishPc.handler.post(new Runnable() {
        @Override
        public void run() {
          String colorImageName = "ColorImage" + Integer.toString(saveImages);
          SaveImage(colorImage,colorImageName);
          String depthImageName = "DepthImage" + Integer.toString(saveImages);
          SaveImage(depthImage,depthImageName);
        }
      });
  }
  public void updateWithDepthImageOnGlThread(final Frame frame) {


    try {
      Image depthImage = frame.acquireDepthImage();
      Image colorImage = frame.acquireDepthImage();
      if(saveImages < 5)
      {
        saveImages++;
        Saveimages(colorImage,depthImage);
      }
      width = depthImage.getWidth();
      height = depthImage.getHeight();
      glBindTexture(GL_TEXTURE_2D, textureId);
      glTexImage2D(
          GL_TEXTURE_2D,
          0,
          GL_RG8,
          width,
          height,
          0,
          GL_RG,
          GL_UNSIGNED_BYTE,
          depthImage.getPlanes()[0].getBuffer());
      depthImage.close();
    } catch (NotYetAvailableException e) {
      // This normally means that depth data is not available yet. This is normal so we will not
      // spam the logcat with this.
    }
  }

  public int getTextureId() {
    return textureId;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
