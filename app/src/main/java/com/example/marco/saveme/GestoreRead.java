package com.example.marco.saveme;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.util.List;

/**
 * Created by Marco on 11/05/2015.
 */
public class GestoreRead implements Runnable{
    public static final String TAG = " GestoreRead";
    String messaggioFinale="123";

    DataOutputStream dataOut=null;
    DataInputStream dataIn=null;
    MyMainService myMainService;
    String idAltroDisp;
    List<String> listaMessaggiLetti;

    public GestoreRead(DataInputStream dataIn,MyMainService m,String idAltroDisp,List<String> listaMessaggiLetti,long timestamp){
        super();

        this.dataIn=dataIn;
        myMainService=m;
        this.idAltroDisp=idAltroDisp;
        this.listaMessaggiLetti=listaMessaggiLetti;
    }
    public void run() {
        try {
            String messaggio="";


            //mi devo mettere in attesa dei messaggi;
            // i messaggi saranno delle stringhe ti testo del tipo mit/timestamp/adsf/asdf/asdf


            while((messaggio.equals("") || !messaggio.equals(messaggioFinale))){
                messaggio=dataIn.readUTF();
                Log.e(TAG,"messaggio: "+messaggio);
                listaMessaggiLetti.add(messaggio);
                if(messaggio.equals(messaggioFinale))
                    Log.e(TAG,"letto mex finale");

            }
            Log.e(TAG,"sono uscito dal ciclo");

        }catch (Exception e){
            Log.e("GestoreRead","eccezione: "+e);
        }
    }
}
