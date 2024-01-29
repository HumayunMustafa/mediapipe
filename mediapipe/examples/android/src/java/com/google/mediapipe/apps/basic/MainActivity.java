package com.google.mediapipe.apps.basic;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.components.CameraHelper;
import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.PacketCreator;
import com.google.mediapipe.glutil.EglManager;
import com.google.mediapipe.glutil.TextureRenderer;

import java.io.FileDescriptor;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
  private ApplicationInfo applicationInfo;
  private Button galleryButton;
  private EglManager eglManager;
  private static final String TAG = "MainActivity";

  private ActivityResultLauncher<Intent> imageGettr;
  private ExternalTextureConverter converter;
  private FrameProcessor processor;
  private static final int NUM_BUFFERS = 2;

  // {@link SurfaceTexture} where the camera-preview frames can be accessed.
  private SurfaceTexture previewFrameTexture;
  // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
  private SurfaceView previewDisplayView;

  private Surface surface;

  Bitmap resized;

  static {
    // Load all native libraries needed by the app.
    System.loadLibrary("mediapipe_jni");
    try {
      System.loadLibrary("opencv_java3");
    } catch (java.lang.UnsatisfiedLinkError e) {
      // Some example apps (e.g. template matching) require OpenCV 4.
      System.loadLibrary("opencv_java4");
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    try {
      applicationInfo = getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
    } catch (Exception e) {
      Log.e(TAG, "Cannot find application info" + e);
    }
    AndroidAssetUtil.initializeNativeAssetManager(this);
    previewDisplayView=new SurfaceView(this);
    setupPreviewDisplayView();
    eglManager = new EglManager(null);
    processor = new FrameProcessor(this, eglManager.getNativeContext(), applicationInfo.metaData.getString("binaryGraphName"),
            applicationInfo.metaData.getString("inputVideoStreamName"),
            applicationInfo.metaData.getString("outputVideoStreamName"));
    galleryButton = findViewById(R.id.selectImageButton);
    PermissionHelper.checkAndRequestReadExternalStoragePermissions(this);
    galleryButton.setOnClickListener(view -> processImage());
    registerResult();
  }

  @Override
  protected void onResume() {
    super.onResume();
    converter = new ExternalTextureConverter(
            eglManager.getContext(),
            applicationInfo.metaData.getInt("converterNumBuffers", NUM_BUFFERS));
    converter.setConsumer(processor);
    if (PermissionHelper.readExternalStoragePermissionsGranted(this)) {
      Log.d("onResume","Running onResume");
      displayResult();
    }
  }

  private void displayResult(){
    if(resized!=null) {
      TextureRenderer r = new TextureRenderer();
      int t = bitmapToTexture(resized);

      Log.d("TextureLog", "Texture ID: " + t);
      PacketCreator packet=new PacketCreator(processor.getGraph());
      previewFrameTexture = converter.getSurfaceTexture();
      previewFrameTexture = new SurfaceTexture(t);
      previewDisplayView.setVisibility(View.VISIBLE);
      r.release();
    }
  }

  private void processImage() {
    Intent intent=new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    imageGettr.launch(intent);
  }

  protected void onPreviewDisplaySurfaceChanged(
          SurfaceHolder holder, int format, int width, int height) {
    Log.d("surface width and height ","here " + width+ " "+height);
    resized=Bitmap.createScaledBitmap(resized, width, height, false);
    Log.d("image width and height ","here " + resized.getWidth()+ " "+resized.getHeight());
    converter.setDestinationSize(
            resized.getWidth(),resized.getHeight()
            );
    previewFrameTexture.updateTexImage();
    converter.setSurfaceTexture(
            previewFrameTexture, resized.getWidth(), resized.getHeight());
  }


  private void registerResult(){
    imageGettr= registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
              @Override
              public void onActivityResult(ActivityResult o) {
                try{
                  assert o.getData() != null;
                  Uri imageUri=o.getData().getData();
                  Bitmap bitmap = getBitmapFromUri(imageUri);
                  resized=downscaleBitmap(bitmap);
                }
                catch (Exception e){
                  Toast.makeText(MainActivity.this,"Image not loaded",Toast.LENGTH_SHORT).show();
                }
              }
            }
    );
  }

  @Override
  protected void onPause(){
    super.onPause();
    converter.close();
  }

  protected Size computeViewSize(int width, int height) {
    return new Size(width, height);
  }


  private void setupPreviewDisplayView(){
    previewDisplayView.setVisibility(View.GONE);
    ViewGroup viewGroup=findViewById(R.id.preview_display_layout);
    viewGroup.addView(previewDisplayView);

    previewDisplayView.getHolder().addCallback(
            new SurfaceHolder.Callback() {
              @Override
              public void surfaceCreated(SurfaceHolder holder) {
                Log.e("Output Processor ","here at processor ");
                processor.getVideoSurfaceOutput().setSurface(holder.getSurface());
              }

              @Override
              public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                onPreviewDisplaySurfaceChanged(holder, format, width, height);
              }

              @Override
              public void surfaceDestroyed(SurfaceHolder holder) {
                processor.getVideoSurfaceOutput().setSurface(null);
              }
            }
    );
  }

  private Bitmap getBitmapFromUri(Uri uri) throws IOException {
    ParcelFileDescriptor parcelFileDescriptor =
            getContentResolver().openFileDescriptor(uri, "r");
    assert parcelFileDescriptor != null;
    FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
    Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
    parcelFileDescriptor.close();
    return image;
  }

  private Bitmap downscaleBitmap(Bitmap originalBitmap) {
    double aspectRatio = (double) originalBitmap.getWidth() / originalBitmap.getHeight();
    int[] size=new TextureRenderer().rescaleBasedOnTextureSize();
    int width = size[0];
    int height = size[1];
    if (((double) width / height) > aspectRatio) {
      width = (int) (height * aspectRatio);
    } else {
      height = (int) (width / aspectRatio);
    }
    return Bitmap.createScaledBitmap(originalBitmap, width, height, false);
  }

  public int bitmapToTexture(Bitmap bitmap){
    final int[] textureHandle = new int[1];
    int error = GLES20.glGetError();
    Log.d("TextureLog", "OpenGL Error: " + error);
    if (error != GLES20.GL_NO_ERROR) {
      // Handle error appropriately, e.g., log or throw an exception
      Log.e("OpenGL", "Error generating texture: " + error);
      return -1; // Or handle the error in your own way
    }
    GLES20.glGenTextures(1, textureHandle, 0);
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);

//    bitmap.recycle();
    int error1 = GLES20.glGetError();
    Log.d("TextureLog", "OpenGL Error: " + error1);
    return textureHandle[0];
  }

}