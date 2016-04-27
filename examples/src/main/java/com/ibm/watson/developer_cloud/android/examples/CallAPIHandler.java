package com.ibm.watson.developer_cloud.android.examples;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;


import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.ibm.watson.developer_cloud.alchemy.v1.AlchemyVision;
import com.ibm.watson.developer_cloud.alchemy.v1.model.ImageFaces;
import com.squareup.okhttp.MultipartBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;

/**
 * Created by ser on 2016/3/10.
 */
public class CallAPIHandler {

    private RequestQueue requestQueue;
    private static CallAPIHandler mInstance;
    private Context context;
    public Queue translatedWords = new LinkedList();
    public LinkedList<Person> photoInfo=null;
    OkHttpClient client = new OkHttpClient();
    public static synchronized CallAPIHandler getInstance(Context context) {
        if(mInstance==null){
            mInstance=new CallAPIHandler(context);
        }
        return mInstance;
    }

    public CallAPIHandler(Context context) {
        this.context = context;
    }

    private RequestQueue getRequestQueue() {
        Log.d("requestQueue", "Into GET requestQueue");
        if (requestQueue == null) {
            requestQueue = Volley.newRequestQueue(this.context);
        }

        return requestQueue;
    }

    public void getTraditionalChinese(Context context , String t) {

        if (ifInternetOpen(context)) {

            JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET,"https://www.googleapis.com/language/translate/v2?key=AIzaSyC-5zBxoCmKwp0k7CePGxq47mTCJjRc5zQ&source=zh_cn&target=zh_tw&q="+t,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject arg0) {

                            try {
                                String temp=arg0.getJSONObject("data").getJSONArray("translations").getJSONObject(0).getString("translatedText");
                                Log.d("Call API"," finish translate: "+temp);
                                String translatedWord=temp;
                                translatedWords.offer(translatedWord);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }

                    }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError arg0) {
                    Log.d("getTraditionalChinese","call API error: "+arg0.getMessage());
                }
            });
            getRequestQueue().add(jsonObjectRequest);

        } else {

        Log.d("Internet Error", "user phone didn't open internet.");
    }

}


    public InputStream getWeatherTerms(Context context ,HashMap<String,Double> loc) {
        InputStream temp=null;
        if (ifInternetOpen(context)) {
            Log.d("Call API", "in Weather");
            try {
                //temp=run("https://twcservice.mybluemix.net/api/weather/v2/forecast/daily/10day?units=m&geocode="+loc.get("latitude")+"%2C"+loc.get("longitude")+"&language=en-US");
                temp=weatherun("http://nodered-flow.mybluemix.net/weather?lat="+loc.get("latitude")+"&lng="+loc.get("longitude"));
                /*
                try {
                    JSONObject jObj = new JSONObject(temp);
                    temp = jObj.getJSONArray("forecasts").getJSONObject(0).getString("narrative");
                } catch (JSONException e) {
                    Log.d("Error in weather api",e.getMessage());
                }
             */
            } catch (IOException e) {
                e.printStackTrace();
            }

        } else {

            Log.d("Internet Error", "user phone didn't open internet.");
        }

        return temp;
    }
    public String run(String url) throws IOException {
        //Authenticate認證
        client = new OkHttpClient.Builder()
                .authenticator(new Authenticator() {
                    @Override
                    public okhttp3.Request authenticate(okhttp3.Route route, okhttp3.Response response) throws IOException {
                        System.out.println("Authenticating for response: " + response);
                        System.out.println("Challenges: " + response.challenges());
                        String credential = Credentials.basic("07da5fe6-e4e4-4f3f-a745-f20b961aec2e", "dNvTBcwcW9");
                        return response.request().newBuilder().header("Authorization", credential).build();
                    }
                })
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        okhttp3.Response response = client.newCall(request).execute();

        return response.body().string();

    }

    public InputStream weatherun(String url) throws IOException {
        //Authenticate認證
        client = new OkHttpClient.Builder().build();
        okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
        okhttp3.Response response = client.newCall(request).execute();
        InputStream inputStream= response.body().byteStream();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        byte[] bmp_buffer;
        int len = 0;
        while( (len=inputStream.read(buffer)) != -1){
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        inputStream.close();
        bmp_buffer=outStream.toByteArray();
        InputStream myInputStream = new ByteArrayInputStream(bmp_buffer);
        return myInputStream;

    }

    public void doVisualReconition(final File image){

        new AsyncTask<Void, Void, ImageFaces>(){
            @Override
            protected ImageFaces doInBackground(Void... none) {
                AlchemyVision service = new AlchemyVision();
                service.setApiKey("d43c2219a37e702baa8ef70b2f16b2aae0d8fd9e");
                ImageFaces faces = service.recognizeFaces(image,false);
                return faces;
            }

            @Override
            protected void onPostExecute(ImageFaces faces) {
                super.onPostExecute(faces);
                Log.d("VisualRecognition", "recognizedImage: " + faces);
                JSONObject o=null;
                try {
                    o = new JSONObject(faces.toString());
                    LinkedList temps=new LinkedList<>();
                    JSONArray images=o.getJSONArray("imageFaces");
                    if(images.length()>0){
                        for (int i = 0; i < images.length(); i++) {
                            Person p=new Person();
                            p.setAge(((JSONObject) images.get(0)).getJSONObject("age").getString("ageRange"));
                            p.setAgeScore(((JSONObject) images.get(0)).getJSONObject("age").getString("score"));
                            p.setGender(((JSONObject) images.get(0)).getJSONObject("gender").getString("gender"));
                            p.setGenderScore(((JSONObject) images.get(0)).getJSONObject("gender").getString("score"));
                            temps.add(p);
                        }
                    }
                    photoInfo=temps;

                } catch (JSONException e) {
                    Log.d("VisualRecognition","recognizedImage no JSON");
                    photoInfo=null;
                }
                if(o!=null){

                }

            }
        }.execute();

    }


    public boolean ifInternetOpen(Context context) {

        final ConnectivityManager connMgr = (ConnectivityManager)
                this.context.getSystemService(Context.CONNECTIVITY_SERVICE);

        final android.net.NetworkInfo wifi =
                connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        final android.net.NetworkInfo mobile =
                connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (wifi.isAvailable() || mobile.isAvailable()) {
            return true;
        } else {
            Toast.makeText(context, "請檢查手機網路再試一次", Toast.LENGTH_LONG).show();
            return false;
        }


    }


}
