package com.example.searchglass;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GoogleSearchActivity extends Activity {
        private static final String TAG = "GoogleSearchActivity";
        private static final int SPEECH_REQUEST_CODE = 0;
        private static final String LIVE_CARD_TAG = "GoogleSearchLiveCard";

        private GestureDetector mGestureDetector;

        private TextView tvResponse;
        private SpeechRecognizer recognizer;
        private TextToSpeech tts;
        private MediaPlayer mediaPlayerWaiting;
        private MediaPlayer mediaPlayerResultsDone;
    private MediaPlayer mediaPlayerTapPrompt;

    private GestureDetector detector;
        private ProgressBar progressBar;
        private ImageView imageView;

        private static final String PROJECT_ID = "your_project_id";
        private static final String LOCATION = "global";

        @Override
        protected void onPause() {
            super.onPause();
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
                recognizer = null;
            }
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(new MyRecognitionListener());
            }
            // Start listening for voice input right away

        }

        private void startVoiceRecognition() {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_google_search);

            tvResponse = (TextView) findViewById(R.id.tvResponse);
            progressBar = (ProgressBar) findViewById(R.id.progressBar);
            imageView = (ImageView) findViewById(R.id.imageView);
            mGestureDetector = createGestureDetector(this);
            startVoiceRecognition();

            mediaPlayerWaiting = MediaPlayer.create(this, R.raw.waiting);
            mediaPlayerResultsDone = MediaPlayer.create(this, R.raw.results_done);
            mediaPlayerTapPrompt = MediaPlayer.create(this, R.raw.tap_prompt);

            mediaPlayerWaiting.setLooping(true);

            detector = createGestureDetector(this);

            tts = new TextToSpeech(this, status -> {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.US);
                }
            });
            tvResponse.setGravity(Gravity.CENTER);

            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new MyRecognitionListener());
        }

        private GestureDetector createGestureDetector(Context context) {
            GestureDetector gestureDetector = new GestureDetector(context);
            // Create the listener for the GestureDetector
            gestureDetector.setBaseListener(gesture -> {
                // Implement scrolling
                if (gesture == Gesture.TAP) {
                    // Handle tap
                    mediaPlayerTapPrompt.start();

                    tts.stop();
                    startVoiceRecognition();
                    return true;
                } else if (gesture == Gesture.SWIPE_RIGHT) {
                    // Handle swipe right
                    finish();
                    return true;
                }
                else if (gesture == Gesture.SWIPE_UP) {
                    // Handle swipe up
                    publishCard(this);
                    return true;
                }
                return false;
            });
            return gestureDetector;
        }

    private void publishCard (Context context){
        // Create a new LiveCard
        LiveCard liveCard = new LiveCard(context, LIVE_CARD_TAG);

        // Set up the live card's action with a pending intent
        // that starts this activity when the card is clicked
        Intent intent = new Intent(context, GoogleSearchActivity.class);
        liveCard.setAction(PendingIntent.getActivity(context, 0, intent, 0));

        // Publish the live card
        liveCard.publish(LiveCard.PublishMode.REVEAL);
    }

    @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            if (detector != null) {
                return detector.onMotionEvent(event);
            }
            return super.onGenericMotionEvent(event);
        }


    private class MyRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) { }
        @Override
        public void onBeginningOfSpeech() { }
        @Override
        public void onRmsChanged(float rmsdB) { }
        @Override
        public void onBufferReceived(byte[] buffer) { }
        @Override
        public void onEndOfSpeech() { }
        @Override
        public void onError(int error) { }
        @Override
        public void onResults(Bundle results) { }
        @Override
        public void onPartialResults(Bundle partialResults) { }
        @Override
        public void onEvent(int eventType, Bundle params) { }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && matches.size() > 0) {
                String query = matches.get(0);
                String urlString = "https://kgsearch.googleapis.com/v1/entities:search?query=" + (query  + "&key=" + Secrets.API_KEY + "&limit=1&indent=True");
                tvResponse.setText("");
                imageView.setImageDrawable(null);
                new PostTask().execute(urlString);
                progressBar.setVisibility(View.VISIBLE);
                tvResponse.setVisibility(View.GONE);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class PostTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String urlString = params[0];

            try {
                TrustManager[] trustAllCerts = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                            }

                            @Override
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return new java.security.cert.X509Certificate[]{};
                            }
                        }
                };

                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());


                OkHttpClient.Builder newBuilder = new OkHttpClient.Builder();
                newBuilder.sslSocketFactory(new TLSSocketFactory(), (X509TrustManager) trustAllCerts[0]);
                newBuilder.hostnameVerifier((hostname, session) -> true);
                newBuilder.connectTimeout(30, TimeUnit.SECONDS); // Sets the connection timeout to 30 seconds
                newBuilder.readTimeout(30, TimeUnit.SECONDS); // Sets the read timeout to 30 seconds
                OkHttpClient newClient = newBuilder.build();


                Request request = new Request.Builder()
                        .url(urlString)
                        .get()
                        .addHeader("Authorization", Secrets.API_KEY)
                        .build();
                Response response = newClient.newCall(request).execute();

                if (!response.isSuccessful()) {
                    Log.e(TAG, "Unsuccessful HTTP Response Code: " + response.code());
                    Log.e(TAG, "Unsuccessful HTTP Response Message: " + response.message());
                    return null;
                }

                String responseBody = response.body().string();
                Log.i(TAG, "Successful HTTP Response: " + responseBody);
                return responseBody;

            } catch (Exception e) {
                Log.e(TAG, "Error in PostTask: " + e.getMessage(), e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progressBar.setVisibility(View.GONE);
            mediaPlayerWaiting.pause();
            mediaPlayerWaiting.seekTo(0);
            mediaPlayerResultsDone.start();
            tvResponse.setTextSize(20);
            tvResponse.setGravity(Gravity.RIGHT);
            tvResponse.setVisibility(View.VISIBLE);

            // Check if result is null first
            if (result == null) {
                tvResponse.setText("Error fetching response from Knowledge Graph Search API");
                Log.e(TAG, "Error fetching response from Knowledge Graph Search API");
                return;  // Return early
            }

            // If result is not null, proceed with parsing the JSON
            try {
                JSONObject jsonResponse = new JSONObject(result);
                if (jsonResponse.has("itemListElement") && jsonResponse.getJSONArray("itemListElement").length() > 0) {
                    JSONObject item = jsonResponse.getJSONArray("itemListElement").getJSONObject(0).getJSONObject("result");
                    if (item.has("detailedDescription") && item.has("image")) {
                        String output = item.getJSONObject("detailedDescription").getString("articleBody");
                        String imageUrl = item.getJSONObject("image").getString("contentUrl");

                        // Load the image into the ImageView using Glide
                        Glide.with(GoogleSearchActivity.this)
                                .load(imageUrl)
                                .apply(new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                                .into(imageView);

                        tvResponse.setText(output);
                        tts.speak(output, TextToSpeech.QUEUE_FLUSH, null);
                    } else {
                        tvResponse.setText("No detailed description or image found for your query.");
                        tts.speak("No detailed description or image found for your query.", TextToSpeech.QUEUE_FLUSH, null);
                    }
                } else {
                    tvResponse.setText("No results found for your query.");
                    tts.speak("No results found for your query.", TextToSpeech.QUEUE_FLUSH, null);
                }
            } catch (JSONException e) {
                tvResponse.setText("Error parsing response from Knowledge Graph Search API");
                Log.e(TAG, "Error parsing JSON response", e);
            }
        }


        }
}

