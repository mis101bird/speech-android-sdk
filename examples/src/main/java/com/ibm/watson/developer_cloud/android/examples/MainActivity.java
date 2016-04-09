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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.app.ActionBar;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.StrictMode;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// IBM Watson SDK
import com.ibm.watson.developer_cloud.alchemy.v1.AlchemyVision;
import com.ibm.watson.developer_cloud.alchemy.v1.model.ImageFaces;
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

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

public class MainActivity extends Activity implements ISpeechDelegate{

    //為了避免語音重複接收的問題，製造FLAG
    private static String preCall;

    private static final String TAG = "MainActivity";
    private CallAPIHandler apihelper;
    private HashMap<String,String> pak;
    private GPSLocation location;
    MyRippleBackground rippleBackground;
    private Button dialogButton;
    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED, TALK
    }
    int imageId = R.id.secretary;
    static ConnectionState mState = ConnectionState.IDLE;
    private Handler mHandler = null;
    private static String mRecognitionResults = "";
    ImageView buttonRecord = null;

    //相機辨識
    public final String PhotoTAG = "CallPhoto";
    public final static int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1034;
    public String photoFileName = "photo.jpg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        preCall="";
        apihelper=CallAPIHandler.getInstance(this.getBaseContext());
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

        // construct a new instance of GPSLocation
        location = new GPSLocation(this);

        // if we can't access the location yet
        if (!location.hasLocationEnabled()) {
            // ask the user to enable location access
            GPSLocation.openSettings(this);
        }

        //set secretary Image Onclick
        buttonRecord = (ImageView)findViewById(imageId);
        rippleBackground=(MyRippleBackground)findViewById(R.id.ripple);
        buttonRecord.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

                if (mState == ConnectionState.IDLE) {
                    mState = ConnectionState.CONNECTING;
                    Log.d("STT", "onClickRecord: IDLE -> CONNECTING");
                    mRecognitionResults = "";
                    // start recognition
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... none) {
                            SpeechToText.sharedInstance().recognize(); //開始轉錄
                            return null;
                        }
                    }.execute();
                    setSecretaryImage(imageId, mState);
                    rippleBackground.startRippleAnimation();

                } else if (mState == ConnectionState.CONNECTED) {
                    mState = ConnectionState.IDLE;
                    Log.d("STT", "onClickRecord: CONNECTED -> IDLE");
                    SpeechToText.sharedInstance().stopRecognition();
                    rippleBackground.stopRippleAnimation();
                }
            }
        });
        // get installed app
        PackageManager pmPack;
        pmPack = getPackageManager();
        List packinfo = pmPack.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        pak = new HashMap<>(); //App name, App package
        for ( int i=0; i < packinfo.size(); i++) {
            PackageInfo p = (PackageInfo) packinfo.get(i);
            //Log.d("App", p.applicationInfo.loadLabel(getPackageManager()).toString()+": "+p.packageName);
            pak.put(p.applicationInfo.loadLabel(getPackageManager()).toString(), p.packageName);
        }

    }
    @Override
    protected void onResume() {
        super.onResume();
        // make the device update its location
        location.beginUpdates();
    }

    @Override
    protected void onPause() {
        // stop location updates (saves battery)
        location.endUpdates();
        super.onPause();
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
     */
    public void playTTS(String ttsText) throws JSONException {

        Log.d("TTS", "playTTS voice");
        Log.d("TTS", "playTTS: "+ttsText);
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
            int iDrawable = R.drawable.call;
            @Override
            public void run() {
                ImageView temp = (ImageView) findViewById(imageId);
                if(s == ConnectionState.IDLE){
                    rippleBackground.stopRippleAnimation();
                    iDrawable = R.drawable.call;
                    temp.setImageResource(iDrawable);

                }else if(s == ConnectionState.CONNECTING){
                    rippleBackground.setRippleColor(getResources().getColor(R.color.WaitrippelColor));
                    iDrawable = R.drawable.wait;
                    temp.setImageResource(iDrawable);
                }else if(s == ConnectionState.CONNECTED){
                    rippleBackground.setRippleColor(getResources().getColor(R.color.ListenrippelColor));
                    iDrawable = R.drawable.listen;
                    temp.setImageResource(iDrawable);
                }else if(s == ConnectionState.TALK){
                    /*
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.d("setSecretaryImage","Thread sleep Error 1: "+e.getMessage());
                        e.printStackTrace();
                    }
                    rippleBackground.setRippleColor(getResources().getColor(R.color.TalkrippelColor));
                    iDrawable = R.drawable.talk;
                    temp.setImageResource(iDrawable);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Log.d("setSecretaryImage","Thread sleep Error 2: "+e.getMessage());
                        e.printStackTrace();
                    }
                    rippleBackground.setRippleColor(getResources().getColor(R.color.ListenrippelColor));
                    iDrawable = R.drawable.listen;
                    temp.setImageResource(iDrawable);
                    */
                    mState=ConnectionState.CONNECTED;
                }

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
        Log.d("STT", "onOpen");
        displayStatus("successfully connected to the STT service");
        mState = ConnectionState.CONNECTED;
        setSecretaryImage(imageId, mState);
    }

    public void onError(String error) {

        Log.e("STT", "In onError: " + error);
        mState = ConnectionState.IDLE;
        setSecretaryImage(imageId, mState);
    }

    public void onClose(int code, String reason, boolean remote) {
        Log.d("STT", "onClose, code: " + code + " reason: " + reason);
        displayStatus("connection closed");
        mState = ConnectionState.IDLE;
        setSecretaryImage(imageId, mState);
    }

    public void onMessage(String message) {

        Log.d("STT", "onMessage, message: " + message);
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
                /*
                JSONObject jArr = jObj.getJSONArray("results").getJSONObject(0);
                JSONArray jArr1 = jArr.getJSONArray("alternatives");
                mRecognitionResults = jArr1.getJSONObject(0).getString("transcript").trim();

                Log.d("TTS", "To checkTerm: "+mRecognitionResults);
                checkTerms(mRecognitionResults,this.getBaseContext());
                 */

                for (int i=0; i < jArr.length(); i++) {
                    JSONObject obj = jArr.getJSONObject(i);
                    JSONArray jArr1 = obj.getJSONArray("alternatives");
                    String str = jArr1.getJSONObject(0).getString("transcript").trim();
                    // remove whitespaces if the language requires it
                    Log.d("TTS", "To checkTerm: " + mRecognitionResults);
                    String model = getString(R.string.modelDefault);
                    if (model.startsWith("ja-JP") || model.startsWith("zh-CN")){
                        str = str.replaceAll(" +","");
                    }
                    if(obj.getString("final").equals("true")) {
                        mRecognitionResults = str;
                        if (mRecognitionResults != null || mRecognitionResults != "") {
                            displayStatus("get "+mRecognitionResults);
                            checkTerms(mRecognitionResults, this.getBaseContext());
                        }
                    }

                    /*
                    if (model.startsWith("ja-JP") || model.startsWith("zh-CN")){
                        str = str.replaceAll(" +","");
                    }
                    String strFormatted = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                    if (obj.getString("final").equals("true")) {
                        Log.d("TTS", "01: "+mRecognitionResults);

                        //displayResult(mRecognitionResults);
                    } else {
                        Log.d("TTS", "02: " + strFormatted);
                    }
                    */
                    break;
                }
            } else {
                Log.d("STT", "03: "+"unexpected data coming from stt server: \n");
                //displayResult("unexpected data coming from stt server: \n" + message);
            }

        } catch (JSONException e) {
            Log.e("STT", "Error parsing JSON");
            e.printStackTrace();
        }
    }

    public void onAmplitude(double amplitude, double volume) {
        //Log.d("TTS", "amplitude=" + amplitude + ", volume=" + volume);
    }

    public void displayStatus(final String status) {
            final Runnable runnableUi = new Runnable(){
                @Override
                public void run() {
                    TextView textResult = (TextView)findViewById(R.id.Status);
                    textResult.setText(status);
                }
            };
            new Thread(){
                public void run(){
                    mHandler.post(runnableUi);
                }
            }.start();
    }

    private void checkTerms(final String temp ,final Context context){
        //String ar[] = temp.split("。");
        //final String simpleChinese=ar[ar.length-1];
        Log.d("checkTerms", "get: " + temp);
        if(!temp.equals(preCall) && temp!=null){
            //Toast.makeText(this.getBaseContext(),temp,Toast.LENGTH_LONG).show();
            // start recognition
            preCall=temp;
            new AsyncTask<Void, Void, String>(){
                @Override
                protected String doInBackground(Void... none) {
                    Object ans="";
                    String s=null;
                    boolean sendcall=false;
                    while(true){

                        if(sendcall==false){
                            apihelper.getTraditionalChinese(context, temp);
                            sendcall=true;
                        }else{
                            if( ( ans= apihelper.translatedWords.poll())!= null){
                                s = (String)ans;
                                displayStatus("translate to "+s);
                                Log.d("checkTerms", "get 回傳: " + s);
                                break;
                            }else if(mState==ConnectionState.IDLE){
                                break;
                            }
                        }
                    }
                    return s;
                }

                @Override
                protected void onPostExecute(String t) {
                    super.onPostExecute(t);
                    //作命令辨識
                    if (t.equals("天氣")) {
                        weather();
                    } else if (t.equals("相機")) {
                        String path=getPhotoFileUri(photoFileName).getPath();
                        Log.d("take picture", getPhotoFileUri(photoFileName).getPath());
                        SpeechToText.sharedInstance().stopRecognition();
                        rippleBackground.stopRippleAnimation();
                        onLaunchCamera(path);
                    }else if (pak.containsKey(t)) {
                        if (!openApp(context, pak.get(t))) {
                            Log.d("call App", "Intent Error");
                        }
                    }

                }
            }.execute();
        }else{
            preCall="";
        }

    }
    private void visualRecognition(String path){
        final File ans=getSmallImageFile(this.getBaseContext(), path, 600, 600, true);
        displayStatus("Face Recognition..." + ans.getAbsolutePath());

        uploadImage(ans.getAbsolutePath());

        /*
        new AsyncTask<Void, Void, ImageFaces>(){
            @Override
            protected ImageFaces doInBackground(Void... none) {
                AlchemyVision service = new AlchemyVision();
                service.setApiKey("d43c2219a37e702baa8ef70b2f16b2aae0d8fd9e");
                ImageFaces faces = service.recognizeFaces(ans,false);
                return faces;
            }

            @Override
            protected void onPostExecute(ImageFaces faces) {
                super.onPostExecute(faces);
                Log.d("VisualRecognition", "recognizedImage: " + faces);
                JSONObject o=null;
                try {
                    o = new JSONObject(faces.toString());
                    JSONArray images=o.getJSONArray("imageFaces");
                    String t1="total "+images.length()+" people\n";
                    if(images.length()>0){
                        for (int i = 0; i < images.length(); i++) {
                            t1 += "("+(i+1)+"): "+images.getJSONObject(i).getJSONObject("age").getString("ageRange")+", 性別: "+images.getJSONObject(i).getJSONObject("gender").getString("gender")+"\n";
                        }
                    }else{
                            t1+="Please try again.";
                    }
                    setDialog("人臉辨識結果", t1);
                    //displayStatus(t1);

                } catch (JSONException e) {
                    Log.d("VisualRecognition","recognizedImage no JSON");
                    displayStatus("recognizedImage no JSON");
                }
                if(o!=null){

                }

            }
        }.execute();
        */
    }

    private void weather(){
        final Context context=this.getBaseContext();
        // make the device update its location

        final HashMap<String,Double> loc=getlocation();
        new AsyncTask<Void, Void, String>(){
            @Override
            protected String doInBackground(Void... none) {
                Object ans="";
                String s=null;
                /*
                boolean sendcall=false;
                while(true){
                    if(sendcall==false){
                        apihelper.getWeatherTerms(context, loc);
                        sendcall=true;
                    }else{
                        if( ( ans= apihelper.translatedWords.poll())!= null ){
                            s = (String)ans;
                            Log.d("weather","get 回傳: "+s);
                            break;
                        }
                    }
                }*/
                return apihelper.getWeatherTerms(context, loc);
            }

            @Override
            protected void onPostExecute(String weather) {
                super.onPostExecute(weather);
                try {
                    playTTS(weather);
                    displayStatus("Speak: "+weather);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }.execute();

    }

    /** Open another app.
     * @param context current Context, like Activity, App, or Service
     * @param packageName the full package name of the app to open
     * @return true if likely successful, false if unsuccessful
     */
    private boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        Intent i = manager.getLaunchIntentForPackage(packageName);
        if (i == null) {
            return false;
        }
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        context.startActivity(i);
        return true;
    }
    public HashMap<String,Double> getlocation() {
        final double latitude = location.getLatitude();
        final double longitude = location.getLongitude();
        HashMap<String,Double> spot=new HashMap<>();
        spot.put("latitude", latitude);
        spot.put("longitude", longitude);
        return  spot;
    }
    public static String getExternalSdCardPath() {
        String path = null;

        File sdCardFile = null;
        List<String> sdCardPossiblePath = Arrays.asList("external_sd", "ext_sd", "external", "extSdCard");

        for (String sdPath : sdCardPossiblePath) {
            File file = new File("/mnt/", sdPath);

            if (file.isDirectory() && file.canWrite()) {
                path = file.getAbsolutePath();

                String timeStamp = new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date());
                File testWritable = new File(path, "ibm_secretary_camera" + timeStamp);

                if (testWritable.mkdirs()) {
                    testWritable.delete();
                }
                else {
                    path = null;
                }
            }
        }

        if (path != null) {
            sdCardFile = new File(path);
        }
        else {
            sdCardFile = new File(Environment.getExternalStorageDirectory().getAbsolutePath());
        }

        return sdCardFile.getAbsolutePath();
    }

    //圖片要壓縮上傳並回傳File
    public File getSmallImageFile(Context cxt, String filePath, int width, int height,
                                         boolean isAdjust) {
        displayStatus("compress image.");
        Bitmap bitmap = reduce(BitmapFactory.decodeFile(filePath), width, height, isAdjust);

        File file = new File(getRandomFileName(cxt.getCacheDir().getPath()));

        BufferedOutputStream outputStream = null;
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
            bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream);
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    public static String getRandomFileName(String filePath) {
        SimpleDateFormat format = new SimpleDateFormat("MMddHHmmss",
                Locale.getDefault());
        Date date = new Date();
        String key = format.format(date);

        Random r = new Random();
        key = key + r.nextInt();
        key = key.substring(0, 15);
        Log.d("Compressed picture", filePath + "/" + key + ".jpeg");
        return filePath + "/" + key + ".jpeg";
    }

    public static Bitmap reduce(Bitmap bitmap, int width, int height, boolean isAdjust) {
        // 如果想要的宽度和高度都比源图片小，就不压缩了，直接返回原图
        if (bitmap.getWidth() < width && bitmap.getHeight() < height) {
            return bitmap;
        }
        if (width == 0 && height == 0) {
            width = bitmap.getWidth();
            height = bitmap.getHeight();
        }

        // 根据想要的尺寸精确计算压缩比例, 方法详解：public BigDecimal divide(BigDecimal divisor, int scale, int
        // roundingMode);
        // scale表示要保留的小数位, roundingMode表示如何处理多余的小数位，BigDecimal.ROUND_DOWN表示自动舍弃
        float sx = new BigDecimal(width).divide(new BigDecimal(bitmap.getWidth()), 4, BigDecimal
                .ROUND_DOWN).floatValue();
        float sy = new BigDecimal(height).divide(new BigDecimal(bitmap.getHeight()), 4,
                BigDecimal.ROUND_DOWN).floatValue();
        if (isAdjust) {// 如果想自动调整比例，不至于图片会拉伸
            sx = (sx < sy ? sx : sy);
            sy = sx;// 哪个比例小一点，就用哪个比例
        }
        Matrix matrix = new Matrix();
        matrix.postScale(sx, sy);// 调用api中的方法进行压缩，就大功告成了
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);
    }

    public void onLaunchCamera(String path) {

        String storageState = Environment.getExternalStorageState();
        if(storageState.equals(Environment.MEDIA_MOUNTED)) {

            File _photoFile = new File(path);
            try {
                if(_photoFile.exists() == false) {
                    _photoFile.getParentFile().mkdirs();
                    _photoFile.createNewFile();
                }

            } catch (IOException e) {
                Log.d(TAG, "Could not create file.", e);
            }
            Log.i(TAG, path);

            Uri _fileUri = Uri.fromFile(_photoFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE );
            intent.putExtra( MediaStore.EXTRA_OUTPUT, _fileUri);
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        }   else {
            displayStatus("External Storeage (SD Card) is required.\nCurrent state: " + storageState);
        }

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                String path=getPhotoFileUri(photoFileName).getPath();
                visualRecognition(path);
            } else { // Result was a failure
                displayStatus("taken photo Error.");
            }
        }
    }

    // Returns the Uri for a photo stored on disk given the fileName
    public Uri getPhotoFileUri(String fileName) {
        // Only continue if the SD Card is mounted
        if (isExternalStorageAvailable()) {
            // Get safe storage directory for photos
            // Use `getExternalFilesDir` on Context to access package-specific directories.
            // This way, we don't need to request external read/write runtime permissions.
            File mediaStorageDir = new File(
                    getExternalFilesDir(Environment.DIRECTORY_PICTURES), "photo");

            // Create the storage directory if it does not exist
            if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()){
                Log.d("photo", "failed to create directory");
            }

            // Return the file target for the photo based on filename
            return Uri.fromFile(new File(mediaStorageDir.getPath() + File.separator + fileName));
        }
        return null;
    }

    // Returns true if external storage for photos is available
    private boolean isExternalStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return state.equals(Environment.MEDIA_MOUNTED);
    }

    private void setDialog(String title, String content){
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(content)
                .setPositiveButton("準", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setNegativeButton("不準", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                }).show();
    }

    public void uploadImage(String name) {

        try {
            Bitmap bm = BitmapFactory.decodeFile(name);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 100, baos); //bm is the bitmap object
            byte[] b = baos.toByteArray();
            String encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
            /*
            MultipartBody.Builder buildernew = new MultipartBody.Builder();
            final MediaType MEDIA_TYPE_JPG = MediaType.parse("image/jpeg");

            RequestBody req = buildernew.setType(MultipartBody.FORM)
                    .addFormDataPart("name", "photo.jpg", RequestBody.create(MEDIA_TYPE_JPG, file)).build();
*/
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url("http://nodered-flow.mybluemix.net/photo?photo=" + encodedImage)
                    .build();

            OkHttpClient client = new OkHttpClient();
            okhttp3.Response response = client.newCall(request).execute();

            Log.d("face response", "uploadImage:"+response.body().string());

            JSONObject o=null;

            try {
                o = new JSONObject(response.body().string());
                JSONArray images=o.getJSONArray("imageFaces");
                String t1="total "+images.length()+" people\n";
                if(images.length()>0){
                    for (int i = 0; i < images.length(); i++) {
                        t1 += "("+(i+1)+"): "+images.getJSONObject(i).getJSONObject("age").getString("ageRange")+", 性別: "+images.getJSONObject(i).getJSONObject("gender").getString("gender")+"\n";
                    }
                }else{
                    t1+="Please try again.";
                }
                setDialog("人臉辨識結果", t1);
                //displayStatus(t1);

            } catch (JSONException e) {
                Log.d("VisualRecognition","recognizedImage no JSON");
                displayStatus("recognizedImage no JSON");
            }
            if(o!=null) {
            }

        } catch (UnknownHostException | UnsupportedEncodingException e) {
            Log.e("Image", "Error: " + e.getLocalizedMessage());
        } catch (Exception e) {
            Log.e("Image", "Other Error: " + e.getLocalizedMessage());
        }
    }

}
