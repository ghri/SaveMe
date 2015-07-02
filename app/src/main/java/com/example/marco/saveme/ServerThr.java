package com.example.marco.saveme;

import android.provider.Settings;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by Marco on 11/05/2015.
 */
public class ServerThr extends Thread {
    public static final String TAG = "serverThrd";

    private ServerSocket serverSocket;
    public static final int SERVERPORT = 5050;
    MyMainService mService;
    MyDbHandler dbHandler;
    String idDisp;

    public ServerThr(MyMainService m){

        mService=m;
        idDisp = Settings.Secure.getString(mService.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

    }

    public void run(){
        Log.e(TAG, "server cominciato ");

        if(mService.myDbHandler!=null){
            dbHandler=mService.myDbHandler;
        }

        try {

            serverSocket = new ServerSocket(SERVERPORT);
            while (mService.IAmGO) {

                Socket client = serverSocket.accept();
                Thread gest = new Thread(new GestoreClient(client));
                gest.start();
            }
        }catch (SocketException e){
           /* if(e.getCause().equals("Socket closed")){
                Log.e("serverThr","il socket è stato chiuso -> non faccio niente");
            }*/
            Log.e(TAG,"SocketException eccezione: "+e);
        }
        catch (Exception e){
            Log.e(TAG,"eccezione: "+e);
            closeMYSocket();
        }
    }
    public void closeMYSocket(){

        try {
            Log.e("closeSocket","sto x chiudere tutto");
            if(serverSocket!=null)
                serverSocket.close();
        } catch (IOException e) {
            Log.e(TAG,"errore " +e);
            //e.printStackTrace();
        }

    }


    public class GestoreClient implements Runnable{
        String funz="GestoreClient";
        Socket client;
        DataOutputStream dataOut=null;
        DataInputStream dataIn=null;
        List<String> listaMessaggiLetti = new ArrayList<>();

        public GestoreClient(Socket c){
            super();
            client=c;

        }
        public void run() {
            Log.e("gestoreClient", "so partito");
            listaMessaggiLetti.clear();
            try {
                dataIn=new DataInputStream(client.getInputStream());
                dataOut=new DataOutputStream(client.getOutputStream());

                dataOut.writeUTF(idDisp);

                String idClient="";

                idClient=dataIn.readUTF();

                mService.modificaUI("idClient: "+idClient);
                Log.e("gestoreCl","idClient: "+idClient);


                long lastUpdateFromClient=mService.myDbHandler.takeLastUpdate(idClient);
                if(lastUpdateFromClient==-1){
                    Log.e(TAG,"ERROREEEEEEE");
                }
                else{
                    //invio al GO il timestamp
                    dataOut.writeUTF(""+lastUpdateFromClient);


                    long lastUpdateFromMe;
                    lastUpdateFromMe=Long.parseLong(dataIn.readUTF());
                    Log.d(funz,"il tempo in cui io ho mandato x l'ultima volta al client: "+lastUpdateFromMe);
                    //DEVO PRENDERE TUTTI I MESSAGGI CHE HANNO TIMESTAMP DI RICEZIONE > LASTUPDATEFROMME



                    /*
                    Thread gestRead = new Thread(new GestoreRead(dataOut,dataIn,mService,idServer,listaMessaggiLetti));
                    gestRead.start();

                    gestRead.join();
                    Log.d(TAG,"il gestRead è finito; ho letto "+listaMessaggiLetti.size()+" messaggi");
                    */


                    /*per sapere quando ho cominciato a mandare*/


                    Calendar c = Calendar.getInstance();
                    c.setTimeZone(TimeZone.getTimeZone("GMT"));
                    long actualTime=c.getTimeInMillis()/1000;



                    dataOut.writeUTF(""+actualTime);

                    long timestamp=0;

                    timestamp = Long.parseLong(dataIn.readUTF());
                    Log.d(TAG, "timestamp: " + timestamp);


                    Thread gestRead = new Thread(new GestoreRead(dataIn,mService,idClient,listaMessaggiLetti,timestamp));
                    gestRead.start();

                    Thread gestWrite=new Thread(new GestoreWrite(dataOut,mService,lastUpdateFromMe));
                    gestWrite.start();

                    gestWrite.join();
                    Log.d(TAG,"il gestWrite è finito");
                    gestRead.join();

                    Log.d(TAG,"il gestRead è finito; ho letto "+listaMessaggiLetti.size()+" messaggi e il timestamp è: "+timestamp);
                    mService.modificaUI("ho letto: "+listaMessaggiLetti.size()+" messaggi");
                    //todo trovare un modo di memorizzare i messaggi
                    //vedere se va bene memorizzarli subito o quando finisce il server
                    //vedere se si devono fare due tipi di memorizzazione dipende se server con conn diretta o meno

                    //per adesso li memorizzo qua
                    //non ci dovrebbero essere problemi

                    mService.myDbHandler.inserisciMessaggiLetti(listaMessaggiLetti,timestamp,idClient);

                }



            }catch (Exception e){
                Log.e(TAG,"eccezione: "+e);
            }
        }
    }
}
