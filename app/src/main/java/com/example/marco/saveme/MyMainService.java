package com.example.marco.saveme;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by Marco on 10/05/2015.
 */
public class MyMainService extends Service {

    public static final String TAG = " MyMainService";

    MyMainService myMainService;

    static final public String MODIFY_UI= "modificaUI";
    static final public String MSG_INTENT = "msgPerIntent";
    LocalBroadcastManager broadcaster;

    WifiManager wifiManager;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;

    MyMainBrdRecv mMainReceiver;
    IntentFilter mIntentFilter;

    int tempoGO=120;//mettiamolo a 2 min cosi il client ha il tempo per cercarlo e connettersi
    int tempoGoConn=240;
    int tempo_cerca_reti=20;
    int tempoClient=40;

    ScheduledExecutorService scheduleTaskExecutorRoutine;
    ScheduledExecutorService scheduleTaskExecutorCercaReti;




    MyDbHandler myDbHandler;

    boolean controlloDatiFinito=false;
    boolean dataIsActive=false;
    boolean wifiIsActive=false;
    boolean algInEx=false;

    boolean IAmGO=false;
    boolean IAmClient=false;

    int connessioneCorrente;

    int distanza=-1;


    WifiP2pDnsSdServiceRequest serviceRequest;

    List<Rete> listaRetiBonj=new ArrayList<Rete>();
    // i dati per ReteMigliore
    List<Rete> listaRetiNoConn=new ArrayList<Rete>();//le reti dei Go che hanno distanza -1
    List<Rete> listaRetiConConn=new ArrayList<Rete>();//le reti dei Go che hanno distanza 0 o maggiore
    List<Rete> listaCandidati = new ArrayList<Rete>();


    int dist_min=0;
    int dist_max=0;
    int intervallo=0;
    final int sogliaPotenza=30;
    Rete bestNet=null;

    Intent mIntent;
    boolean serverAttivo=false;

    //per debug
    boolean debugAttivo=true;
    boolean soloClient=false;
    boolean soloGo=false;
    boolean mostraListaWifi=true;

    public MyMainService(){

        myMainService=this;

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, " onCreate");
        broadcaster = LocalBroadcastManager.getInstance(this);
        modificaUI("MAIN SERVICE partito");

    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand  ");
        initialize();

        routine();
        return Service.START_NOT_STICKY;
    }
    public void initialize(){
        wifiManager=(WifiManager) getSystemService(Context.WIFI_SERVICE);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        // scheduleTaskExecutorStopClient= Executors.newScheduledThreadPool(5);
        mMainReceiver=new MyMainBrdRecv(myMainService);
        mIntentFilter=new IntentFilter();

        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        mIntentFilter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);


        registerReceiver(mMainReceiver, mIntentFilter);

        myDbHandler=MainActivity.myDbHandler; //todo stare attento a sta cosa, potrebbe creare problemi

        mIntent= new Intent(this,ServiceCommunicateWithServer.class);

        scheduleTaskExecutorRoutine= Executors.newScheduledThreadPool(5);

    }
    public void TimerPerRoutine(int tempo){
        String funz="TimerPerRoutine";
        Log.e(funz,"chiamato");
        scheduleTaskExecutorRoutine= Executors.newScheduledThreadPool(5);
        scheduleTaskExecutorRoutine.schedule(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG,"schedule routine tempo scaduto");
                routine();
                Log.e(TAG,"routine finita");
                //scheduleTaskExecutor.shutdown(); todo ricordarsi di spegnere lo scheduler
            }
        },tempo, TimeUnit.SECONDS);
    }
    public void routine(){
        String funz="routine";
        Log.e(funz,"sono partita");

        controlloDatiFinito=false;
        IAmGO=false;
        WifiP2pDnsSdServiceInfo myInfo=null;
        if(mMainReceiver!=null){
            try {
                myInfo=(mMainReceiver).myInfo;
            }catch (Exception e){
                Log.e(funz,"catturata eccezione myInfo "+e);
            }
        }
        if(myInfo!=null){
            mManager.removeLocalService(mChannel,myInfo,new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d("routine removeLocalService", "success");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e("routine removeLocalService", "failure reason: " + reason);
                }
            });
        }

        algInEx=false;
        if(mMainReceiver!=null){
            mMainReceiver.stopLocalServer();
        }

        new ControllaConnessione().execute();


    }
    class ControllaConnessione extends AsyncTask<String, String, String> {
        String funz="ControllaConnessione";
        public ControllaConnessione(){

        }
        @Override
        protected String doInBackground(String... uri) {
            wifiManager.disconnect();
            wifiManager.disableNetwork(wifiManager.getConnectionInfo().getNetworkId());

            if (isNetworkDataAvailable()) {
                //Log.e(funz,"possibile connessione dati");
                try {
                    HttpURLConnection urlc = (HttpURLConnection) (new URL("http://www.google.com").openConnection());
                    urlc.setRequestProperty("User-Agent", "Test");
                    urlc.setRequestProperty("Connection", "close");
                    urlc.setConnectTimeout(2000);
                    urlc.connect();
                    if(urlc.getResponseCode() == 200) {
                        //c'è connessione
                        Log.d("","la connessione è sicuramente ATTIVA");
                        dataIsActive=true;
                        distanza=0;

                    }


                } catch (IOException e) {
                    Log.d(funz, "Error checking internet connection"+ e);
                    dataIsActive=false;
                }
            } else {
                Log.d(funz, "No network available!");
                dataIsActive=false;
            }
            controlloDatiFinito=true;
            return "finito";
        }
        @Override
        protected void onPostExecute(String result) {
            Log.e(funz,"onPostExecute cominciato");
            if(wifiIsActive){
                //Log.e(funz,"wifi già attivo,chiamo algoritmo");
                algoritmo();
            }
            else{
                //Log.e(funz,"wifi ancora non attivo");
                wifiManager.setWifiEnabled(true);
            }
        }
    }
    private boolean isNetworkDataAvailable() {
        Log.d("isNetworkDataAvailable","chiamata");
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        // if(activeNetwork[i].getType()==cm.TYPE_MOBILE)

        if(activeNetworkInfo!=null && activeNetworkInfo.getType()==connectivityManager.TYPE_MOBILE) {
            //Log.e("isNetworkDataAvailable","forse c'è la connessione ai dati");
            return true;
        }
        else {


            return false;
        }


    }

    public void algoritmo(){
        String funz="algoritmo";
        Log.e(funz,"sono stato chiamato");
        if(!algInEx){

            //Log.d(funz,"posso partire");
            if(wifiIsActive && controlloDatiFinito){
                Log.d(funz,"il wifi è attivo posso andare avanti e il controllo dati è finito");
                algInEx=true;


                if((debugAttivo && soloClient) || (debugAttivo && soloGo)){
                    if(soloClient){
                        diventaClient();
                    }
                    if(soloGo){
                        diventaGo();
                    }
                }
                else {
                    if (dataIsActive) {
                        //faccio partire il contatto col server

                        if (!serverAttivo) {
                            startService(mIntent);
                            serverAttivo = true;
                        }
                        //devo diventare GO

                        diventaGo();



                    } else {

                        //todo da decommentare stopService(mIntent);

                        //devo diventare Client

                        diventaClient();




                    }
                }

            }
            else{
                Log.e(funz,"o il wifi non è attivo o il controlloDati non ha ancora finito");
            }
        }
        else{
            Log.e(funz,"NON posso partire sono già in esecuzione");
        }
    }

    public void diventaGo(){
        final String funz="diventaGo";
        Log.e(funz,"partito");

        IAmClient=false;
        if(!IAmGO) {
            Log.d(funz,"NON SONO GO->LO DIVENTO");

            if (connessioneCorrente != -1) {
                wifiManager.disableNetwork(connessioneCorrente);
                wifiManager.removeNetwork(connessioneCorrente);

            }

            if (mManager != null && mChannel != null) {
                //Log.e(funz, "faccio la remove group");
                mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onFailure(int reasonCode) {
                        Log.e(funz + " removegroup", "Disconnect failed. Reason  :" + reasonCode);
                        creaGO();
                    }

                    @Override
                    public void onSuccess() {
                        Log.d(funz + " removegroup", "rimosso con successo");
                        creaGO();
                    }

                });


            }
        }
        else{
            Log.e(funz, "            --- SONO GIA' GO ---");
        }


    }

    public void creaGO(){
        String funz="creaGO";
        mManager.createGroup(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {

                Log.d("creaGO create Group", "          SUCCESS");

                //chiamo il timer per la routine
                if(dataIsActive)
                    TimerPerRoutine(tempoGoConn);
                else
                    TimerPerRoutine(tempoGO);

            }

            @Override
            public void onFailure(int reason) {

                Log.e("creaGO create Group", "          FAILURE");


                //chiamo subito la routine
                routine();
            }
        });

    }

    public void diventaClient(){
        if(!IAmGO) {
            String funz = "diventaClient";

            Log.e(funz, "cominciata");

            if (mManager != null && mChannel != null) {


                mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onFailure(int reasonCode) {
                        Log.e("diventaClient " + "removegroup", "Disconnect failed. Reason  :" + reasonCode);
                        cercaReti();

                    }

                    @Override
                    public void onSuccess() {
                        Log.d("diventaClient removegroup", "rimosso con successo,chiamo cercaReti");

                        cercaReti();

                    }

                });


            }
        } else{
            Log.e(TAG,"non posso diventare client XKE SONO GIA GO");
        }
    }

    public void cercaReti(){
        String funz="cercaReti";
        Log.e(funz,"                           cominciata");

        if(!IAmGO) {
            if(wifiIsActive){
                listaRetiBonj.clear();

                serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

                WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
                    @Override


                    public void onDnsSdTxtRecordAvailable(
                            String fullDomain, Map record, WifiP2pDevice device) {
                        Log.d("cercaReti", "DnsSdTxtRecord available - " + record.toString());
                        //Log.d("main","DnsSdTxtRecord available - "+device);

                        Rete myRete = new Rete(
                                record.get(getString(R.string.record_nome)).toString(),
                                record.get(getString(R.string.record_pass)).toString(),
                                record.get(getString(R.string.record_indirizzo)).toString(),
                                Integer.parseInt(record.get(getString(R.string.record_dist)).toString()));

                        listaRetiBonj.add(myRete);
                        //mRecord=record;

                    }
                };

                WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
                    @Override
                    public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                                        WifiP2pDevice resourceType) {


                    }
                };

                mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);

                mManager.addServiceRequest(mChannel,serviceRequest,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d("cercaReti", "Added service discovery request");
                                //una volta aggiunto la richiesta di servizi faccio la richiesta di scoperta dei servizi
                                mManager.discoverServices(mChannel,new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d("cercaReti", "Service discovery initiated");
                                        scheduleTaskExecutorCercaReti = Executors.newScheduledThreadPool(5);
                                        scheduleTaskExecutorCercaReti.schedule(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d("sono il contatore di cerca reti", "tempo scaduto ");
                                                scheduleTaskExecutorCercaReti.shutdown();
                                                scegliReteMigliore();

                                            }
                                        }, tempo_cerca_reti, TimeUnit.SECONDS);
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        Log.e("cercaReti", "Service discovery failed " + reason);


                                        diventaGo();
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.e("cercaReti", "Failed adding service discovery request" + reason);
                                diventaGo();
                            }
                        });
            }
            else{
                Log.e("cercaReti","il wifi non è attivo");
                wifiManager.setWifiEnabled(true);
            }

        }else{
            Log.e(TAG,"cercaReti, ---- nn posso continuare xke sono GO ----");
        }
    }

    public void scegliReteMigliore(){
        String funz="scegliReteMigliore";
        Log.e(funz, "sono stata chiamata");

        mManager.clearServiceRequests(mChannel,new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d("reteMigliore","clearServiceRequest SUCCESS");
            }

            @Override
            public void onFailure(int reason) {
                Log.e("reteMigliore","clearServiceRequest FAILURE");
            }
        });

        List<ScanResult> listaConnWiFi=wifiManager.getScanResults();
        if(debugAttivo && mostraListaWifi)
            for (int i = 0; i < listaConnWiFi.size(); i++) {
                Log.e(funz, "nome wifi: " + listaConnWiFi.get(i).SSID);

                Log.d(funz, "potenza wifi: " + wifiManager.calculateSignalLevel(listaConnWiFi.get(i).level, 100));

            }


        /**
         * nella lista reti con connessione ci andranno tutti quei GO
         * che hanno una distanza !=-1 ossia che in un tot di passi riescono a far arrivare il
         * messaggio ad un GO che abbia la connessione internet attiva*/
        listaRetiConConn.clear();
        listaRetiNoConn.clear();

        for(int i=0; i<listaRetiBonj.size();i++){

            Rete tmp=listaRetiBonj.get(i);
            for(int j=0;j<listaConnWiFi.size();j++){

                if(tmp.getNome().equals(listaConnWiFi.get(j).SSID)){
                    tmp.setPotenza(wifiManager.calculateSignalLevel(listaConnWiFi.get(j).level, 100));

                    if(tmp.distanza==-1)
                        listaRetiNoConn.add(tmp);
                    else
                        listaRetiConConn.add(tmp);

                }
            }
        }

        if(listaRetiBonj.size()!=0) {
            Log.d(funz,"ci sono dei GO disponibili");
            if (listaRetiConConn.size() != 0) {
                Log.d(funz, "ci sono Go che hanno un percorso verso internet");
                listaCandidati.clear();
                for (int i = 0; i < listaRetiConConn.size(); i++) {
                    Rete tmp = listaRetiConConn.get(i);
                    //cerco i Go con connessione diretta
                    if (tmp.distanza == 0 && tmp.potenza > sogliaPotenza) {
                        listaCandidati.add(tmp);
                    }

                    //nel frattempo cerco dist_max e dist_min

                    //todo nella distanza minima devo mettere anche lo zero???
                    // forse non lo devo mettere dato che poi quando cerco i percorsi non lo tengo in considerazione

                    if (dist_min > tmp.distanza)
                        dist_min = tmp.distanza;
                    if (dist_max < tmp.distanza)
                        dist_max = tmp.distanza;

                }
                intervallo = dist_max - dist_min;

                if (listaCandidati.size() != 0) {
                    Log.d(funz, "ci sono GO con connessione diretta");
                    //qualsiasi sia la loro potenza del segnale mi provo a connettere
                    bestNet = findStrongestGO(listaCandidati);
                }else{
                    //ci sono GO che con un certo numero di passi riescono ad arrivare al Go con connessione
                    selezionaReti(30);//cerco i Go che hanno un percorso che è max il 30% dell'intervallo
                    if (listaCandidati.size() != 0) {
                        bestNet = findStrongestGO(listaCandidati);
                    } else {
                        selezionaReti(60);
                        if (listaCandidati.size() != 0) {
                            bestNet = findStrongestGO(listaCandidati);
                        } else {
                            selezionaReti(100);
                            if (listaCandidati.size() != 0) {
                                bestNet = findStrongestGO(listaCandidati);
                            } else {
                                //tutti i GO che possono raggiungere la connessione sono molto lontani da me->hanno una potenza inferiore al 30%
                                //a questo punto mi collego al + potente senza tenere conto dei passi
                                bestNet = findStrongestGO(listaRetiConConn);
                            }
                        }
                    }
                }
                if(bestNet!=null) {
                    Log.e(funz, "rete Migliore: " + bestNet.nome);
                    connettiAllaRete(bestNet);
                }

            }
            else {
                Log.d(funz, "Nessun Go è in grado di arrivare a internet");
                if (listaRetiNoConn.size() != 0) {
                    listaCandidati.clear();
                    for (int i = 0; i < listaRetiNoConn.size(); i++) {
                        Rete tmp = listaRetiNoConn.get(i);

                        if (!myDbHandler.isInLastGo(tmp.nome)) {
                            listaCandidati.add(tmp);
                        }
                    }

                    if (listaCandidati.size() != 0) {
                        //ci sono nuovi go rispetto alla lista

                        Log.d(funz, "--- ci sono nuovi GO rispetto alla lista");
                        bestNet = findStrongestGO(listaCandidati);


                    }
                    else {
                        //mi sono già collegato a tutti i go disponibili
                        Log.d(funz, " ---   NON ci sono nuovi GO rispetto alla lista");
                        bestNet = findStrongestGO(listaRetiNoConn);
                    }

                    Log.d(funz, "rete Migliore senza internet: " + bestNet.nome);
                    connettiAllaRete(bestNet);
                }else {
                    Log.d(funz, "i Go che hanno mandato mex di bonjour non sono presenti nella lista wifi");
                    diventaGo();
                }
            }

        }else{
            Log.d(funz,"nessunGO  disponibile");
            diventaGo();
        }

    }
    public void selezionaReti(int perc_int){
        for(int i=0;i<listaRetiConConn.size();i++){
            Rete tmp=listaRetiConConn.get(i);
            if(tmp.potenza>sogliaPotenza && (tmp.distanza)>dist_min && tmp.distanza<(dist_min+(perc_int*intervallo/100)))
            {
                listaCandidati.add(tmp);
            }
        }
    }
    public Rete findStrongestGO(List<Rete> listaReti){

        //scelgo il + potente
        Rete bestNet=null;
        for(int i=0;i<listaReti.size();i++){
            Rete tmp=listaReti.get(i);
            if(i==0)
                bestNet=tmp;
            else{

                if (bestNet.potenza < tmp.potenza)
                    bestNet = tmp;

            }
        }

        return bestNet;
    }

    public void connettiAllaRete(Rete bestNet) {
        String funz="connettiAllaRete";
        Log.e(funz,"cominciato");

        if(wifiManager.isWifiEnabled()){

            String nomeRete=bestNet.getNome();
            String pass=bestNet.getPassPhrase();

            WifiConfiguration conf = new WifiConfiguration();

            conf.SSID = "\"" + nomeRete + "\"";

            conf.preSharedKey="\""+ pass +"\"";
            conf.status=WifiConfiguration.Status.ENABLED;


            connessioneCorrente=wifiManager.addNetwork(conf);

            wifiManager.saveConfiguration();

            wifiManager.disconnect();

            Log.d("connetti alla rete", "ris addNetwork = " + connessioneCorrente);
            if(connessioneCorrente!=-1) {
                boolean risultato = wifiManager.enableNetwork(connessioneCorrente, true);
                if (risultato) {
                    Log.e("connettiAllaRete", "tutto bene mi sono connesso o almeno ho cominciato a connettermi ");
                    wifiManager.disconnect();
                    wifiManager.reconnect();
                    timerPerGo(tempoClient);
                }
                else{
                    diventaGo();
                }

            }
            else{
                diventaGo();
            }
        }
        else{
            Log.e(funz,"il wifi è disattivato");
            wifiManager.setWifiEnabled(true);
        }
    }

    public void timerPerGo(long tempoGO){
        final String funz="timerPerGo";
        Log.e(funz,"so stato chiamato");
        final ScheduledExecutorService scheduleTaskExecutor1= Executors.newScheduledThreadPool(5);
        scheduleTaskExecutor1.schedule(new Runnable() {
            @Override
            public void run() {
                Log.d("   timerPerGo ", "tempo scaduto");
                scheduleTaskExecutor1.shutdown();
                if(!IAmClient)
                    diventaGo();
                else{
                    Log.e(funz,"non faccio partire niente ---- sto ancora comunicando");
                }

            }
        }, tempoGO, TimeUnit.SECONDS);
    }

    @Override
    public void onDestroy() {
        String funz = "onDestroy";

        Log.e(TAG, "onDestroy");

        mMainReceiver.stopLocalServer();
        //stopService(mIntent);
        unregisterReceiver(mMainReceiver);

        if (mManager != null && mChannel != null) {
            Log.d(funz, "faccio la remove group");
            mManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.e("onDestroy removegroup", "Disconnect failed. Reason  :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                    Log.d( "onDestroy removegroup", "rimosso con successo");
                }

            });
        }
        stopService(mIntent);
        myDbHandler.cancellaUltimiGO();
        scheduleTaskExecutorRoutine.shutdown();
        super.onDestroy();
    }

    public void modificaUI(String message) {
        Intent intent = new Intent(MSG_INTENT);
        if(message != null)
            intent.putExtra(MODIFY_UI, message);
        broadcaster.sendBroadcast(intent);
    }

    public class Rete{
        private String nome;
        private String passPhrase;
        private String indirizzo;
        private String connessione;
        private int distanza=-1;
        private int potenza;

        public Rete(String nome,String passPhrase,String indirizzo,int distanza){
            this.nome=nome;
            this.passPhrase=passPhrase;
            this.indirizzo=indirizzo;
            this.distanza=distanza;
        }

        public void setPotenza(int potenza) {
            this.potenza = potenza;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getNome() {
            return nome;
        }

        public void setPassPhrase(String passPhrase) {
            this.passPhrase = passPhrase;
        }

        public String getPassPhrase() {
            return passPhrase;
        }

        public void setIndirizzo(String indirizzo) {
            this.indirizzo = indirizzo;
        }

        public String getIndirizzo() {
            return indirizzo;
        }

        public int getPotenza() {
            return potenza;
        }

        public String getConnessione() {
            return connessione;
        }

        public void setDistanza(int distanza) {
            this.distanza = distanza;
        }

        public int getDistanza() {
            return distanza;
        }
    }
}
