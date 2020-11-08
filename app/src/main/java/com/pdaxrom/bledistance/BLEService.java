/*
    https://www.ijcaonline.org/research/volume137/number13/jayakody-2016-ijca-909028.pdf
    https://developer.radiusnetworks.com/2014/12/04/fundamentals-of-beacon-ranging.html

 */
package com.pdaxrom.bledistance;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BLEService extends Service implements Runnable, Handler.Callback {
    private static final String TAG = "BLEService";
    public static final String ACTION_CONNECT = "com.pdaxrom.bledistance.BLEService.START";
    public static final String ACTION_DISCONNECT = "com.pdaxrom.bledistance.BLEService.STOP";

    public static final String MSG_SRVS_FILTER = "com.pdaxrom.bledistance.BLEService";
    public static final String MSG_SRVS_STATUS = "STATUS";

    private BroadcastReceiver mBcReceiver;

    private int mConnStatus;
    private String mConnMessage;

    private Handler mHandler;

    private BluetoothLeScanner mBtScanner;
    private BluetoothLeAdvertiser mBtAdvertiser;

    HashMap<String, KalmanFilterSimple> mKalmanFilters = new HashMap<>();

    private int mAlertLevel = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        BluetoothManager btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter btAdapter = btManager.getAdapter();
        mBtScanner = btAdapter.getBluetoothLeScanner();
        mBtAdvertiser = btAdapter.getBluetoothLeAdvertiser();

        mBcReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                boolean status = intent.getBooleanExtra(MSG_SRVS_STATUS, false);
                Log.i(TAG, "Message from service = " + status);
                if (status) {
                    sendStatus(mConnStatus, mConnMessage);
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mBcReceiver, new IntentFilter(MSG_SRVS_FILTER));
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBcReceiver);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Device scan callback.
    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            double smoothValue;

            if (mKalmanFilters.containsKey(result.getDevice().getAddress())) {
                KalmanFilterSimple filter = mKalmanFilters.get(result.getDevice().getAddress());
                smoothValue = filter.getSmooth(result.getRssi());
            } else {
                KalmanFilterSimple filter = new KalmanFilterSimple(0.75);
                smoothValue = filter.getSmooth(result.getRssi());
                mKalmanFilters.put(result.getDevice().getAddress(), filter);
            }

            Log.i(TAG, "Device address: " + result.getDevice().getAddress()
                    + " rssi: " + result.getRssi()
                    + " rssi smooth: " + smoothValue);

            if (Build.VERSION.SDK_INT >= 26) {
                //FIXME: unfortunately, looks like it's not working with builtin bluetooth
                Log.i(TAG, "txPower :" + result.getTxPower());
            }

            Log.i(TAG, "Approx. Distance : " + calculateDistance(-75, smoothValue));

            sendStatus((result.getRssi() > mAlertLevel) ? ConnectFragment.SRVS_ALERT : ConnectFragment.SRVS_MESSAGE,
                    "Device Address: "
                    + result.getDevice().getAddress()
                    + " rssi: " + smoothValue);
        }
    };

    public void startScanning() {
        mKalmanFilters.clear();

        List<ScanFilter> filters = new ArrayList<>();

        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid))))
                .build();
        filters.add(filter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        AsyncTask.execute(() -> mBtScanner.startScan(filters, settings, leScanCallback));
    }

    public void stopScanning() {
        AsyncTask.execute(() -> mBtScanner.stopScan(leScanCallback));
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

        mBtAdvertiser.startAdvertising(settings, data, advertisingCallback);
    }

    private void stopAdvertising() {
        mBtAdvertiser.stopAdvertising(advertisingCallback);
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
        updateForegroundNotification(message.what);
        return true;
    }

    private void sendStatus(int status, String msg) {
        Intent intent = new Intent(ConnectFragment.MSG_SRVS_FILTER);
        mConnStatus = status;
        intent.putExtra(ConnectFragment.MSG_SRVS_STATUS, status);
        if (msg != null) {
            mConnMessage = msg;
            intent.putExtra(ConnectFragment.MSG_SRVS_MESSAGE, msg);
        }
        LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
    }

    public void startThread() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());

        try {
            mAlertLevel = Integer.parseInt(prefs.getString("alert_level", "-70"));
        } catch (NumberFormatException e) {
            mAlertLevel = -70;
            prefs.edit()
                    .putString("alert_level", Integer.toString(mAlertLevel))
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

    /*
        You can plug these two numbers into a formula to calculate a distance estimate. Below,
        you can see the formula we created in the Android Beacon Library. The three constants
        in the formula (0.89976, 7.7095 and 0.111) are based on a best fit curve based on a
        number of measured signal strengths at various known distances from a Nexus 4.
        https://developer.radiusnetworks.com/2014/12/04/fundamentals-of-beacon-ranging.html
     */
    private static double calculateDistance(int txPower, double rssi) {
        if (rssi == 0) {
            return -1.0; // if we cannot determine distance, return -1.
        }

        double ratio = rssi*1.0/txPower;
        if (ratio < 1.0) {
            return Math.pow(ratio,10);
        } else {
            double accuracy =  (0.89976)*Math.pow(ratio,7.7095) + 0.111;
            return accuracy;
        }
    }

    private static class KalmanFilterSimple {
        private final double A;
        private double RSSIprev = Double.MAX_VALUE;

        public KalmanFilterSimple(double A) {
            this.A = A;
        }

        public double getSmooth(double R) {
            double smooth;
            if (RSSIprev == Double.MAX_VALUE) {
                smooth = R;
            } else {
                smooth = A * R + (1 - A) * RSSIprev;
            }
            RSSIprev = R;
            return smooth;
        }
    }
}
