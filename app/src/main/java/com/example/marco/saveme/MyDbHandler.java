package com.example.marco.saveme;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.android.gms.games.internal.game.GameSearchSuggestionRef;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by Marco on 10/05/2015.
 */
public class MyDbHandler extends SQLiteOpenHelper {
    private static final String TAG = "DB";
    private static final int DATABASE_VERSION = 1;
    public static  final String DATABASE_NAME = "myDB.db";



    public static final String LAST_GOS_TABLE_NAME= "ultimi_GO";
    public static final String LAST_GOS_COLUMN_ID= "_id";
    public static final String LAST_GOS_NOME_NET= "nome_net";


    public static final String MEX_TABLE_NAME="messaggi";
    public static final String MEX_COLUMN_ID_DB="id";
    public static final String MEX_COLUMN_ID_MEX="id_mex";
    //public static final String MEX_COLUMN_MITTENTE="mittente"; non dovrebbe servire
    public static final String MEX_COLUMN_TESTO="testo";
    public static final String MEX_COLUMN_TIMESTAMP_ORIGINE="timestampOrigin"; //è il momento in cui è stato salvato sul dispositivo da cui è stato digitato
    public static final String MEX_COLUMN_TIMESTAMP_RICEZIONE="timestampRic";
    public static final String MEX_COLUMN_LAT="lat";
    public static final String MEX_COLUMN_LNG="lng";

    public static final String RISP_SERVER_TABLE_NAME="rispServer";
    public static final String RISP_SERVER_ID_DB="id";
    public static final String RISP_SERVER_ID_MEX="idMex";
    public static final String RISP_SERVER_TESTO="testo";
    public static final String RISP_SERVER_TIMESTAMP_RICEZIONE="timestampRic";

    public static final String MEX_FROM_SERVER_TABLE_NAME="mexFromServer";
    public static final String MEX_FROM_SERVER_ID_DB="id";
    public static final String MEX_FROM_SERVER_ID_MEX="idMex";
    public static final String MEX_FROM_SERVER_TESTO="testo";
    public static final String MEX_FROM_SERVER_TIMESTAMP_RICEZIONE="timestampRic";


    public static final String HO_RICEVUTO_DA_TABLE_NAME="hoRicevutoDa";
    public static final String HO_RICEVUTO_DA_ID_DB="id";
    public static final String HO_RICEVUTO_DA_ID_DISP="idDisp";
    public static final String HO_RICEVUTO_DA_TIMESTAMP="timestampRecv";


    //TODO fare tab messaggi di salvataggio

    public static final int numero_go_mem=10;
    private MainActivity mainAct;

    String separatore="/";
    int numPartiMessaggio=6;
    int numPartiRisposta=3;
    int numPartiMexFromServer=3;
    // TIPO/ID_MEX/TIMESTAMP_ORIGINE/TESTO/LAT/LNG
    String messaggioFinale="123";



    public MyDbHandler(MainActivity m){
        super(m.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
        mainAct=m;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String funz="onCreate";

        Log.e(funz, "chiamata");

        /*CREO TABELLA LAST GO*/
        String CREATE_TABLE_LAST_GO="CREATE TABLE "+LAST_GOS_TABLE_NAME+"("
                +LAST_GOS_COLUMN_ID+" INTEGER PRIMARY KEY,"
                +LAST_GOS_NOME_NET+" TEXT)";
        db.execSQL(CREATE_TABLE_LAST_GO);

        //CREO TABELLA DEI MEX
        String CREATE_TABLE_MEX="CREATE TABLE "+MEX_TABLE_NAME+"("
                +MEX_COLUMN_ID_DB+" INTEGER PRIMARY KEY,"
                +MEX_COLUMN_ID_MEX+" TEXT, "
                //+MEX_COLUMN_MITTENTE+" TEXT, "
                +MEX_COLUMN_TESTO+" TEXT, "
                +MEX_COLUMN_TIMESTAMP_ORIGINE+" INTEGER, "
                +MEX_COLUMN_TIMESTAMP_RICEZIONE+" INTEGER, "
                +MEX_COLUMN_LAT+" REAL, "
                +MEX_COLUMN_LNG+" REAL) ";

        db.execSQL(CREATE_TABLE_MEX);


        //creo tabella degli update
        String CREATE_TABLE_HORICEVUTODA="CREATE TABLE "+HO_RICEVUTO_DA_TABLE_NAME+"("
                +HO_RICEVUTO_DA_ID_DB+" INTEGER PRIMARY KEY,"
                +HO_RICEVUTO_DA_ID_DISP+" TEXT, "
                +HO_RICEVUTO_DA_TIMESTAMP+" TEXT) ";//FORSE DEVO METTERE INTEGER

        db.execSQL(CREATE_TABLE_HORICEVUTODA);

        String CREATE_TABLE_RISP_SERVER="CREATE TABLE "+RISP_SERVER_TABLE_NAME+" ("
                +RISP_SERVER_ID_DB+" INTEGER PRIMARY KEY,"
                +RISP_SERVER_ID_MEX+" TEXT,"
                +RISP_SERVER_TESTO+" TEXT,"
                +RISP_SERVER_TIMESTAMP_RICEZIONE+" INTEGER) ";
        db.execSQL(CREATE_TABLE_RISP_SERVER);

        String CREATE_TABLE_MEX_FROM_SERVER="CREATE TABLE "+MEX_FROM_SERVER_TABLE_NAME+" ("
                +MEX_FROM_SERVER_ID_DB+" INTEGER PRIMARY KEY,"
                +MEX_FROM_SERVER_ID_MEX+" TEXT,"
                +MEX_FROM_SERVER_TESTO+" TEXT,"
                +MEX_FROM_SERVER_TIMESTAMP_RICEZIONE+" INTEGER) ";
        db.execSQL(CREATE_TABLE_MEX_FROM_SERVER);




    }

    public boolean isInLastGo(String nomeNet){
        SQLiteDatabase db = this.getWritableDatabase();
        String ricerca="SELECT * FROM "+LAST_GOS_TABLE_NAME+" WHERE "+LAST_GOS_NOME_NET+"= '"+nomeNet+"'";

        Cursor cursor=db.rawQuery(ricerca,null);
        //db.close();
        if(cursor.getCount()==0) {
            Log.d(TAG,"il go non è nella tab ");
            return false;
        }
        else {
            Log.d(TAG,"il go sta già nella tab");
            return true;
        }
    }

    public void cancellaUltimiGO(){
        Log.e(TAG,"eliminazione record tabella UltimiGO");
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(LAST_GOS_TABLE_NAME,null,null);
    }

    public boolean insertGo(String nomeNet){

        SQLiteDatabase db = this.getWritableDatabase();

        if(!isInLastGo(nomeNet)) {

            String ricerca = "SELECT * FROM " + LAST_GOS_TABLE_NAME;
            Cursor cursor = db.rawQuery(ricerca, null);
            if (cursor.getCount() == numero_go_mem) {
                //devo eliminare il primo
                Log.d(TAG + " insertGO", "ho raggiunto il num max di GO in mem");
                if (cursor.moveToFirst()) {
                    int id = cursor.getInt(cursor.getColumnIndex(LAST_GOS_COLUMN_ID));
                    String elimina = "DELETE FROM " + LAST_GOS_TABLE_NAME + " WHERE " + LAST_GOS_COLUMN_ID + "=" + id;
                    db.rawQuery(elimina, null);
                }
            }
        }



        ContentValues val=new ContentValues();
        val.put(LAST_GOS_NOME_NET,nomeNet);

        long id=db.insert(LAST_GOS_TABLE_NAME,null,val);
        if(id==-1){
            return false;
        }
        else{
            return true;
        }


    }


    public long takeLastUpdate(String idDisp) {
        String funz="takeLastUpdate";
        SQLiteDatabase db = this.getWritableDatabase();
        String ricerca = "SELECT " + HO_RICEVUTO_DA_TIMESTAMP + " FROM " + HO_RICEVUTO_DA_TABLE_NAME + " WHERE " + HO_RICEVUTO_DA_ID_DISP + "= '" + idDisp + "'";

        Cursor cursor = db.rawQuery(ricerca, null);
        if (cursor != null) {
            if(cursor.getCount()==0){
                return 0;
            }
            if(cursor.getCount()==1) {
                while(cursor.moveToNext()) {
                    long timeStampLastUpdate = cursor.getLong(cursor.getColumnIndex(HO_RICEVUTO_DA_TIMESTAMP));
                    Log.d(funz, "lastUpdate: " + timeStampLastUpdate);
                    return timeStampLastUpdate;

                }
            }
            else{
                Log.e(funz,"MANNAGGIA MANNAGGIA CI SONO + ID UGUALI NELLA TAB");
                return -1;
            }
        }
        return -1;
    }


    public List<String> takeLastMex(long timestamp){//timestamp indica il tempo dell'ultimo update che l'altro dispositivo ha ricevuto da me
            //quindi dovrei andare a vedere e prendere il timestamp di ricezione maggiore rispetto al timestamp
        String funz="takeLastMex";

        List<String> lista=new ArrayList<>();
        lista.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        String ricerca;
        if(timestamp==0)
            ricerca="SELECT * FROM "+MEX_TABLE_NAME;
        else
            ricerca="SELECT * FROM "+MEX_TABLE_NAME+" WHERE "+MEX_COLUMN_TIMESTAMP_RICEZIONE+">"+timestamp;


        Cursor cursor = db.rawQuery(ricerca, null);
        if (cursor != null) {
            while(cursor.moveToNext()) {

                String tipo="0";
                String idMex=cursor.getString(cursor.getColumnIndex(MEX_COLUMN_ID_MEX));
                String timestampOr=""+cursor.getLong(cursor.getColumnIndex(MEX_COLUMN_TIMESTAMP_ORIGINE));
                String testo=cursor.getString(cursor.getColumnIndex(MEX_COLUMN_TESTO));
                String lat=""+cursor.getDouble(cursor.getColumnIndex(MEX_COLUMN_LAT));
                String lng=""+cursor.getDouble(cursor.getColumnIndex(MEX_COLUMN_LNG));

                String messaggio=tipo+separatore
                        +idMex+separatore
                        +timestampOr+separatore
                        +testo+separatore
                        +lat+separatore
                        +lng;
                lista.add(messaggio);
            }
        }



        return lista;
    }
    public List<String> takeLastRisp(long timestamp){//timestamp indica il tempo dell'ultimo update che l'altro dispositivo ha ricevuto da me
        //quindi dovrei andare a vedere e prendere il timestamp di ricezione maggiore rispetto al timestamp
        String funz="takeLastRisp";

        List<String> lista=new ArrayList<>();
        lista.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        String ricerca;
        if(timestamp==0)
            ricerca="SELECT * FROM "+RISP_SERVER_TABLE_NAME;
        else
            ricerca="SELECT * FROM "+RISP_SERVER_TABLE_NAME+" WHERE "+RISP_SERVER_TIMESTAMP_RICEZIONE+">"+timestamp;


        Cursor cursor = db.rawQuery(ricerca, null);
        if (cursor != null) {
            while(cursor.moveToNext()) {

                String tipo="1";
                String idMex=cursor.getString(cursor.getColumnIndex(RISP_SERVER_ID_MEX));

                String testo=cursor.getString(cursor.getColumnIndex(RISP_SERVER_TESTO));


                String messaggio=tipo+separatore
                        +idMex+separatore

                        +testo;
                lista.add(messaggio);
            }
        }






        return lista;
    }
    public List<String> takeLastMexFromServer(long timestamp){//timestamp indica il tempo dell'ultimo update che l'altro dispositivo ha ricevuto da me

        String funz="takeLastMexFromServer";

        List<String> lista=new ArrayList<>();
        lista.clear();
        SQLiteDatabase db = this.getWritableDatabase();
        String ricerca;
        if(timestamp==0)
            ricerca="SELECT * FROM "+MEX_FROM_SERVER_TABLE_NAME;
        else
            ricerca="SELECT * FROM "+MEX_FROM_SERVER_TABLE_NAME+" WHERE "+MEX_FROM_SERVER_TIMESTAMP_RICEZIONE+">"+timestamp;


        Cursor cursor = db.rawQuery(ricerca, null);
        if (cursor != null) {
            while(cursor.moveToNext()) {

                String tipo="2";
                String idMex=cursor.getString(cursor.getColumnIndex(MEX_FROM_SERVER_ID_MEX));

                String testo=cursor.getString(cursor.getColumnIndex(MEX_FROM_SERVER_TESTO));


                String messaggio=tipo+separatore
                        +idMex+separatore

                        +testo;
                lista.add(messaggio);
            }
        }






        return lista;
    }

    public void inserisciNuovoMessaggio(String testo, double lat, double lng){
        String funz="inserisciNuovoMessaggio";
        Log.e(funz,"sono partito");
        Log.d(funz,"testo: "+testo);
        Log.d(funz,"lat: "+lat);
        Log.d(funz,"lng: "+lng);

        SQLiteDatabase db = this.getWritableDatabase();

        String mittente = Settings.Secure.getString(mainAct.getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);
        // non so se è una buona idea ma x ora metto id del dispositivo + timestamp

        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        long actualTime=c.getTimeInMillis()/1000;

        String idUnivoco=mittente+"_"+actualTime;
        Log.d(funz,"idUnivoco: "+idUnivoco);

        ContentValues val=new ContentValues();
        val.put(MEX_COLUMN_ID_MEX,idUnivoco);

        val.put(MEX_COLUMN_TESTO,testo);
        val.put(MEX_COLUMN_TIMESTAMP_RICEZIONE,actualTime);
        val.put(MEX_COLUMN_TIMESTAMP_ORIGINE,actualTime);
        val.put(MEX_COLUMN_LAT,lat);
        val.put(MEX_COLUMN_LNG,lng);

        long id=db.insert(MEX_TABLE_NAME,null,val);
        if(id==-1){
            Log.e(funz,"QUALCOSA è ANDATO STORTO");

        }else{
            Log.d(funz,"TUTTAPPOST");
        }

    }

    public void inserisciNuovaRisposta(String testo, String idMex){
        String funz="inserisciNuovaRisposta";

        SQLiteDatabase db = this.getWritableDatabase();

        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        long actualTime=c.getTimeInMillis()/1000;

        ContentValues val=new ContentValues();
        val.put(RISP_SERVER_ID_MEX,idMex);
        val.put(RISP_SERVER_TESTO,testo);
        val.put(RISP_SERVER_TIMESTAMP_RICEZIONE,actualTime);

        if(esisteMessaggio(idMex)){
            cancellaMessaggio(idMex);
        }
        if(!esisteMexFromServer(idMex)) {
            long id = db.insert(RISP_SERVER_TABLE_NAME, null, val);
            if (id == -1) {
                Log.e(funz, "QUALCOSA è ANDATO STORTO");

            } else {
                Log.d(funz, "TUTTAPPOST");
            }
        }



    }

    public void inserisciNuovoMexFromServer(String testo,String idMex){
        String funz="inserisciNuovoMexFromServer";

        SQLiteDatabase db = this.getWritableDatabase();

        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("GMT"));
        long actualTime=c.getTimeInMillis()/1000;

        ContentValues val=new ContentValues();
        val.put(MEX_FROM_SERVER_ID_MEX,idMex);
        val.put(MEX_FROM_SERVER_TESTO,testo);
        val.put(MEX_FROM_SERVER_TIMESTAMP_RICEZIONE,actualTime);

        if(esisteMessaggio(idMex)){
            cancellaMessaggio(idMex);
        }
        if(esisteRisposta(idMex)){
            cancellaRisposta(idMex);
        }
        if(!esisteMexFromServer(idMex)) {
            long id = db.insert(MEX_FROM_SERVER_TABLE_NAME, null, val);
            if (id == -1) {
                Log.e(funz, "QUALCOSA è ANDATO STORTO");

            } else {
                Log.d(funz, "TUTTAPPOST");
            }
        }

    }

    public void cancellaMessaggio(String idMex){
        String funz="cancellaMessaggio";
        SQLiteDatabase db = this.getWritableDatabase();
        String where=MEX_COLUMN_ID_MEX+" = '"+idMex+"'";
        int righe=db.delete(MEX_TABLE_NAME,where,null);
       if(righe==1){
            Log.d(funz,"tutto è andato a buon fine");

        }else {
            Log.e(funz,"MI SA CHE QUALCOSA NON VA PER NIENTE");

        }
    }
    public void cancellaRisposta(String idMex){
        String funz="CancellaRisposta";
        SQLiteDatabase db = this.getWritableDatabase();
        String where=RISP_SERVER_ID_MEX+" = '"+idMex+"'";
        int righe=db.delete(RISP_SERVER_TABLE_NAME,where,null);
        if(righe==1){
            Log.d(funz,"tutto è andato a buon fine");

        }else {
            Log.e(funz,"MI SA CHE QUALCOSA NON VA PER NIENTE");

        }
    }

    public void     inserisciMessaggiLetti(List<String> lista,long timestamp,String idDisp){
        String funz="inserisciMessaggiLetti";
        SQLiteDatabase db = this.getWritableDatabase();
        //------->NELLA LISTA C'è ANCHE IL MESSAGGIO FINALE<-------

        for(int i=0;i<lista.size()-1;i++){
            String tmp=lista.get(i);
            char tipo=tmp.charAt(0);
            if(tipo=='0') {
                //è un messaggio da un dispositivo
                String[] parti = tmp.split("/");
                if (parti.length == numPartiMessaggio) {

                    //è un messaggio normale da un altro dispositivo
                    if (!esisteMessaggio(parti[1]) && !esisteRisposta(parti[1]) && !esisteMexFromServer(parti[1])) {
                        ContentValues val = new ContentValues();
                        val.put(MEX_COLUMN_ID_MEX, parti[1]);
                        val.put(MEX_COLUMN_TIMESTAMP_ORIGINE, Long.parseLong(parti[2]));
                        val.put(MEX_COLUMN_TESTO, parti[3]);
                        val.put(MEX_COLUMN_LAT, Double.parseDouble(parti[4]));
                        val.put(MEX_COLUMN_LNG, Double.parseDouble(parti[5]));
                        val.put(MEX_COLUMN_TIMESTAMP_RICEZIONE, timestamp);


                        long id = db.insert(MEX_TABLE_NAME, null, val);
                        if (id == -1)
                            Log.e(funz, "----ERRORE INSERIMENTO MESSAGGIO LETTO----");
                    }


                } else {
                    Log.e(funz, ">>> ERROREEEEE PARTI MESSAGGIO <<<<<");
                }
            }else if(tipo=='1'){
                //è una risposta dal server
                String[] parti = tmp.split("/");

                if (parti.length == numPartiRisposta) {

                    if(esisteMessaggio(parti[1]))
                        cancellaMessaggio(parti[1]);


                    if (!esisteRisposta(parti[1]) && !esisteMexFromServer(parti[1])) {
                        ContentValues val = new ContentValues();
                        val.put(RISP_SERVER_ID_MEX, parti[1]);
                        val.put(RISP_SERVER_TIMESTAMP_RICEZIONE, timestamp);
                        val.put(RISP_SERVER_TESTO, parti[2]);


                        long id = db.insert(RISP_SERVER_TABLE_NAME, null, val);
                        if (id == -1)
                            Log.e(funz, "----ERRORE INSERIMENTO RISPOSTA LETTA----");
                        else{
                            //se il mex è stato inviato dal dispositivo attuale mando una notifica all'utente
                            String myId=Settings.Secure.getString(mainAct.getApplicationContext().getContentResolver(),
                                    Settings.Secure.ANDROID_ID);
                            if(parti[1].contains(myId)){
                                NotificationCompat.Builder mBuilder =
                                        new NotificationCompat.Builder(mainAct.getApplicationContext())
                                                .setSmallIcon(R.drawable.ic_launcher)
                                                .setContentTitle("My notification")
                                                .setContentText("Il server ha ricevuto il tuo messaggio")
                                                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
                                NotificationManager mNotifyMgr =
                                        (NotificationManager) mainAct.getSystemService(mainAct.getApplicationContext().NOTIFICATION_SERVICE);
                                // Builds the notification and issues it.
                                mNotifyMgr.notify(i, mBuilder.build());

                            }
                        }
                    }
                }
                else {
                    Log.e(funz, ">>> ERROREEEEE PARTI RISPOSTA <<<<<");
                }


            }else if (tipo=='2') {
                //MESSAGGI DAL SERVER

                String[] parti = tmp.split("/");
                if (parti.length == numPartiMexFromServer) {


                    if(esisteMessaggio(parti[1]))
                        cancellaMessaggio(parti[1]);

                    if(esisteRisposta(parti[1]))
                        cancellaRisposta(parti[1]);


                    if (!esisteMexFromServer(parti[1])) {
                        ContentValues val = new ContentValues();
                        val.put(MEX_FROM_SERVER_ID_MEX, parti[1]);
                        val.put(MEX_FROM_SERVER_TIMESTAMP_RICEZIONE, timestamp);
                        val.put(MEX_FROM_SERVER_TESTO, parti[2]);


                        long id = db.insert(MEX_FROM_SERVER_TABLE_NAME, null, val);
                        if (id == -1)
                            Log.e(funz, "----ERRORE INSERIMENTO RISPOSTA LETTA----");
                        else{
                            //se il mex è stato inviato dal dispositivo attuale mando una notifica all'utente
                            String myId=Settings.Secure.getString(mainAct.getApplicationContext().getContentResolver(),
                                    Settings.Secure.ANDROID_ID);
                            if(parti[1].contains(myId)){
                                NotificationCompat.Builder mBuilder =
                                        new NotificationCompat.Builder(mainAct.getApplicationContext())
                                                .setSmallIcon(R.drawable.ic_launcher)
                                                .setContentTitle("My notification")
                                                .setContentText("Qualcuno sta venendo ad aiutarti")
                                                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
                                NotificationManager mNotifyMgr =
                                        (NotificationManager) mainAct.getSystemService(mainAct.getApplicationContext().NOTIFICATION_SERVICE);
                                // Builds the notification and issues it.
                                mNotifyMgr.notify(i, mBuilder.build());

                            }
                        }
                    }
                }
                else {
                    Log.e(funz, ">>> ERROREEEEE PARTI RISPOSTA <<<<<");
                }

            } else {
                Log.e(funz, ">>> ERROREEEEE TIPO<<<<<");
            }

        }
        if(lista.get(lista.size()-1).equals(messaggioFinale)){
            aggiornaTabHoRicevuto(idDisp,timestamp);
            //lo scambio di messaggi è andato a buon fine
            //faccio update nella tabella lastUpdateFrom
            /*ContentValues val=new ContentValues();
            val.put(HO_RICEVUTO_DA_TABLE_NAME,actualTime);
            int righe=db.update(HO_RICEVUTO_DA_TABLE_NAME, val,MEX_COLUMN_ID_MEX+"= '"+idMex+"'",null);
            Log.d("updateTimeStampInvio","righe cambiate: "+righe);
            if(righe==0)
                return false;
            else
                return true;
                */

            //controllo se ho già ricevuto messaggi da questo

        }
    }

    public void aggiornaTabHoRicevuto(String id,long timestamp){
        String funz="aggiornaTabHoRicevuto";
        SQLiteDatabase db = this.getWritableDatabase();

        if(esisteId(id)){
            ContentValues val=new ContentValues();
            val.put(HO_RICEVUTO_DA_TIMESTAMP,timestamp);

            int righe=db.update(HO_RICEVUTO_DA_TABLE_NAME, val,HO_RICEVUTO_DA_ID_DISP+"= '"+id+"'",null);
            Log.d("updateTimeStampInvio","righe cambiate: "+righe);
            if(righe==0)
                Log.e( funz, "nessun dispositivo cambiato ### ERRORE ###");
            else
                Log.e( funz, "aggiornato timestamp");
        }
        else {
            //è la prima volta che ricevo aggiornamenti
            ContentValues val=new ContentValues();
            val.put(HO_RICEVUTO_DA_TIMESTAMP,timestamp);
            val.put(HO_RICEVUTO_DA_ID_DISP,id);

            long idDB =db.insert(HO_RICEVUTO_DA_TABLE_NAME,null,val);
            if(idDB==-1){
                Log.e(funz,"non sono riuscito a mettere il nuovo disp nella tab");
            }

        }
    }

    public boolean esisteId(String id){
        String funz="esisteId";
        SQLiteDatabase db = this.getWritableDatabase();


        String ricerca="SELECT * FROM "+HO_RICEVUTO_DA_TABLE_NAME+" WHERE "+HO_RICEVUTO_DA_ID_DISP+"= '"+id+"'";



        Cursor cursor=db.rawQuery(ricerca,null);
        if(cursor.getCount()==0){
            Log.d(funz,"NON ho mai ricevuto aggiornamenti da questo id");
            return false;
        }
        else{
            Log.d(funz," ho ricevuto aggiornamenti da questo id");
            return true;
        }
    }

    public boolean esisteMessaggio(String idMex){
        //attraverso l'id univoco del messaggio controllo se è presente nel mio db
        String funz="esisteMessaggio";
        SQLiteDatabase db = this.getWritableDatabase();

        //controllo se sta nei mex normali
        String ricerca="SELECT * FROM "+MEX_TABLE_NAME+" WHERE "+MEX_COLUMN_ID_MEX+"= '"+idMex+"'";



        Cursor cursor=db.rawQuery(ricerca,null);
        if(cursor.getCount()==0){
            Log.d("esisteMessaggio","il messaggio non è presente nella tab");
            return false;
        }
        else{
            Log.d("esisteMessaggio","il messaggio è presente nella tab");
            return true;
        }

    }
    public boolean esisteRisposta(String idMex){
        //attraverso l'id univoco del messaggio controllo se è presente nel mio db
        String funz="esisteRisposta";
        SQLiteDatabase db = this.getWritableDatabase();

        //controllo se sta nei mex normali
        String ricerca="SELECT * FROM "+RISP_SERVER_TABLE_NAME+" WHERE "+RISP_SERVER_ID_MEX+"= '"+idMex+"'";



        Cursor cursor=db.rawQuery(ricerca,null);
        if(cursor.getCount()==0){
            Log.d(funz,"la risposta non è presente nella tab");
            return false;
        }
        else{
            Log.d(funz,"la risposta è presente nella tab");
            return true;
        }

    }
    public boolean esisteMexFromServer(String idMex){
        //attraverso l'id univoco del messaggio controllo se è presente nel mio db
        String funz="esisteMexFromServer";
        SQLiteDatabase db = this.getWritableDatabase();

        //controllo se sta nei mex normali
        String ricerca="SELECT * FROM "+MEX_FROM_SERVER_TABLE_NAME+" WHERE "+MEX_FROM_SERVER_ID_MEX+"= '"+idMex+"'";



        Cursor cursor=db.rawQuery(ricerca,null);
        if(cursor.getCount()==0){
            Log.d(funz,"il mex del server non è presente nella tab");
            return false;
        }
        else{
            Log.d(funz,"il mex del server è presente nella tab");
            return true;
        }

    }



    public void resetDB(){
        String TAG="resetDB";
        cancellaUltimiGO();


        SQLiteDatabase db = this.getWritableDatabase();


        if(db.delete(MEX_TABLE_NAME,null,null)!=0)
            Log.d(TAG,"eliminazione record tabella messaggi");

        if(db.delete(HO_RICEVUTO_DA_TABLE_NAME,null,null)!=0)
            Log.d(TAG,"eliminazione record tabella horicevutoDa");

        if(db.delete(RISP_SERVER_TABLE_NAME,null,null)!=0)
            Log.d(TAG,"eliminazione record tabella risp_server");

        if(db.delete(MEX_FROM_SERVER_TABLE_NAME,null,null)!=0)
            Log.d(TAG,"eliminazione record tabella mex from server");

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int arg1, int arg2) {

        db.execSQL("DROP TABLE IF EXISTS " + LAST_GOS_TABLE_NAME);
        onCreate(db);
    }
}
