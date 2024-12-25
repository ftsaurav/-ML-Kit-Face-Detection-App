package com.example.facerecognition;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceLandmark;

import com.canhub.cropper.CropImage;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.controls.Facing;
import com.otaliastudios.cameraview.frame.Frame;
import com.otaliastudios.cameraview.frame.FrameProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements FrameProcessor {

    private Facing cameraFacing = Facing.FRONT;
    private ImageView imageView2;
    private ImageView imageView;
    private CameraView faceDetectionCameraView;
    private static final String AD_UNIT_ID = "ca-app-pub-7974244395909250~2315323133";
    private RecyclerView bottomSheetRecyclerView;
    private BottomSheetBehavior bottomSheetBehavior;
    private ArrayList<FaceDetectionModel> faceDetectionModels;
    private AdView adView;
    private FrameLayout adContainerView;

    // Get the ad size with screen width.
    public AdSize getAdSize() {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int adWidthPixels = displayMetrics.widthPixels;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = this.getWindowManager().getCurrentWindowMetrics();
            adWidthPixels = windowMetrics.getBounds().width();
        }

        float density = displayMetrics.density;
        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, adWidth);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new Thread(
                () -> {
                    // Initialize the Google Mobile Ads SDK on a background thread.
                    MobileAds.initialize(this, initializationStatus -> {});
                })
                .start();


        adContainerView = findViewById(R.id.ad_container);
        // Create a new ad view.
        adView = new AdView(this);
        adView.setAdUnitId(AD_UNIT_ID);
        adView.setAdSize(getAdSize());

        // Replace ad container with new ad view.
        adContainerView.removeAllViews();
        adContainerView.addView(adView);

        AdRequest adRequest = new AdRequest.Builder().build();
        adView.loadAd(adRequest);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        faceDetectionModels = new ArrayList<>();
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        bottomSheetBehavior.setDraggable(true);

        imageView = findViewById(R.id.face_detection_image_view);
        faceDetectionCameraView = findViewById(R.id.face_detection_camera_view);
        FrameLayout bottomSheetButton = findViewById(R.id.bottom_sheet_button);
        bottomSheetRecyclerView = findViewById(R.id.bottom_sheet_recycler_view);
        imageView2= findViewById(R.id.face_detection_camera_image_view);

//        faceDetectionCameraView.setFacing(cameraFacing);
//        faceDetectionCameraView.setLifecycleOwner(MainActivity.this);
//        faceDetectionCameraView.addFrameProcessor(this);



        ActivityResultLauncher<CropImageContractOptions> cropImage = registerForActivityResult(new CropImageContract(), result -> {
            if (result.isSuccessful()) {
                imageView2.setImageBitmap(null);
                // This means the crop operation was completed successfully.
                String filePath = result.getUriFilePath(getApplicationContext(), true);
                if (filePath != null) {
                    Bitmap cropped = BitmapFactory.decodeFile(filePath);
                    imageView.setImageBitmap(cropped); // Show cropped image
                    analyzeImage(cropped);
                }
            } else {
                // Handle cases where the crop was canceled or failed.
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dalle);
                imageView2.setImageBitmap(bitmap);
                Toast.makeText(this, "Crop canceled or failed", Toast.LENGTH_SHORT).show();
            }
        });

        bottomSheetRecyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
        bottomSheetRecyclerView.setAdapter(new FaceDetectionAdapter(faceDetectionModels, MainActivity.this));

        bottomSheetButton.setOnClickListener(view -> {
            CropImageOptions cropImageOptions = new CropImageOptions();
            cropImageOptions.imageSourceIncludeGallery = true; // Enable gallery selection
            cropImageOptions.imageSourceIncludeCamera = true; // Enable camera capture
            cropImageOptions.showCropOverlay = true; // Show crop overlay
            cropImageOptions.allowRotation = true; // Allow rotation
            cropImageOptions.autoZoomEnabled = true; // Enable auto zoom for better cropping
            cropImageOptions.fixAspectRatio = false; // Allow free cropping
            cropImageOptions.showIntentChooser = true; // Show chooser for selecting images
            cropImageOptions.cropMenuCropButtonTitle = "Done";


            CropImageContractOptions cropImageContractOptions = new CropImageContractOptions(null, cropImageOptions);
            cropImage.launch(cropImageContractOptions);
        });
    }

    private void analyzeImage(final Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(this, "There was an error", Toast.LENGTH_SHORT).show();
            return;
        }

        imageView.setImageBitmap(null);
        faceDetectionModels.clear();
        Objects.requireNonNull(bottomSheetRecyclerView.getAdapter()).notifyDataSetChanged();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        showProgress();

        InputImage inputImage = InputImage.fromBitmap(bitmap, 0); // 0 is for default rotation
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .build();

        FaceDetection.getClient(options).process(inputImage)
                .addOnSuccessListener(faces -> {
                    Bitmap mutableImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    detectFaces(faces, mutableImage);
                    imageView.setImageBitmap(mutableImage);
                    hideProgress();
                    bottomSheetRecyclerView.getAdapter().notifyDataSetChanged();
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MainActivity.this, "Face detection failed", Toast.LENGTH_SHORT).show();
                    hideProgress();
                });
    }

    private void detectFaces(List<Face> firebaseVisionFaces, Bitmap bitmap) {
        if (firebaseVisionFaces == null || bitmap == null) {
            Toast.makeText(this, "There was an error", Toast.LENGTH_SHORT).show();
            return;
        }

        Canvas canvas = new Canvas(bitmap);
        Paint facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(5f);

        Paint faceTextPaint = new Paint();
        faceTextPaint.setColor(Color.BLUE);
        faceTextPaint.setTextSize(30f);
        faceTextPaint.setTypeface(Typeface.SANS_SERIF);

        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(8f);
        for (int i = 0; i < firebaseVisionFaces.size(); i++) {
            canvas.drawRect(firebaseVisionFaces.get(i).getBoundingBox(), facePaint);
            canvas.drawText("Face " + i, (firebaseVisionFaces.get(i).getBoundingBox().centerX()
                            - (firebaseVisionFaces.get(i).getBoundingBox().width() >> 2) + 8f),
                    (firebaseVisionFaces.get(i).getBoundingBox().centerY()
                            + firebaseVisionFaces.get(i).getBoundingBox().height() >> 2) - 8F,
                    faceTextPaint);

            Face face = firebaseVisionFaces.get(i);

            if (face.getLandmark(FaceLandmark.LEFT_EYE) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.LEFT_EYE)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.LEFT_EYE)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.RIGHT_EYE) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.RIGHT_EYE)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.RIGHT_EYE)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.NOSE_BASE) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.NOSE_BASE)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.NOSE_BASE)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.LEFT_EAR) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.LEFT_EAR)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.LEFT_EAR)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.RIGHT_EAR) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.RIGHT_EAR)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.RIGHT_EAR)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.MOUTH_LEFT) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.MOUTH_LEFT)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.MOUTH_LEFT)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.MOUTH_RIGHT)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.MOUTH_RIGHT)).getPosition().y,
                        8f, landmarkPaint);
            }
            if (face.getLandmark(FaceLandmark.MOUTH_BOTTOM) != null) {
                canvas.drawCircle(Objects.requireNonNull(face.getLandmark(FaceLandmark.MOUTH_BOTTOM)).getPosition().x,
                        Objects.requireNonNull(face.getLandmark(FaceLandmark.MOUTH_BOTTOM)).getPosition().y,
                        8f, landmarkPaint);
            }

            faceDetectionModels.add(new FaceDetectionModel(i+1, "Smiling Probability: " + face.getSmilingProbability()*100+"%"));
            faceDetectionModels.add(new FaceDetectionModel(i+1, "Left Eye Open Probability: " + face.getLeftEyeOpenProbability()*100+"%"));
            faceDetectionModels.add(new FaceDetectionModel(i+1, "Right Eye Open Probability: " + face.getRightEyeOpenProbability()*100+"%"));


        }

        imageView.setImageBitmap(bitmap);
    }


    private void showProgress() {
        findViewById(R.id.bottom_sheet_button_progress).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.GONE);
    }

    private void hideProgress() {
        findViewById(R.id.bottom_sheet_button_progress).setVisibility(View.GONE);
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.VISIBLE);
    }

    @Override
    public void process(@NonNull Frame frame) {
        final int width = frame.getSize().getWidth();
        final int height = frame.getSize().getHeight();

        // Convert the frame data into an InputImage
        InputImage image = InputImage.fromByteArray(frame.getData(), width, height, 0, InputImage.IMAGE_FORMAT_NV21);

        // Set up the options for face detection
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build();

        // Initialize the face detector
        FaceDetector detector = FaceDetection.getClient(options);

        // Perform face detection
        ((FaceDetector) detector).process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(@NonNull List<Face> faces) {
                        imageView.setImageBitmap(null);

                        Bitmap bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);

                        Paint dotPaint = new Paint();
                        dotPaint.setColor(Color.RED);
                        dotPaint.setStyle(Paint.Style.FILL);
                        dotPaint.setStrokeWidth(3f);

                        Paint linePaint = new Paint();
                        linePaint.setColor(Color.GREEN);
                        linePaint.setStyle(Paint.Style.STROKE);
                        linePaint.setStrokeWidth(2f);

                        for (Face face : faces) {
                            // Draw face contours
                            List<PointF> faceContours = Objects.requireNonNull(face.getContour(FaceContour.FACE)).getPoints();
                            for (int i = 0; i < faceContours.size(); i++) {
                                PointF contour = faceContours.get(i);
                                if (i != (faceContours.size() - 1)) {
                                    canvas.drawLine(contour.x, contour.y, faceContours.get(i + 1).x, faceContours.get(i + 1).y, linePaint);
                                } else {
                                    canvas.drawLine(contour.x, contour.y, faceContours.get(0).x, faceContours.get(0).y, linePaint);
                                }
                                canvas.drawCircle(contour.x, contour.y, 4f, dotPaint);
                            }

                            // Draw specific features
                            drawFeatureContours(face, FaceContour.LEFT_EYEBROW_TOP, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.RIGHT_EYEBROW_TOP, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.LEFT_EYE, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.RIGHT_EYE, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.UPPER_LIP_TOP, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.UPPER_LIP_BOTTOM, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.LOWER_LIP_TOP, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.LOWER_LIP_BOTTOM, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.NOSE_BRIDGE, canvas, dotPaint, linePaint);
                            drawFeatureContours(face, FaceContour.NOSE_BOTTOM, canvas, dotPaint, linePaint);
                        }

                        // Handle camera flipping for front-facing camera
                        if (cameraFacing == Facing.FRONT) {
                            // Flip the image horizontally
                            Matrix matrix = new Matrix();
                            matrix.preScale(-1f, 1f);
                            Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                            imageView.setImageBitmap(flippedBitmap);
                        } else {
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    imageView.setImageBitmap(null);
                    // Handle failure here
                });
    }

    // Helper function to draw specific features
    private void drawFeatureContours(Face face, int contourType, Canvas canvas, Paint dotPaint, Paint linePaint) {
        List<PointF> featureContours = face.getContour(contourType).getPoints();
        for (int i = 0; i < featureContours.size(); i++) {
            PointF contour = featureContours.get(i);
            if (i != (featureContours.size() - 1)) {
                canvas.drawLine(contour.x, contour.y, featureContours.get(i + 1).x, featureContours.get(i + 1).y, linePaint);
            } else {
                canvas.drawLine(contour.x, contour.y, featureContours.get(0).x, featureContours.get(0).y, linePaint);
            }
            canvas.drawCircle(contour.x, contour.y, 4f, dotPaint);
        }
        // Add your frame processing logic here
    }

}