package com.google.mediapipe.apps.basic;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.mediapipe.components.ExternalTextureConverter;
import com.google.mediapipe.components.FrameProcessor;
import com.google.mediapipe.components.PermissionHelper;
import com.google.mediapipe.framework.AndroidAssetUtil;
import com.google.mediapipe.framework.SurfaceOutput;
import com.google.mediapipe.glutil.EglManager;

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
    registerResult();
    galleryButton.setOnClickListener(view -> processImage());
  }

  @Override
  protected void onResume() {
    super.onResume();
    converter = new ExternalTextureConverter(
            eglManager.getContext(),
            applicationInfo.metaData.getInt("converterNumBuffers", NUM_BUFFERS));
    converter.setConsumer(processor);
    if (PermissionHelper.cameraPermissionsGranted(this)) {
      registerResult();
      galleryButton.setOnClickListener(view -> processImage());
    }
  }

  private void processImage() {
    Intent intent=new Intent(Intent.ACTION_PICK);
    intent.setType("image/*");
    imageGettr.launch(intent);
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
                  processor.onNewFrame(bitmap, System.currentTimeMillis());
                  SurfaceOutput image=processor.getVideoSurfaceOutput();
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
}