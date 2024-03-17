package com.example.tomatoleafdetector;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.example.tomatoleafdetector.ml.Blackmodel;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class MainActivity extends AppCompatActivity {
    TextView result, knowmore;
    ImageView img, uploadButton;
    Button detectButton;

    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final int STORAGE_PERMISSION_CODE = 102;
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_IMAGE_UPLOAD = 2;
    int imageSize = 224;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        result = findViewById(R.id.text_result);
        result.setVisibility(View.GONE);

        detectButton = findViewById(R.id.detect_button);
        uploadButton = findViewById(R.id.upload_button);
        img = findViewById(R.id.img_view);

        detectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                == PackageManager.PERMISSION_GRANTED) {
                    // Start the camera for taking a photo
                    startCamera();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            CAMERA_PERMISSION_CODE);
                }
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
                    // Start the gallery for uploading a photo
                    startImageUpload();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            STORAGE_PERMISSION_CODE);
                }
            }
        });
    }

    private void startCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    private void startImageUpload() {
        Intent uploadIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        uploadIntent.setType("image/*");
        startActivityForResult(uploadIntent, REQUEST_IMAGE_UPLOAD);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults.length > 1 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera and storage permissions are required", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startImageUpload();
            } else {
                Toast.makeText(this, "Storage permission is required for uploading images", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK && data != null) {
            // Handle the image taken from the camera
            Bundle extras = data.getExtras();
            if (extras != null) {
                Bitmap imageBitmap = (Bitmap) extras.get("data");
                if (imageBitmap != null) {
                    Bitmap resizedImage = resizeImage(imageBitmap);
                    processImage(resizedImage);
                }
            }
        } else if (requestCode == REQUEST_IMAGE_UPLOAD && resultCode == RESULT_OK && data != null) {
            // Handle the image uploaded from the gallery
            Uri selectedImage = data.getData();
            try {
                Bitmap imageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImage);
                if (imageBitmap != null) {
                    Bitmap resizedImage = resizeImage(imageBitmap);
                    processImage(resizedImage);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void processImage(Bitmap image) {
        Pair<String, Float> resultPair = classifyImage(image);
        if (resultPair != null) {
            String predictedClass = resultPair.first;
            if ("No Leaf Detected".equals(predictedClass)) {
                img.setImageResource(R.drawable.img_1);
                float confidence = resultPair.second;
                String k = (confidence * 100) + "%";
                result.setText(predictedClass);
                result.setVisibility(View.VISIBLE);
                Toast.makeText(getApplicationContext(), "Please Make Sure You Have Captured a Leaf", Toast.LENGTH_SHORT).show();
            } else {
                img.setImageBitmap(image);
                float confidence = resultPair.second;
                String k = (confidence * 100) + "%";
                result.setText(predictedClass + " " + k);
                result.setVisibility(View.VISIBLE);
            }
        }
    }

    private Bitmap resizeImage(Bitmap image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float scaleWidth = ((float) imageSize) / width;
        float scaleHeight = ((float) imageSize) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(image, 0, 0, width, height, matrix, true);
    }

    private Pair<String, Float> classifyImage(Bitmap image) {
        try {
            Blackmodel model = Blackmodel.newInstance(getApplicationContext());
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, imageSize, imageSize, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());
            int[] intValue = new int[imageSize * imageSize];
            image.getPixels(intValue, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValue[pixel++];
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }
            inputFeature0.loadBuffer(byteBuffer);
            Blackmodel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();
            float[] confidence = outputFeature0.getFloatArray();
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidence.length; i++) {
                if (confidence[i] > maxConfidence) {
                    maxConfidence = confidence[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Tomato Mosaic Virus", "Target Spot", "Bacterial spot", "Tomato Yellow Leaf Curl Virus", "Late Blight", "Leaf Mold", " Early Blight", "Spider Mites Two-spotted Spider mite", "Tomato Healthy", "Septoria Leaf Spot", "No Leaf Detected"};
            String predictedClass = classes[maxPos];
            return new Pair<>(predictedClass, maxConfidence);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
