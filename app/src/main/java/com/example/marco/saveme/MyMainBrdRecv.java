package com.example.marco.saveme;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.util.Log;

import java.util.HashMap;

/**
 * Created by Marco on 10/05/2015.
 */
public class MyMainBrdRecv extends BroadcastReceiver {
    public static final String TAG = "MyMainBrdRecv";

    MyMainService myMainService;
    WifiManager wifiManager;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;

    WifiP2pDnsSdServiceInfo myInfo;

    Thread client=null;
    Thread server=null;

    public MyMainBrdRecv(MyMainService myMainService) {
        super();
        Log.e(TAG, "sono partito");
        this.myMainService = myMainService;
        wifiManager = myMainService.wifiManager;
        mChannel = myMainService.mChannel;
        mManager = myMainService.mManager;

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "è cambiato qualcosa nello stato del p2p ");

            int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);

            if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                // Wifi Direct mode is enabled
                Log.e(TAG, "p2p attivo");
                myMainService.wifiIsActive = true;
                myMainService.algoritmo();
            } else {
                Log.e(TAG, "p2p disattivo");
                myMainService.wifiIsActive = false;

            }

        }
        else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");

            if(!myMainService.IAmGO) {

                mManager.requestConnectionInfo(mChannel, new WifiP2pManager.ConnectionInfoListener() {
                    @Override
                    public void onConnectionInfoAvailable(WifiP2pInfo info) {
                        String funz="onConnectionInfoAvailable";
                        if (info.groupFormed) {
                            Log.d(funz, "un gruppo è stato creato");
                            if (info.isGroupOwner) {
                                Log.e("WIFI_P2P_CONNECTION_CHANGED_ACTION", "  SONO     IL     GROUP     OWNER   ");
                                myMainService.modificaUI("SONO IL GO");
                                /*Toast.makeText(myMainService.getApplicationContext(), "SONO IL GO",
                                        Toast.LENGTH_SHORT).show();*/

                                myMainService.IAmGO = true;

                                if (server == null || !server.isAlive()) {

                                    server = new ServerThr(myMainService);

                                    server.start();

                                }

                                mManager.requestGroupInfo(mChannel, new WifiP2pManager.GroupInfoListener() {
                                    @Override
                                    public void onGroupInfoAvailable(WifiP2pGroup group) {
                                        if (group != null && group.isGroupOwner()) {



                                            Log.d(TAG, "nome: " + group.getNetworkName());
                                            Log.d(TAG, "pass:" + group.getPassphrase());
                                            Log.d(TAG, "dist: " + myMainService.distanza);

                                            HashMap record = new HashMap();
                                            record.put(myMainService.getString(R.string.record_nome), group.getNetworkName());
                                            record.put(myMainService.getString(R.string.record_pass), group.getPassphrase());
                                            record.put(myMainService.getString(R.string.record_indirizzo), group.getOwner().deviceAddress);
                                            record.put(myMainService.getString(R.string.record_dist), "" + myMainService.distanza);

                                            myInfo = WifiP2pDnsSdServiceInfo.newInstance("myGO", "_presence._tcp", record);

                                            mManager.removeLocalService(mChannel, myInfo, new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    Log.d("WIFI_P2P_CONNECTION_CHANGED_ACTION "+"removeLocalService", "success");
                                                }

                                                @Override
                                                public void onFailure(int reason) {
                                                    Log.e("WIFI_P2P_CONNECTION_CHANGED_ACTION "+"removeLocalService", "failure reason: " + reason);
                                                }
                                            });
                                            mManager.addLocalService(mChannel, myInfo, new WifiP2pManager.ActionListener() {
                                                @Override
                                                public void onSuccess() {
                                                    Log.d("WIFI_P2P_CONNECTION_CHANGED_ACTION ", "addLocalService SUCCESS");

                                                }

                                                @Override
                                                public void onFailure(int reason) {
                                                    Log.e("WIFI_P2P_CONNECTION_CHANGED_ACTION ", "addLocalService FAILURE " + reason);
                                                }
                                            });

                                        }
                                    }
                                });
                            }
                        }
                    }
                });
            }

        }
        else if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(action)) {
            Log.d(TAG, "SUPPLICANT_STATE_CHANGED_ACTION ");

            SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
            if (SupplicantState.isValidState(state)
                    && state == SupplicantState.COMPLETED) {

                boolean connected = checkConnectedToDesiredWifi();
                if (connected) {
                    Log.e("SUPPLICANT_STATE_CHANGED_ACTION", "       FINALMENTE SO CHE SONO CONNESSO");
                    myMainService.modificaUI("connesso a "+wifiManager.getConnectionInfo().getSSID());

                  /*  Toast.makeText(myMainService.getApplicationContext(), "sono connesso al GO",
                            Toast.LENGTH_SHORT).show();*/

                    myMainService.myDbHandler.insertGo(myMainService.bestNet.getNome());


                    if (myMainService.bestNet.getDistanza() != -1) {
                        Log.d("SUPPLICANT_STATE_CHANGED_ACTION", "    cambio distanza");
                        myMainService.distanza = myMainService.bestNet.getDistanza() + 1;
                    }


                    if (!myMainService.IAmGO) {

                        client = new Thread(new ClientThrd(myMainService));
                        client.start();
                        //myMainService.IAmClient=true;
                    } else {
                        wifiManager.disconnect();
                        wifiManager.removeNetwork(myMainService.connessioneCorrente);
                    }


                }
            }
        }
    }
    private boolean checkConnectedToDesiredWifi() {
        boolean connected = false;



        WifiInfo wifi=null;
        wifi= wifiManager.getConnectionInfo();
        String nomeRete="";
        if(myMainService.bestNet!=null)
            nomeRete=myMainService.bestNet.getNome();

        if (wifi != null && myMainService.bestNet!=null) {

            String nome = wifi.getSSID();

            Log.e("checkConnectedToDesiredWifi", "nome bestNet: " + nomeRete);
            Log.e("checkConnectedToDesiredWifi", "nome connessione corrente: " + nome);

            myMainService.modificaUI("sono connesso a: "+nomeRete);

            //nome.contains(nomeNet);
            connected = nome.contains(nomeRete);
        }
        return connected;


    }
    public void stopLocalServer(){
        Log.e(TAG,"stopLocalServer partita");
        if(server!=null && server.isAlive())
        {
            Log.e(TAG,"server è ancora vivo");
            ((ServerThr)server).closeMYSocket();
        }
    }

}
