package com.example.marco.saveme;

import android.net.wifi.WifiInfo;
import android.provider.Settings;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by Marco on 10/05/2015.
 */
public class ClientThrd implements Runnable{

    public static final String TAG = " ClientThrd";
    private String serverIpAddress = "192.168.49.1";
    private MyMainService mService;
    DataOutputStream dataOut=null;
    DataInputStream dataIn=null;
    Socket clientSocket=null;
    String messaggioFinale="123";
    ClientThrd cl;


    List<String> listaMessaggiLetti = new ArrayList<>();


    public ClientThrd(MyMainService s){
        mService=s;

    }
    public void run() {
        Log.e(TAG, "partito");
        try {
            String ipString;
            do {
                WifiInfo wifiInfo = mService.wifiManager.getConnectionInfo();
                int ip = wifiInfo.getIpAddress();
                ipString = String.format(
                        "%d.%d.%d.%d",
                        (ip & 0xff),
                        (ip >> 8 & 0xff),
                        (ip >> 16 & 0xff),
                        (ip >> 24 & 0xff));
            }while(ipString.equals("0.0.0.0") && !mService.IAmGO);

            if(!mService.IAmGO){
                Log.d(TAG, "ip = " + ipString);
                clientSocket = new Socket(serverIpAddress, 5050);


                mService.IAmClient=true;
                mService.modificaUI("sono un Client");

                String idDisp = Settings.Secure.getString(mService.getApplicationContext().getContentResolver(),
                        Settings.Secure.ANDROID_ID);

                dataIn=new DataInputStream(clientSocket.getInputStream());
                dataOut=new DataOutputStream(clientSocket.getOutputStream());

                dataOut.writeUTF(idDisp);

                String idServer="";

                idServer=dataIn.readUTF();
                mService.modificaUI("idServer: "+idServer);
                Log.e(TAG,"idserver:   "+idServer);
/*
                Thread gestRead = new Thread(new GestoreRead(dataOut,dataIn,mService,idServer,listaMessaggiLetti));
                gestRead.start();

                gestRead.join();
                Log.d(TAG,"il gestRead è finito; ho letto "+listaMessaggiLetti.size()+" messaggi");
*/

                //vedo quando ho ricevuto l'update da questo dispositivo
                long lastUpdateFromServer=mService.myDbHandler.takeLastUpdate(idServer);
                if(lastUpdateFromServer==-1){
                    Log.e(TAG,"ERROREEEEEEE");
                }
                else{
                    //invio al GO il timestamp
                    dataOut.writeUTF(""+lastUpdateFromServer);
                    //nel server fare prima le read last update e poi il write last update

                    long lastUpdateFromMe;
                    lastUpdateFromMe=Long.parseLong(dataIn.readUTF());
                    Log.d(TAG,"il tempo in cui io ho mandato x l'ultima volta al server: "+lastUpdateFromMe);

                    /*per sapere quando ho cominciato a mandare*/
                    long timestamp=0;

                    timestamp = Long.parseLong(dataIn.readUTF());
                    Log.d(TAG, "timestamp: " + timestamp);

                    Calendar c = Calendar.getInstance();
                    c.setTimeZone(TimeZone.getTimeZone("GMT"));
                    long actualTime=c.getTimeInMillis()/1000;

                    dataOut.writeUTF(""+actualTime);



                    Thread gestRead = new Thread(new GestoreRead(dataIn,mService,idServer,listaMessaggiLetti,timestamp));
                    gestRead.start();

                    Thread gestWrite=new Thread(new GestoreWrite(dataOut,mService,lastUpdateFromMe));
                    gestWrite.start();

                    gestWrite.join();
                    Log.d(TAG,"il gestWrite è finito");
                    gestRead.join();

                    Log.d(TAG,"il gestRead è finito; ho letto "+listaMessaggiLetti.size()+" messaggi e il timestamp è: "+timestamp);
                    mService.modificaUI("ho letto: "+listaMessaggiLetti.size()+" messaggi");

                    /*sta controllo lo faccio nella funz del db
                    if(listaMessaggiLetti.get(listaMessaggiLetti.size()-1).equals(messaggioFinale)){
                        Log.d(TAG,"----->COMUNICAZIONE ESEGUITA CON SUCCESSO<-----");

                    }else{

                    }
                    */


                    //ora ho i messaggi(stringhe) nella listaMessaggiLetti
                    //li passo al db così li memorizza
                    mService.myDbHandler.inserisciMessaggiLetti(listaMessaggiLetti,timestamp,idServer);



                }



                closeSocket();

            }


        }catch (ConnectException e){
            Log.e(TAG,"Catturata eccezione particolare: "+e);


        }
        catch (Exception e){
            Log.e(TAG,"Catturata eccezione: "+e);
            closeSocket();
        }
    }

    public void closeSocket(){

        Log.e("client closeSocket","partita");






        if(dataOut!=null) {
            try {
                dataOut.close();
            } catch (Exception e) {
                Log.e("client closeSocket", "eccezzione: " + e);
            }
        }
        if(dataIn!=null) {
            try {
                dataIn.close();
            } catch (Exception e) {
                Log.e("client closeSocket", "eccezzione: " + e);
            }
        }
        if(clientSocket!=null) {
            try {
                clientSocket.close();
            } catch (Exception e) {
                Log.e("client closeSocket", "eccezzione: " + e);
            }
        }

        mService.diventaGo();

    }
}
