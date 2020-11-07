package com.pdaxrom.bledistance;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.content.BroadcastReceiver;
import android.os.ParcelFileDescriptor;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;
import android.content.Context;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.os.ParcelUuid;
import android.widget.Toast;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import androidx.core.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class BLEService extends Service implements Runnable, Handler.Callback {
    private static final String TAG = "BLEService";
    public static final String ACTION_CONNECT = "com.pdaxrom.bledistance.BLEService.START";
    public static final String ACTION_DISCONNECT = "com.pdaxrom.bledistance.BLEService.STOP";

    public static final String MSG_SRVS_FILTER = "com.pdaxrom.bledistance.BLEService";
    public static final String MSG_SRVS_STATUS = "STATUS";

    private BroadcastReceiver bReceiv;

    private int connStatus;
    private String connMessage;

    private Handler mHandler;

    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothLeScanner btScanner;
    private BluetoothLeAdvertiser btAdvertiser;

    private int alert_level = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();
        btAdvertiser = btAdapter.getBluetoothLeAdvertiser();

        bReceiv = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean status = intent.getBooleanExtra(MSG_SRVS_STATUS, false);
                Log.i(TAG, "Message from service = " + status);
                if (status) {
                    sendStatus(connStatus, connMessage);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(bReceiv, new IntentFilter(MSG_SRVS_FILTER));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Log.i(TAG, "onStartCommand " + intent.getAction());
        } else {
            Log.i(TAG, "Service restarted by system");
        }

        if (intent != null && ACTION_DISCONNECT.equals(intent.getAction())) {
            stopThread();
            return START_NOT_STICKY;
        } else {
            startThread();
            return START_STICKY;
        }
    }

    @Override
    public void onDestroy() {
        stopThread();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(bReceiv);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
//            Log.i(TAG, "ScanCallback " + result.getDevice().getAddress() + " " + BluetoothAdapter.getDefaultAdapter().getAddress());
//
//            if (btAdapter.getAddress().equals(result.getDevice().getAddress())) {
//                return;
//            }

            Log.i(TAG, "Device address: " + result.getDevice().getAddress() + " rssi: " + result.getRssi());
            sendStatus((result.getRssi() > alert_level) ? ConnectFragment.SRVS_ALERT : ConnectFragment.SRVS_MESSAGE, "Device Address: "
                    + result.getDevice().getAddress()
                    + " rssi: " + result.getRssi());
//FIXME:
//            peripheralTextView.append("Device Name: " + result.getDevice().getName() + " rssi: " + result.getRssi() + "\n");

            // auto scroll for text view
//            final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
//            if (scrollAmount > 0)
//                peripheralTextView.scrollTo(0, scrollAmount);
        }
    };

    public void startScanning() {
        System.out.println("start scanning");

        List<ScanFilter> filters = new ArrayList<>();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid))))
                .build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

//FIXME:
//        peripheralTextView.setText("");
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(filters, settings, leScanCallback);
//                btScanner.startScan(leScanCallback);
            }
        });
    }

    public void stopScanning() {
//        System.out.println("stopping scanning");
//        peripheralTextView.append("Stopped Scanning");
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    private void startAdvertising() {
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build();

        ParcelUuid pUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(pUuid)
//                .addServiceData(pUuid, "Data".getBytes(Charset.forName("UTF-8")))
                .build();

        btAdvertiser.startAdvertising(settings, data, advertisingCallback);
    }

    private void stopAdvertising() {
        btAdvertiser.stopAdvertising(advertisingCallback);
    }

    AdvertiseCallback advertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e("BLE", "Advertising onStartFailure: " + errorCode);
            super.onStartFailure(errorCode);
        }
    };


    @Override
    public boolean handleMessage(Message message) {
        Toast.makeText(this, message.what, Toast.LENGTH_SHORT).show();
//        if (message.what != R.string.disconnected) {
            updateForegroundNotification(message.what);
//        }
        return true;
    }

    private void sendStatus(int status, String msg) {
        Intent intent = new Intent(ConnectFragment.MSG_SRVS_FILTER);
        connStatus = status;
        intent.putExtra(ConnectFragment.MSG_SRVS_STATUS, status);
        if (msg != null) {
            connMessage = msg;
            intent.putExtra(ConnectFragment.MSG_SRVS_MESSAGE, msg);
        }
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
    }

    private void sendStatus(int status) {
        sendStatus(status, null);
    }

    private void sendStatus(String msg) {
        sendStatus(connStatus, msg);
    }

    public void startThread() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());

        try {
            alert_level = Integer.parseInt(prefs.getString("alert_level", "-70"));
        } catch (NumberFormatException e) {
            alert_level = -70;
            prefs.edit()
                    .putString("alert_level", Integer.toString(alert_level))
                    .apply();
        }

        sendStatus(ConnectFragment.SRVS_STARTED, getString(R.string.started));
        updateForegroundNotification(R.string.started);
        startAdvertising();
        startScanning();
    }

    public void stopThread() {
        stopScanning();
        stopAdvertising();
        sendStatus(ConnectFragment.SRVS_STOPPED, getString(R.string.stopped));
        updateForegroundNotification(R.string.stopped);
        stopForeground(true);
    }

    private void updateForegroundNotification(final int message) {
        final String NOTIFICATION_CHANNEL_ID = "BLEService";

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(
                    NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setSound(null, null);
            mNotificationManager.createNotificationChannel(notificationChannel);
        }
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        //notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        startForeground(1, new NotificationCompat.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify_icon)
                .setContentTitle("BLEDistance")
                .setContentText(getString(message))
                .setContentIntent(pendingIntent)
                .build());
    }

    @Override
    public void run() {

    }
}
