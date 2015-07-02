package com.example.marco.saveme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;


public class MainActivity extends ActionBarActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = "MainActivity";

    Button parti;
    Button stop;

    EditText testo;
    Button invia;

    Button resetDB;
    String messaggio;

    TextView debug;
    String debugStr="";

    private double mLatitude = 0;
    private double mLongitude = 0;

    protected Location mLastLocation;
    protected GoogleApiClient mGoogleApiClient;

    BroadcastReceiver receiver;
    Intent mIntent;
    public static MyDbHandler myDbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        buildGoogleApiClient();
        mIntent = new Intent(this,MyMainService.class);

        myDbHandler=new MyDbHandler(this);

        parti=(Button)findViewById(R.id.parti);
        parti.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(mIntent);
            }
        });

        stop=(Button)findViewById(R.id.stop);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(mIntent);
                LocalBroadcastManager.getInstance(getApplicationContext()).unregisterReceiver(receiver);
            }
        });

        testo=(EditText)findViewById(R.id.textBox);

        invia=(Button)findViewById(R.id.invia);
        invia.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                messaggio=testo.getText().toString();
                testo.setText("");
                mGoogleApiClient.connect();
                //MyMainService.myDbHandler.inserisciNuovoMessaggio(messaggio);

            }
        });

        resetDB=(Button)findViewById(R.id.resetDB);
        resetDB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                myDbHandler.resetDB();
            }
        });

        debug=(TextView)findViewById(R.id.testoDebug);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.e("main", "onReceive");
                String s = intent.getStringExtra(MyMainService.MODIFY_UI);
                debugStr=debugStr+"\n"+s;
                debug.setText(debugStr);
                // do something here.

            }
        };

        //registro il brdRecv per modificare l'interfaccia
        LocalBroadcastManager.getInstance(this).registerReceiver((receiver),
                new IntentFilter(MyMainService.MSG_INTENT)
        );
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

        myDbHandler.inserisciNuovoMessaggio(messaggio,mLatitude,mLongitude);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();


    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

    }


}
