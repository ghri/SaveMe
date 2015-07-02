package com.example.marco.saveme;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by Marco on 11/05/2015.
 */
public class GestoreWrite implements Runnable{
    public static final String TAG = " GestoreWrite";
    DataOutputStream dataOut;
    MyMainService myMainService;
    long lastUpdateFromMe;
    String messaggioFinale="123";


    List<String> listaMessaggiDaInviare=new ArrayList<>();
    List<String> listaRisposteDaInviare=new ArrayList<>();
    List<String> listaMexFromServerDaInviare=new ArrayList<>();


    public GestoreWrite(DataOutputStream dataOut,MyMainService m,long lastUpdateFromMe){
        super();

        this.dataOut=dataOut;
        myMainService=m;
        this.lastUpdateFromMe=lastUpdateFromMe;

    }
    public void run() {


        Log.e(TAG, "sono partito");

        listaMessaggiDaInviare=myMainService.myDbHandler.takeLastMex(lastUpdateFromMe);
        Log.e(TAG,"listaMexDaInviare: "+listaMessaggiDaInviare.size());

        listaRisposteDaInviare=myMainService.myDbHandler.takeLastRisp(lastUpdateFromMe);
        Log.e(TAG,"listaRispDaInviare: "+listaRisposteDaInviare.size());
        listaMexFromServerDaInviare=myMainService.myDbHandler.takeLastMexFromServer(lastUpdateFromMe);
        Log.e(TAG,"listaMexFromServerDaInviare: "+listaMexFromServerDaInviare.size());

       // Log.e(TAG, "sono partito");
        for(int i=0;i<listaMessaggiDaInviare.size();i++) {
            String tmp = listaMessaggiDaInviare.get(i);
            try{
                Log.d(TAG,"messaggio: "+tmp);
                dataOut.writeUTF(tmp);
                Log.d(TAG,"--- inviato ---");
            }catch (Exception e){
                Log.e("GestoreWrite","eccezione: "+e);
                break;

            }
        }

        for(int i=0;i<listaRisposteDaInviare.size();i++){
            String tmp = listaRisposteDaInviare.get(i);
            try{
                Log.d(TAG,"risposta: "+tmp);
                dataOut.writeUTF(tmp);
                Log.d(TAG,"--- inviato ---");
            }catch (Exception e){
                Log.e("GestoreWrite","eccezione: "+e);
                break;

            }
        }
        for(int i=0;i<listaMexFromServerDaInviare.size();i++){
            String tmp = listaMexFromServerDaInviare.get(i);
            try{
                Log.d(TAG,"mexFromServer: "+tmp);
                dataOut.writeUTF(tmp);
                Log.d(TAG,"--- inviato ---");
            }catch (Exception e){
                Log.e("GestoreWrite","eccezione: "+e);
                break;

            }
        }
        try {
            dataOut.writeUTF(messaggioFinale);
            Log.d("gestoreWrite", "-----mandato mex finale---");
        }catch (Exception e){
            Log.e("gestoreWrite", "-----eccezione mex finale---");
        }



    }
}
