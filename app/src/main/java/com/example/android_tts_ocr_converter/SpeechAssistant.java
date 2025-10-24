package com.example.android_tts_ocr_converter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SpeechAssistant {

    private Context context;
    private String stringURLEndPoint = "https://api.openai.com/v1/completions";
    private String stringAPIKey = "--------------Your-Key---------------";
    private TextToSpeech textToSpeech;
    private SpeechRecognizer speechRecognizer;
    private Intent intent;
    private boolean isListening = false;


    public SpeechAssistant(Context context){
        this.context = context;

        // 初始化 TextToSpeech
        textToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                textToSpeech.setSpeechRate((float) 0.8);
            }
        });


        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle bundle) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float v) {
            }

            @Override
            public void onBufferReceived(byte[] bytes) {
            }

            @Override
            public void onEndOfSpeech() {
                isListening = false;
            }

            @Override
            public void onError(int i) {
//                textToSpeech.speak("An error occurred during speech recognition", TextToSpeech.QUEUE_FLUSH, null, null);
                isListening = false;
            }

            @Override
            public void onResults(Bundle bundle) {
                isListening = false;
                ArrayList<String> matches = bundle.getStringArrayList(speechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && matches.size() > 0) {
                    String stringInput = matches.get(0);  // 獲取語音轉文字結果
                    chatGPTModel(stringInput);  // 調用 chatGPT 模型進行對話
                }
            }

            @Override
            public void onPartialResults(Bundle bundle) {
            }

            @Override
            public void onEvent(int i, Bundle bundle) {
            }
        });

    }

    // 啟動語音識別
    public void startListening() {
        if (textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
        speechRecognizer.startListening(intent);
        isListening = true;

    }


    public void stopListening() {
        if (isListening) {
            speechRecognizer.cancel();  // 取消正在进行的语音识别
            isListening = false;  // 更新状态
        }
    }


    public void destroyRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();;
            speechRecognizer = null;
        }
    }

    public boolean isListening() {
        return isListening;
    }

    public void stopTextToSpeech() {
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    public void destroyTextToSpeech() {
        if (textToSpeech != null) {
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }


    private void chatGPTModel(String stringInput){
        textToSpeech.speak("進行中", TextToSpeech.QUEUE_FLUSH, null,null);

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("model", "gpt-5-mini");

            JSONArray jsonArrayMessage = new JSONArray();
            JSONObject jsonObjectMessage = new JSONObject();
            jsonObjectMessage.put("role", "user");
            jsonObjectMessage.put("content", stringInput);
            jsonArrayMessage.put(jsonObjectMessage);

            jsonObject.put("messages", jsonArrayMessage);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST,
                stringURLEndPoint, jsonObject, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

                String stringText = null;
                try {
                    stringText = response.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                textToSpeech.speak(stringText, TextToSpeech.QUEUE_FLUSH, null,null);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                textToSpeech.speak("Error: Unable to get response from API", TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> mapHeader = new HashMap<>();
                mapHeader.put("Authorization", "Bearer " + stringAPIKey);
                mapHeader.put("Content-Type", "application/json");

                return mapHeader;
            }

            @Override
            protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
                return super.parseNetworkResponse(response);
            }
        };

        int intTimeoutPeriod = 60000; // 60 seconds timeout duration defined
        RetryPolicy retryPolicy = new DefaultRetryPolicy(intTimeoutPeriod,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        jsonObjectRequest.setRetryPolicy(retryPolicy);
        Volley.newRequestQueue(context).add(jsonObjectRequest);
    }

}
