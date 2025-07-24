package com.example.android_tts_ocr_converter;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Locale;

public class TTSManager {

    private static TextToSpeech textToSpeech;

    // 初始化 TTS 实例
    public static void initTTS(Context context) {
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(context, "TTS Language not supported", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(context, "TTS Initialization failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // 获取已经初始化的 TTS 实例
    public static TextToSpeech getTextToSpeech() {
        return textToSpeech;
    }

    // 销毁 TTS 实例
    public static void shutDownTTS() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }

}
