package com.example.android_tts_ocr_converter;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class VoiceActivity extends AppCompatActivity {

    private static final String TAG = "VoiceActivity";
    ImageView imageView;
    TextView textView;
    TextToSpeech textToSpeech;
    Animation topAnim, bottomAnim;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_voice);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //Animations
        topAnim = AnimationUtils.loadAnimation(this, R.anim.top_animation);
        bottomAnim = AnimationUtils.loadAnimation(this, R.anim.bottom_animation);

        imageView = findViewById(R.id.imageView);
        textView = findViewById(R.id.text_1);

        imageView.setAnimation(topAnim);
        textView.setAnimation(bottomAnim);


        // 初始化TTS实例
        initTextToSpeech();
    }

    // 初始化TTS
    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                Log.d(TAG, "TTS initialized successfully");

                int result = textToSpeech.setLanguage(Locale.getDefault());
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(getApplicationContext(), "TTS Language not supported", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "TTS language not supported or missing data");
                } else {
                    speakIntroText();
                }
            } else {
                Toast.makeText(getApplicationContext(), "TTS Initialization failed", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "TTS Initialization failed with status: " + status);
            }
        });
    }

    private void speakIntroText() {
        String text = "歡迎使用該應用程序, 請仔細聆聽。 ";
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, this.hashCode() + "");

        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // 语音开始播放时
                Log.d(TAG, "TTS started speaking");
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS finished speaking");
                runOnUiThread(() -> {
                    // 语音播放完成后跳转到下一个页面
                    Intent intent = new Intent(VoiceActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS encountered an error");
                runOnUiThread(() -> {
                    Toast.makeText(VoiceActivity.this, "Error in TTS", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(VoiceActivity.this, MainActivity.class);
                    startActivity(intent);
                    finish();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

}