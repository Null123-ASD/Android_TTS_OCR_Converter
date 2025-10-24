package com.example.android_tts_ocr_converter;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    PreviewView previewView;
    ImageView imageView;
    ScrollView scrollView;
    TextView textView;
    //    Button button;
    RelativeLayout deltaRelative;
    Bitmap bitmap;
    TextToSpeech textToSpeech;
    ImageCapture imageCapture;
    ProgressDialog otherProgressDialog, exitProgressDialog ;
    private TextRecognizer recognizer;
    private SpeechAssistant speechAssistant;
    Handler handler = new Handler(Looper.getMainLooper());
    boolean canCapture = true; // 标志位，控制音量增大键是否可用
    private boolean volumeDownPressedOnce = false; // 音量减小键是否已按下
    private MediaPlayer mediaPlayer;
    private OverlayView overlayView; // 新增 OverlayView
    private boolean isTextDetectionActive = true; // 控制文字检测和语音提示的开关
    private boolean lastTextDetectedState = false; // 记录上一次的文字检测状态
    private boolean isResultScreen = false; // 标志是否处于结果界面
    private int volumeUpClickCount = 0; // 记录音量增大键的点击次数
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // 双击时间窗口（300毫秒）
    private String recognizedText = ""; // 存储识别出的文字
    private boolean isChatAICooldown = false; // 新增冷卻標誌
    private static final long CHAT_AI_COOLDOWN_MS = 1000; // 冷卻時間 1 秒
    private boolean isPromptRepeating = false; // 控制重複播放標誌

    private static final String PROMPT_TEXT = "識別完成，點擊屏幕可重播語音，雙擊增大音量鍵可啟動 AI 聊天功能，點擊減少音量鍵退回拍攝狀態，雙擊減少音量鍵可退出應用。";
    private static final String NO_TEXT_PROMPT = "識別唔到文字，請對準對焦要識別的圖像，並點擊減少音量鍵退回拍攝狀態。";
    private static final String UTTERANCE_ID_RECOGNIZED = "recognized_text";
    private static final long REPEAT_INTERVAL_MS = 1500; // 重複間隔 2 秒
    private Handler promptHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        initializeTextToSpeech();
        initializeTextRecognizer(); // Preload OCR engine
        requestCameraPermission();
        requestMicrophonePermission();
    }

    // 初始化 OCR 文字识别器，支持中文,ENG
    private void initializeTextRecognizer() {
        // Initialize OCR Recognizer
//        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    }

    // 初始化各種視圖
    private void initializeViews() {
        speechAssistant = new SpeechAssistant(this);

        previewView = findViewById(R.id.preview_view);
        imageView = findViewById(R.id.image_view);
        scrollView = findViewById(R.id.scroll_view);
        textView = findViewById(R.id.text_view);
        overlayView = findViewById(R.id.overlay_view); // 初始化 OverlayView
        deltaRelative = findViewById(R.id.deltaRelative); // 初始化 deltaRelative
        View clickOverlay = findViewById(R.id.clickOverlay); // 初始化透明覆蓋層


        clickOverlay.setVisibility(View.GONE);
        // 設置透明覆蓋層的點擊監聽器
        clickOverlay.setOnClickListener(v -> {
            if (isResultScreen && !recognizedText.isEmpty()) {
                speakText(recognizedText, UTTERANCE_ID_RECOGNIZED);
            } else {

            }
        });

        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    }

    // 初始化 TTS（文字轉語音）
    private void initializeTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(getApplicationContext(), "TTS Language not supported", Toast.LENGTH_SHORT).show();
                } else {
                    textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onStart(String utteranceId) {
                            // 語音開始播放
                        }

                        @Override
                        public void onDone(String utteranceId) {
                            if (utteranceId.equals(UTTERANCE_ID_RECOGNIZED)) {
                                // 識別文字語音播放完成，啟動重複播放
                                startRepeatingSpeech(PROMPT_TEXT);
                            }
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.e("TTS", "Error playing speech for utterance: " + utteranceId);
                        }
                    });
                }
            } else {
                Toast.makeText(getApplicationContext(), "TTS Initialization failed", Toast.LENGTH_SHORT).show();
            }
        });
    }



    // 请求摄像头权限
    private void requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 100);
        } else {
            openCamera();
        }
    }

    // 请求麦克风权限
    private void requestMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, 200);
        }
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == 200) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Microphone permission is required for speech recognition", Toast.LENGTH_LONG).show();
            }
        }
    }

    // 获取设备最佳分辨率
    private Size getBestResolution() {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        int screenWidth = displayMetrics.widthPixels;
        int screenHeight = displayMetrics.heightPixels;
        return new Size(screenWidth, screenHeight);

    }



    // 打开摄像头并准备拍照
    // Initialize CameraX
    private void openCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                //根据设备支持的分辨率动态调整图像捕获设置。
                Size targetResolution = getBestResolution();

                Preview preview = new Preview.Builder()
                        .setTargetResolution(targetResolution)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setTargetResolution(targetResolution)
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // 添加 ImageAnalysis 用于实时文字检测
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(targetResolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), this::analyzeImage);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, imageAnalysis, preview);

                isTextDetectionActive = true; // 启动文字检测和语音提示
                lastTextDetectedState = false; // 重置文字检测状态
                isResultScreen = false; // 进入相机预览界面

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Log.e("Camera", "Error opening camera: ", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }



    // 实时分析相机帧
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy imageProxy) {
        if (!isTextDetectionActive) {
            imageProxy.close();
            return; // 如果文字检测被禁用，直接返回
        }

        InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());
        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    boolean textDetected = !visionText.getTextBlocks().isEmpty();
                    processTextWithBoundingBox(textDetected);
                    imageProxy.close();
                })
                .addOnFailureListener(e -> {
                    Log.e("TextDetection", "Failed to analyze image: " + e.getMessage());
                    imageProxy.close();
                });
    }

    private void processTextWithBoundingBox(boolean textDetected) {
        handler.post(() -> {
            // 如果文字检测状态发生变化，停止当前语音
            if (textDetected != lastTextDetectedState) {
                if (textToSpeech != null && textToSpeech.isSpeaking()) {
                    textToSpeech.stop();
                }
                lastTextDetectedState = textDetected;
            }

            overlayView.setTextDetected(textDetected); // 更新边框颜色

            if (textDetected) {
                int imageWidth = previewView.getWidth();
                int imageHeight = previewView.getHeight();
                int centerX = imageWidth / 2;
                int centerY = imageHeight / 2;

                // 模拟获取文字中心（这里简化处理，因为我们不再绘制多个边界框）
                provideGuidance(centerX, centerY, centerX, centerY); // 使用中心点模拟
            } else {
                String noTextPrompt = "未檢測到文字，請移動手機對準文字區域。";
                if (!textToSpeech.isSpeaking()) {
                    textToSpeech.speak(noTextPrompt, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");
                }
            }
        });
    }

    private void provideGuidance(int textCenterX, int textCenterY, int centerX, int centerY) {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        StringBuilder guidanceMessage = new StringBuilder();

        if (textCenterX < centerX - 100) {
            guidanceMessage.append("文字在左側，請向右移動手機。");
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(500);
            }
        } else if (textCenterX > centerX + 100) {
            guidanceMessage.append("文字在右側，請向左移動手機。");
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(new long[]{0, 500, 200, 500}, -1);
            }
        }

        if (textCenterY < centerY - 100) {
            guidanceMessage.append("文字在上部，請向下移動手機。");
        } else if (textCenterY > centerY + 100) {
            guidanceMessage.append("文字在下部，請向上移動手機。");
        }

        if (guidanceMessage.length() > 0) {
            String finalMessage = guidanceMessage.toString();
            if (!textToSpeech.isSpeaking()) {
                textToSpeech.speak(finalMessage, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");
            }
        } else {
            if (!textToSpeech.isSpeaking()) {
                textToSpeech.speak("文字位於畫面中央，請按增大音量鍵確認。", TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");
            }
        }
    }


    private void delayedSpeak(String text, long delayMillis) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> speakText(text, null), delayMillis);
    }

    // 拍照并处理
    public void captureImage() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        // 停止语音提示和文字检测
        isTextDetectionActive = false;
        // 保存拍照的文件
        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                        if (bitmap != null) {
                            handleCapturedImage(bitmap);
                        } else {
                            Toast.makeText(MainActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this, "Image capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }


    // 处理拍摄的图像
    private void handleCapturedImage(Bitmap bitmap) {
        previewView.setVisibility(View.GONE);
        overlayView.setVisibility(View.GONE); // 隐藏实时边框
        Bitmap scaledBitmap = scaleToFitImageView(bitmap);
        imageView.setImageBitmap(scaledBitmap);
        imageView.setVisibility(View.VISIBLE);
        scrollView.setVisibility(View.VISIBLE);
        textView.setVisibility(View.VISIBLE);
        bitmap.recycle();
        detectTextFromBitmap(scaledBitmap);
        isResultScreen = true; // 进入结果界面
        View clickOverlay = findViewById(R.id.clickOverlay);
        clickOverlay.setVisibility(View.VISIBLE);

    }

    // 将图像缩放以适配 ImageView
    private Bitmap scaleToFitImageView(Bitmap source) {
        int targetWidth = imageView.getWidth();
        int targetHeight = imageView.getHeight();

        // 如果 ImageView 的尺寸还未确定，使用屏幕尺寸
        if (targetWidth == 0 || targetHeight == 0) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            targetWidth = displayMetrics.widthPixels;
            targetHeight = displayMetrics.heightPixels;
        }

        float scaleFactor = Math.min(
                (float) targetWidth / source.getWidth(),
                (float) targetHeight / source.getHeight());

        int scaledWidth = Math.round(source.getWidth() * scaleFactor);
        int scaledHeight = Math.round(source.getHeight() * scaleFactor);

        return Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
    }

    // 预处理图像（灰度化和二值化）
    private Bitmap preprocessImage(Bitmap original) {
        try {
            Bitmap scaledBitmap = scaleToFitImageView(original);
            Bitmap grayBitmap = toGrayscale(scaledBitmap);
            Bitmap contrastBitmap = enhanceContrast(grayBitmap, 1.5);
            Bitmap sharpenedBitmap = sharpenImage(contrastBitmap);
            Bitmap binarizedBitmap = binarizeImage(sharpenedBitmap);

            scaledBitmap.recycle();
            grayBitmap.recycle();
            contrastBitmap.recycle();

            return binarizedBitmap;
        } catch (Exception e) {
            Log.e("ImageProcessing", "Error in preprocessImage: " + e.getMessage());
            return original; // 返回原图以避免崩溃
        }
    }

    private Bitmap toGrayscale(Bitmap src) {
        Bitmap grayBitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(grayBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(src, 0, 0, paint);
        return grayBitmap;
    }

    private Bitmap enhanceContrast(Bitmap bitmap, double contrast) {
        try {
            Bitmap contrastBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            for (int x = 0; x < bitmap.getWidth(); x++) {
                for (int y = 0; y < bitmap.getHeight(); y++) {
                    int pixel = bitmap.getPixel(x, y);
                    int red = (int) (Color.red(pixel) * contrast);
                    int green = (int) (Color.green(pixel) * contrast);
                    int blue = (int) (Color.blue(pixel) * contrast);

                    red = Math.min(255, Math.max(0, red));
                    green = Math.min(255, Math.max(0, green));
                    blue = Math.min(255, Math.max(0, blue));

                    contrastBitmap.setPixel(x, y, Color.rgb(red, green, blue));
                }
            }
            return contrastBitmap;

        }catch (Exception e) {
            Log.e("ImageProcessing", "Error in enhanceContrast: " + e.getMessage());
            return bitmap; // 返回原图以避免崩溃
        }

    }

    private Bitmap sharpenImage(Bitmap src) {
        float[] sharpenMatrix = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };

        Bitmap sharpenedBitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
        Canvas canvas = new Canvas(sharpenedBitmap);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix(sharpenMatrix);
        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(filter);
        canvas.drawBitmap(src, 0, 0, paint);
        return sharpenedBitmap;
    }

    private Bitmap binarizeImage(Bitmap src) {
        Bitmap binarizedBitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        for (int x = 0; x < src.getWidth(); x++) {
            for (int y = 0; y < src.getHeight(); y++) {
                int pixel = src.getPixel(x, y);
                int gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;

                if (gray > 128) {
                    binarizedBitmap.setPixel(x, y, Color.WHITE);
                } else {
                    binarizedBitmap.setPixel(x, y, Color.BLACK);
                }
            }
        }
        return binarizedBitmap;
    }


    // 文字识别
    private void detectTextFromBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            runOnUiThread(() -> Toast.makeText(getApplicationContext(), "Bitmap is null", Toast.LENGTH_LONG).show());
            return;
        }

        otherProgressDialog = ProgressDialog.show(this, "Recognizing text", "Please wait...", true);
        String text = "正在識別文字中，請等候。";
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");

        new Thread(() ->{
            // 图像预处理：灰度化
            Bitmap processedBitmap = preprocessImage(bitmap);
            InputImage image = InputImage.fromBitmap(processedBitmap, 0);

            recognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        processText(visionText);  // 处理识别到的文字
                        otherProgressDialog.dismiss();
                    })
                    .addOnFailureListener(e -> {
                        otherProgressDialog.dismiss();
                        Toast.makeText(getApplicationContext(), "Text recognition failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("TTS", "Error during text recognition", e);
                    });
        }).start();

    }

    // 处理识别到的文字
    private void processText(Text visionText) {
        runOnUiThread(() -> {
            StringBuilder resultText = new StringBuilder();
            boolean textDetected = false;

            for (Text.TextBlock block : visionText.getTextBlocks()) {
                String text = block.getText().trim(); // 去掉首尾空格
                if (!text.isEmpty()) {
                    textDetected = true;
                    resultText.append(text).append("\n");
                }
            }

            if (!textDetected) {
                recognizedText = "";
                Toast.makeText(getApplicationContext(), "No text detected", Toast.LENGTH_LONG).show();
                startRepeatingSpeech(NO_TEXT_PROMPT); // 啟動重複播放未識別提示
            } else {
                recognizedText = resultText.toString();
                textView.setText(recognizedText);
                // 自動播放語音
                speakText(recognizedText, UTTERANCE_ID_RECOGNIZED);
            }
        });
    }


    // 语音输出
    private void speakText(String text, String utteranceId) {
        if (textToSpeech != null) {
            if (textToSpeech.isSpeaking()) {
                textToSpeech.stop();
            }
            Bundle params = new Bundle();
            if (utteranceId != null) {
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            }
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
        } else {
            Log.e("TTS", "TextToSpeech not initialized");
        }
    }

    private void startRepeatingSpeech(String textToRepeat) {
        if (!isResultScreen || isPromptRepeating) {
            return;
        }

        isPromptRepeating = true;
        promptHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isResultScreen && textToSpeech != null && !textToSpeech.isSpeaking()) {
                    speakText(textToRepeat, null);
                }
                if (isResultScreen && isPromptRepeating) {
                    promptHandler.postDelayed(this, REPEAT_INTERVAL_MS);
                } else {
                    isPromptRepeating = false;
                }
            }
        }, REPEAT_INTERVAL_MS);
    }

    private void stopRepeatingSpeech() {
        isPromptRepeating = false;
        if (promptHandler != null) {
            promptHandler.removeCallbacksAndMessages(null); // 只清提示語音，不影響 main handler
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }




    // 监听音量键
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 检查是否需要停止语音助手
        if (speechAssistant != null) {
            if (speechAssistant.isListening()) {
                speechAssistant.stopListening();;
                speechAssistant.destroyRecognizer();
            }
            // 停止 TextToSpeech
            speechAssistant.stopTextToSpeech();
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (isResultScreen) {
                // 在结果界面，检测双击
                volumeUpClickCount++;
                if (volumeUpClickCount == 1) {
                    // 第一次点击，启动双击检测
                    handler.postDelayed(() -> {
                        if (volumeUpClickCount == 2) {
                            // 检测到双击，触发 Chat AI
                            stopRepeatingSpeech();
                            if (textToSpeech != null) textToSpeech.stop();
                            
                            if (isChatAICooldown) {
                                Toast.makeText(this, "Please wait a moment before starting Chat AI again", Toast.LENGTH_SHORT).show();
                                String text = "請保持1秒鐘的間隙時間。";
                                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");
                                volumeUpClickCount = 0;
                                return;
                            }

                            try {
                                // 检查麦克风权限
                                if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.RECORD_AUDIO)
                                        != PackageManager.PERMISSION_GRANTED) {
                                    Toast.makeText(MainActivity.this, "Microphone permission is required for Chat AI", Toast.LENGTH_LONG).show();
                                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO}, 200);
                                    return;
                                }

                                // 停止当前语音
                                if (textToSpeech != null) {
                                    textToSpeech.stop();
                                }

                                // 檢查並重新初始化 speechAssistant
                                if (speechAssistant == null) {
                                    speechAssistant = new SpeechAssistant(this);
                                }

                                // 启动 Chat AI
                                if (speechAssistant != null) {
                                    speechAssistant.startListening();
                                    Toast.makeText(MainActivity.this, "Chat AI started", Toast.LENGTH_SHORT).show();

                                    // 設置冷卻時間
                                    isChatAICooldown = true;
                                    handler.postDelayed(() -> isChatAICooldown = false, CHAT_AI_COOLDOWN_MS);
                                    stopRepeatingSpeech(); // 啟動 Chat AI 時停止重複播放
                                } else {
                                    Toast.makeText(MainActivity.this, "Chat AI failed to start: SpeechAssistant not initialized", Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "Failed to start Chat AI: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                String text = "請保持1秒鐘的間隙時間。";
                                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");
                                // 重新初始化 speechAssistant
                                speechAssistant = new SpeechAssistant(this);
                            }
                        } else {
                            // 未检测到双击，重置计数器
                            Toast.makeText(this, "Double click volume up to start Chat AI", Toast.LENGTH_SHORT).show();
                        }
                        volumeUpClickCount = 0; // 重置计数器
                    }, DOUBLE_CLICK_TIME_DELTA);
                }
            } else {
                // 在相机预览界面，执行拍摄确认逻辑
                if (canCapture) {
                    captureImage();
                    textView.setText("");
                    Toast.makeText(this, "Volume up pressed, capturing image", Toast.LENGTH_SHORT).show();
                    canCapture = false;
                }
            }
            return true; // 消费事件
        }

        // 如果有语音正在播放，打断它
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop(); // 停止当前正在播放的语音
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (volumeDownPressedOnce) {
                String text = "已退出";
                textToSpeech.setSpeechRate(1.25f);
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");
                finish();
            } else {
                volumeDownPressedOnce = true;
                handler.postDelayed(() -> volumeDownPressedOnce = false, 1000); // 1秒内有效

                // 重新拍摄
                previewView.setVisibility(View.VISIBLE);
                overlayView.setVisibility(View.VISIBLE); // 显示实时边框
                imageView.setVisibility(View.GONE);
                scrollView.setVisibility(View.GONE);
                textView.setVisibility(View.GONE);
                openCamera();  // 重新打开相机
                Toast.makeText(this, "Volume down pressed, retaking image", Toast.LENGTH_SHORT).show();
                canCapture = true; // 重新启用拍摄
                isResultScreen = false; // 返回相机预览界面
                recognizedText = ""; // 清空识别文字
                View clickOverlay = findViewById(R.id.clickOverlay);
                clickOverlay.setVisibility(View.GONE);
                stopRepeatingSpeech(); // 離開結算界面時停止重複播放

            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true; // 消费事件
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (speechAssistant != null) {
            speechAssistant.destroyRecognizer();
            speechAssistant.destroyTextToSpeech();
            speechAssistant = null; // 確保銷毀後設為 null
        }
        super.onDestroy();
    }

}
