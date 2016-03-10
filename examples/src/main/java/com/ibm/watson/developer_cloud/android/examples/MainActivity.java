/**
 * © Copyright IBM Corporation 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/

package com.ibm.watson.developer_cloud.android.examples;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Vector;

import android.app.FragmentTransaction;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.app.ActionBar;
import android.app.Fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

// IBM Watson SDK
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.android.speech_common.v1.TokenProvider;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MainActivity extends Activity implements ISpeechDelegate{

    private static final String TAG = "MainActivity";
    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED, TALK
    }
    int imageId = R.id.secretary;
    ConnectionState mState = ConnectionState.IDLE;
    private Handler mHandler = null;
    private static String mRecognitionResults = "";
    ImageView buttonRecord = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        // Strictmode needed to run the http/wss request for devices > Gingerbread
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                    .permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }
        //setContentView(R.layout.activity_tab_text);

        ActionBar actionBar = getActionBar();
        actionBar.setSplitBackgroundDrawable(new ColorDrawable(Color.parseColor("#336ebdc4")));

        setContentView(R.layout.activity_main);

        Log.d("TTS", "setup TTS");
        if (initTTS() == false) {
            Toast.makeText(this.getBaseContext(), "TTS Error: no authentication", Toast.LENGTH_LONG).show();
        }
        Log.d("STT", "setup STT");
        if (initSTT() == false) {
            Toast.makeText(this.getBaseContext(), "STT Error: no authentication", Toast.LENGTH_LONG).show();
        }
        //set secretary Image Onclick
        buttonRecord = (ImageView)findViewById(imageId);
        buttonRecord.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                if (mState == ConnectionState.IDLE) {
                    mState = ConnectionState.CONNECTING;
                    Log.d("TTS", "onClickRecord: IDLE -> CONNECTING");
                    mRecognitionResults = "";
                    // start recognition
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... none) {
                            SpeechToText.sharedInstance().recognize(); //開始轉錄
                            return null;
                        }
                    }.execute();
                    setSecretaryImage(imageId, mState);

                }
                else if (mState == ConnectionState.CONNECTED) {
                    mState = ConnectionState.IDLE;
                    Log.d("TTS", "onClickRecord: CONNECTED -> IDLE");
                    SpeechToText.sharedInstance().stopRecognition();

                }
            }
        });

    }

    // initialize the connection to the Watson STT service
    private boolean initSTT() {
        // initialize the connection to the Watson STT service
        String username = getString(R.string.STTdefaultUsername);
        String password = getString(R.string.STTdefaultPassword);
        String tokenFactoryURL = getString(R.string.STTdefaultTokenFactory);
        String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";
        SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS); //壓縮音擋
        SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), getApplicationContext(), sConfig); //時體化
        // Basic Authentication
        SpeechToText.sharedInstance().setCredentials(username, password);
        SpeechToText.sharedInstance().setModel(getString(R.string.modelDefault)); //預設SST語言
        SpeechToText.sharedInstance().setDelegate(this);
        return true;
    }
    private boolean initTTS() {
        String username = getString(R.string.TTSdefaultUsername);
        String password = getString(R.string.TTSdefaultPassword);
        String tokenFactoryURL = getString(R.string.TTSdefaultTokenFactory);
        String serviceURL = "https://stream.watsonplatform.net/text-to-speech/api";
        TextToSpeech.sharedInstance().initWithContext(this.getHost(serviceURL));
        TextToSpeech.sharedInstance().setCredentials(username, password);
        TextToSpeech.sharedInstance().setVoice(getString(R.string.voiceDefault));
        return true;
    }
    public URI getHost(String url){
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    static class MyTokenProvider implements TokenProvider {

        String m_strTokenFactoryURL = null;

        public MyTokenProvider(String strTokenFactoryURL) {
            m_strTokenFactoryURL = strTokenFactoryURL;
        }

        public String getToken() {

            Log.d("TTS", "attempting to get a token from: " + m_strTokenFactoryURL);
            try {
                // DISCLAIMER: the application developer should implement an authentication mechanism from the mobile app to the
                // server side app so the token factory in the server only provides tokens to authenticated clients
                HttpClient httpClient = new DefaultHttpClient();
                HttpGet httpGet = new HttpGet(m_strTokenFactoryURL);
                HttpResponse executed = httpClient.execute(httpGet);
                InputStream is = executed.getEntity().getContent();
                StringWriter writer = new StringWriter();
                IOUtils.copy(is, writer, "UTF-8");
                String strToken = writer.toString();
                Log.d("TTS", strToken);
                return strToken;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }


    /**
     * Play TTS Audio data
     *
     * @param view
     */
    public void playTTS(View view) throws JSONException {

        Log.d("TTS", "playTTS voice");

        String ttsText="But the debate could have bigger consequences than previous arguments over whether Mexico will pay to build a bigger wall at its border or the size of a candidate’s manhood.";

        //Change Image State
        mState = ConnectionState.TALK;
        setSecretaryImage(imageId, mState);

        //Call the sdk function
        TextToSpeech.sharedInstance().synthesize(ttsText);
    }

    /**
     * Change the image src according to mstate
     */
    public void setSecretaryImage(final int imageId, final ConnectionState s) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                int iDrawable = R.drawable.call;
                if(s == ConnectionState.IDLE){
                    iDrawable = R.drawable.call;
                }else if(s == ConnectionState.CONNECTING){
                    iDrawable = R.drawable.wait;
                }else if(s == ConnectionState.CONNECTED){
                    iDrawable = R.drawable.listen;
                }else if(s == ConnectionState.TALK){
                    iDrawable = R.drawable.talk;
                }
                ImageView temp = (ImageView) findViewById(imageId);
                temp.setImageResource(iDrawable);
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    // STT delegages ----------------------------------------------

    public void onOpen() {
        Log.d("TTS", "onOpen");
        displayStatus("successfully connected to the STT service");
        mState = ConnectionState.CONNECTED;
        setSecretaryImage(imageId, mState);
    }

    public void onError(String error) {

        Log.e("TTS", "In onError: "+error);
        mState = ConnectionState.IDLE;
        setSecretaryImage(imageId, mState);
    }

    public void onClose(int code, String reason, boolean remote) {
        Log.d("TTS", "onClose, code: " + code + " reason: " + reason);
        displayStatus("connection closed");
        mState = ConnectionState.IDLE;
        setSecretaryImage(imageId, mState);
    }

    public void onMessage(String message) {

        Log.d("TTS", "onMessage, message: " + message);
        try {
            JSONObject jObj = new JSONObject(message);
            // state message
            if(jObj.has("state")) {
                Log.d("TTS", "Status message: " + jObj.getString("state"));
            }
            // results message
            else if (jObj.has("results")) {
                //if has result
                JSONArray jArr = jObj.getJSONArray("results");
                for (int i=0; i < jArr.length(); i++) {
                    JSONObject obj = jArr.getJSONObject(i);
                    JSONArray jArr1 = obj.getJSONArray("alternatives");
                    String str = jArr1.getJSONObject(0).getString("transcript");
                    // remove whitespaces if the language requires it
                    String model = getString(R.string.modelDefault);
                    if (model.startsWith("ja-JP") || model.startsWith("zh-CN")){
                        str = str.replaceAll(" +","");
                    }
                    String strFormatted = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                    if (obj.getString("final").equals("true")) {
                        String stopMarker = (model.startsWith("ja-JP") || model.startsWith("zh-CN")) ? "。" : ". ";
                        mRecognitionResults += strFormatted.substring(0,strFormatted.length()-1) + stopMarker;
                        Log.d("TTS", "01: "+mRecognitionResults);
                        //displayResult(mRecognitionResults);
                    } else {
                        Log.d("TTS", "02: "+mRecognitionResults + strFormatted);
                        //displayResult(mRecognitionResults + strFormatted);
                    }

                    break;
                }
            } else {
                Log.d("TTS", "03: "+"unexpected data coming from stt server: \n");
                //displayResult("unexpected data coming from stt server: \n" + message);
            }

        } catch (JSONException e) {
            Log.e("TTS", "Error parsing JSON");
            e.printStackTrace();
        }
    }

    public void onAmplitude(double amplitude, double volume) {
        //Logger.e("TTS", "amplitude=" + amplitude + ", volume=" + volume);
    }

    public void displayStatus(final String status) {
            /*final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    TextView textResult = (TextView)mView.findViewById(R.id.sttStatus);
                    textResult.setText(status);
                }
            };
            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();*/
    }
}
