package com.example.marco.saveme;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Marco on 12/05/2015.
 */
public class ServiceCommunicateWithServer extends Service implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    public static final String TAG = " ServComWithServer";

    String url_server = "http://ltw1528.web.cs.unibo.it/create_mex.php";
    String url_server_takeRisp = "http://ltw1528.web.cs.unibo.it/takeMexFromServer.php";

    ScheduledExecutorService scheduleTaskExecutor;

    List<String> listaMessaggi;
    List<String> listaRisposte;

    MyDbHandler dbH;

    int tempoCiclo=1*60;
    int tempoIniziale=40;

    private double mLatitude = 0;
    private double mLongitude = 0;

    protected Location mLastLocation;
    protected GoogleApiClient mGoogleApiClient;

    SharedPreferences sharedPreferences;
    public static final String lastUpdateFromServer="lastTimestamp";
    public static final String MyPREFERENCES = "MyPrefs" ;

    long actualTime;

    public ServiceCommunicateWithServer(){

    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, " onCreate");
        buildGoogleApiClient();

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //super.onCreate();
        Log.e(TAG, " onStartCommand");

        scheduleTaskExecutor= Executors.newScheduledThreadPool(5);
        dbH=MainActivity.myDbHandler;


        listaMessaggi=new ArrayList<>();
        listaRisposte=new ArrayList<>();
        listaMessaggi.clear();
        listaRisposte.clear();

        scheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG,"schedule routine tempo scaduto");
                prendiRisposte();
                mGoogleApiClient.connect();
                Log.e(TAG,"routine finita");

            }
        },tempoIniziale,tempoCiclo, TimeUnit.SECONDS);

        return Service.START_NOT_STICKY;
    }

    public void prendiRisposte() {
        String funz = "prendiRisposte";
        listaMessaggi=dbH.takeLastMex(0);//in questo modo prendo tutti i messaggi

        //ora ho la lista dei messaggi sotto forma di stringa

        for(int i=0;i<listaMessaggi.size();i++){
            String tmp=listaMessaggi.get(i);
            String[] parti=tmp.split("/");

            // Building Parameters
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("mex",parti[3]));
            params.add(new BasicNameValuePair("idMex",parti[1]));
            params.add(new BasicNameValuePair("lat",""+parti[4]));
            params.add(new BasicNameValuePair("lng",""+parti[5]));

            new InviaMessaggio(i,params).execute();

        }
    }
    class InviaMessaggio extends AsyncTask<String, String, String> {
        List<NameValuePair> params;
        int indice;
        public InviaMessaggio(int i,List<NameValuePair> params){
            this.params=params;
            indice=i;
        }

        @Override
        protected String doInBackground(String... uri) {
            String responseString = null;

            Log.e("RequestTask","do in back");
            responseString= makePost(params);
            Log.e("RequestTask","dopo makePost; response: "+responseString);




            return responseString;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            JSONObject jObj = null;
            //Do anything with response..
            try {
                jObj = new JSONObject(result);
                Log.i(TAG,"json: "+ jObj);
            }
            catch (Exception e) {
                Log.e(TAG,"eccezione: "+e);
            }
            int success=-1;
            String testoMex="";
            String idMex="";
            try{
                if(jObj!=null) {
                    Log.e(TAG, "provo a prendere success: " + jObj.get("success"));

                    success = jObj.getInt("success");
                    testoMex = jObj.getString("testo");
                    idMex = jObj.getString("idMex");
                }

            }catch (Exception e){
                Log.e(TAG,"provo a prendere success eccezione: "+e);
            }
            if(success==1){
                //il mex è stato inserito correttamente nel server
                //genero il mex risposta
                Log.e(TAG,"TUTTO è STATO UN SUCCESSO");
                Log.e(TAG,"testo: "+testoMex);
                Log.e(TAG,"idMex: "+idMex);

                //inserisco la risposta del server nel db
                dbH.inserisciNuovaRisposta(testoMex,idMex);
                String myId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                        Settings.Secure.ANDROID_ID);
                String[] parti = idMex.split("_");
                if(parti[0].equals(myId)) {
                    NotificationCompat.Builder mBuilder =
                            new NotificationCompat.Builder(getApplicationContext())
                                    .setSmallIcon(R.drawable.ic_launcher)
                                    .setContentTitle("My notification")
                                    .setContentText("Il server ha ricevuto il tuo messaggio")
                                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
                    NotificationManager mNotifyMgr =
                            (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
                    // Builds the notification and issues it.

                    mNotifyMgr.notify(Integer.parseInt(parti[1]), mBuilder.build());
                }




            }
            else{
                Log.e(TAG,"NON HO INSERITO IL MESSAGGIO");
            }




        }
    }
    public String makePost( List<NameValuePair> params){

        StringBuilder builder = new StringBuilder();
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url_server);
            httpPost.setEntity(new UrlEncodedFormEntity(params));

            HttpResponse httpResponse = httpClient.execute(httpPost);
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();

            if (statusCode == 200) {
                HttpEntity entity = httpResponse.getEntity();
                InputStream content = entity.getContent();
                BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                String line;
                while ((line = reader.readLine()) != null) {
                    //Log.e("lettura","line: "+line);
                    builder.append(line);
                }

            } else {
                Log.e(TAG, "Failed to download file");
            }
        }catch (Exception e){
            Log.e("makePost","catturata eccezione: "+e);
        }
        return builder.toString();
    }


    class PrendiMessaggiServer extends AsyncTask<String, String, String> {
        public PrendiMessaggiServer(){

        }
        @Override
        protected String doInBackground(String... uri) {

            String funz="doInBackground";
            String responseString = null;

            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("lat",""+mLatitude));
            params.add(new BasicNameValuePair("lng",""+mLongitude));



            Calendar c = Calendar.getInstance();
            c.setTimeZone(TimeZone.getTimeZone("GMT"));
            actualTime=c.getTimeInMillis()/1000;

            sharedPreferences = getSharedPreferences(MyPREFERENCES, Context.MODE_PRIVATE);
            long timestamp=sharedPreferences.getLong(lastUpdateFromServer, 0);
            Log.e(TAG,"ts: "+timestamp);


            params.add(new BasicNameValuePair("timestamp",""+timestamp));

            StringBuilder builder = new StringBuilder();

            HttpClient httpclient = new DefaultHttpClient();
            String paramsString= URLEncodedUtils.format(params, "UTF-8");
            Log.e(funz,"stringa: "+url_server_takeRisp+"?"+paramsString);
            HttpGet httpGet=new HttpGet(url_server_takeRisp+"?"+paramsString);

            HttpResponse response;

            try {
                response = httpclient.execute(httpGet);
                StatusLine statusLine = response.getStatusLine();
                if(statusLine.getStatusCode() == HttpStatus.SC_OK){
                    HttpEntity entity = response.getEntity();
                    InputStream content = entity.getContent();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(content));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        //Log.e("lettura","line: "+line);
                        builder.append(line);
                    }
                }else {
                    Log.e(TAG, "Failed to download file");
                }
            } catch (Exception e){
                Log.e("makePost","catturata eccezione: "+e);
            }
            // Log.e(funz,builder.toString());
            return builder.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            String funz="onPostExecute";
            super.onPostExecute(result);
            // Log.e(funz,result);
            //abbiamo il json
            JSONArray jArr = null;
            String idMex;
            String testoMex;

            try {
                jArr = new JSONArray(result);
                Log.i(TAG,"json: "+ jArr);
                if(jArr.length()!=0) {
                    if (jArr.getJSONObject(0).getString("id_mex").equals("0")) {
                        Log.e(funz, "ERRORE CONNESSIONE O ELEMENTI MANCANTI");
                    } else {
                        for (int i = 0; i < jArr.length(); i++) {
                            JSONObject json_data = jArr.getJSONObject(i);
                            idMex = json_data.getString("id_mex");
                            if (idMex.equals("0")) {//si dovrebbe cancellare
                                Log.e(funz, "ERRORE CONNESSIONE O ELEMENTI MANCANTI");

                            } else {
                                testoMex = json_data.getString("testo");
                                Log.e(funz, "idMex: " + idMex + " testo: " + testoMex);
                                dbH.inserisciNuovoMexFromServer(testoMex, idMex);

                                String myId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                                        Settings.Secure.ANDROID_ID);
                                String[] parti = idMex.split("_");
                                if(parti[0].equals(myId)) {
                                    NotificationCompat.Builder mBuilder =
                                            new NotificationCompat.Builder(getApplicationContext())
                                                    .setSmallIcon(R.drawable.ic_launcher)
                                                    .setContentTitle("My notification")
                                                    .setContentText("Qualcuno sta venendo a salvarti")
                                                    .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
                                    NotificationManager mNotifyMgr =
                                            (NotificationManager) getSystemService(getApplicationContext().NOTIFICATION_SERVICE);
                                    // Builds the notification and issues it.

                                    mNotifyMgr.notify(Integer.parseInt(parti[1]), mBuilder.build());
                                }
                            }
                        }

                        SharedPreferences.Editor editor = getSharedPreferences(MyPREFERENCES, MODE_PRIVATE).edit();
                        Log.e(TAG,"actual time: "+actualTime);
                        editor.putLong(lastUpdateFromServer, actualTime);
                        editor.commit();
                    }
                }
            }
            catch (Exception e) {
                Log.e(TAG,"eccezione: "+e);
            }

        }
    }


    /**
     * Builds a GoogleApiClient. Uses the addApi() method to request the LocationServices API.
     */
    protected synchronized void buildGoogleApiClient() {
        Log.e("buildGoogleApiClient","chiamata");
        mGoogleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }
    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        // Provides a simple way of getting a device's location and is well suited for
        // applications that do not require a fine-grained location and that do not need location
        // updates. Gets the best and most recent location currently available, which may be null
        // in rare cases when a location is not available.
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {

            Log.d("onConnected", "lat=" + mLastLocation.getLatitude() + "      lng=" + mLastLocation.getLongitude());

            mLatitude=mLastLocation.getLatitude();
            mLongitude=mLastLocation.getLongitude();






            /*mLatitudeText.setText(String.valueOf(mLastLocation.getLatitude()));
            mLongitudeText.setText(String.valueOf(mLastLocation.getLongitude()));*/
        } else {
            Log.e("onConnected","nessuna location disponibile!!!");

            //Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_LONG).show();
        }

        new PrendiMessaggiServer().execute();

        mGoogleApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in
        // onConnectionFailed.
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode());
    }


    @Override
    public void onConnectionSuspended(int cause) {
        // The connection to Google Play services was lost for some reason. We call connect() to
        // attempt to re-establish the connection.
        Log.i(TAG, "Connection suspended");
        mGoogleApiClient.connect();
    }

    /***
     *
     * fine funz geolocalizzazione
     *
     * **/


    @Override
    public void onDestroy() {
        String funz = "onDestroy";
        Log.e(TAG, "onDestroy");
        scheduleTaskExecutor.shutdown();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }


}
