package com.pdaxrom.bledistance;

import android.app.Service;
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
import java.util.Date;

public class BLEService extends Service implements Runnable, Handler.Callback {
    private static final String TAG = "BLEService";
    public static final String ACTION_CONNECT = "com.pdaxrom.bledistance.BLEService.START";
    public static final String ACTION_DISCONNECT = "com.pdaxrom.bledistance.BLEService.STOP";

    public static final String MSG_SRVS_FILTER = "com.pdaxrom.bledistance.BLEService";
    public static final String MSG_SRVS_STATUS = "STATUS";

    public interface ClientStatus {
        int NOERROR = 0;
        int BAD_CONFIG = 1;
        int NO_KEY = 2;
        int BAD_KEY = 3;
        int CONNECTION_ERROR = 4;
        int CONNECTION_REJECTED = 5;
        int CONNECTION_NOAUTH = 6;
        int CONFIGURATION_ERROR = 7;
        int UPDATING_NODELIST = 8;
        int UPDATING_BENCHMARKS = 9;
        int CONNECTING_TO_SERVER = 10;
        int WAITING_FOR_SERVER = 11;
    }

    private String mConfigFile;
    private String mCacheDir;
    private String mHexKey;
    private boolean mReconnectNoBenchmark;
    private String mRemoteNode;

    private int mClientStatus;

    private Thread mThread;
    private boolean mQuit;
    private ParcelFileDescriptor mInterface;

    private BroadcastReceiver bReceiv;

    private int connStatus;
    private String connMessage;
    private long connStartTime;

    private Handler mHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

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
        sendStatus(ConnectFragment.SRVS_STARTED, getString(R.string.started));
        updateForegroundNotification(R.string.started);
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplication());
    }

    public void stopThread() {
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
