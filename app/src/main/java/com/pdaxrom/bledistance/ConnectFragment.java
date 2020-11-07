package com.pdaxrom.bledistance;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static android.app.Activity.RESULT_OK;
import static android.content.Context.ACTIVITY_SERVICE;

public class ConnectFragment extends Fragment implements View.OnClickListener {
    private static final String TAG = "ConnectFragment";
    public interface Prefs {
        String NAME = "settingsPrefs";
        String CONFIG_FILE = "CONFIG_FILE";
        String CACHE_DIR = "CACHE_DIR";
//        String UPDATE_NODES = "update_nodes";
        String FORCE_UPDATE_NODES = "force_update_root_nodes";
        String RECONNECT_NO_BENCH = "reconnect_no_bench";
    }

    //public static final String CONFIG_URL = "https://raw.githubusercontent.com/lilitun/hexdump/master/dump.txt";
    public static final String CONFIG_URL64 = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL2xpbGl0dW4vaGV4ZHVtcC9tYXN0ZXIvZHVtcC50eHQ=";

    public static final String DEMO_KEY = "50049165b6321f800087ae9342db82f397ccd94f87a318a560e6cbd8551cbcc1";
    public static final String REMOTE_NODE = "";

    public static final String MSG_SRVS_FILTER = "com.pdaxrom.bledistance.ConnectFragment";
    public static final String MSG_SRVS_STATUS = "STATUS";
    public static final String MSG_SRVS_MESSAGE = "MESSAGE";
    public static final String MSG_SRVS_START_TIME = "START_TIME";

    public static final int SRVS_STOPPED = 0;
    public static final int SRVS_STOPPING = 1;
    public static final int SRVS_STARTED = 2;
    public static final int SRVS_STARTING = 3;
    public static final int SRVS_INPROGRESS = 4;
    public static final int SRVS_INUPDATE = 5;
    //public static final int SRVS_MESSAGE = 6;

    private static final String remoteConfigName = "remote";

    private final static int BUFFER_SIZE = 1024;
    private Button btn;
    private TextView tv;

//    private ImageView artImageView;

    boolean isActive;

    private AsyncTask<String, String, String> downloadFileFromURL = null;

    private final BroadcastReceiver onServiceMessage = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(MSG_SRVS_STATUS, SRVS_STOPPED);
            Log.i(TAG, "Message from service = " + status);
            String msg = intent.getStringExtra(MSG_SRVS_MESSAGE);
            if (msg != null) {
                tv.setText(msg);
            }
            setStatus(status);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_connect, container, false);

        btn = rootView.findViewById(R.id.go_button);
        btn.setOnClickListener(this);

        // Example of a call to a native method
        tv = rootView.findViewById(R.id.sample_text);
        tv.setText(getString(R.string.we_care));

//        artImageView = rootView.findViewById(R.id.imageView);
//        changeSaturation(0);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        prefs.edit()
            .putString(Prefs.CONFIG_FILE, getActivity().getCacheDir() + "/client-a.cfg")
            .putString(Prefs.CACHE_DIR, getActivity().getCacheDir().toString())
            .apply();

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(onServiceMessage, new IntentFilter(MSG_SRVS_FILTER));
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop()");
        mUpHandler.removeCallbacks(animateImage);
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(onServiceMessage);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");
        if (downloadFileFromURL != null && downloadFileFromURL.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }
        if (isServiceRunning()) {
            LocalBroadcastManager.getInstance(getActivity().getBaseContext()).sendBroadcast(
                    new Intent(BLEService.MSG_SRVS_FILTER)
                            .putExtra(BLEService.MSG_SRVS_STATUS, true)
            );
        } else {
            setStatus(SRVS_STOPPED);
        }
    }

    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);

        if (visible && getActivity() != null) {
//            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        }
        Log.i(TAG, "VISIBLE " + visible);
    }

    @Override
    public void onClick(View v) {
//        btn.setEnabled(false);
//        progressBar.setVisibility(View.VISIBLE);

        Intent intent = new Intent(getActivity(), BLEService.class);
        if (isActive) {
            Log.i(TAG, "btn disconnect");
            intent.setAction(BLEService.ACTION_DISCONNECT);
        } else {
            Log.i(TAG, "btn connect");
            intent.setAction(BLEService.ACTION_CONNECT);
        }
        getActivity().startService(intent);
    }

//    @Override
//    public void onActivityResult(int request, int result, Intent data) {
//        if (result == RESULT_OK) {
//            Intent intent = new Intent(getActivity(), BLEService.class);
//            if (isActive) {
//                Log.i(TAG, "btn disconnect");
//                intent.setAction(BLEService.ACTION_DISCONNECT);
//            } else {
//                Log.i(TAG, "btn connect");
//                intent.setAction(BLEService.ACTION_CONNECT);
//            }
//            getActivity().startService(intent);
//        }
//    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getActivity().getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if(BLEService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void setStatus(int status) {
        boolean enabled = true;
        if (status == SRVS_STOPPED) {
            btn.setText(getString(R.string.start));
            startAnimation(mLevel, -1, 0);
            enabled = true;
            isActive = false;
        } else if (status == SRVS_STARTED) {
            btn.setText(getString(R.string.stop));
            startAnimation(mLevel, 1, 100);
            enabled = true;
            isActive = true;
        }

        btn.setEnabled(enabled);
    }

    private void changeSaturation(float sat) {
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(sat);

//        Log.i(TAG, "saturation " + (int)(sat * 100));

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(matrix);
//        artImageView.setColorFilter(filter);
    }

    private int mStopLevel = -2;
    private int mLevel = 0;
    private int mDirLevel = 1;
    private static final int DELAY = 30;

    private final Handler mUpHandler = new Handler();
    private final Runnable animateImage = this::doTheAnimation;

    private void doTheAnimation() {
        if (mDirLevel > 0) {
            if (mLevel < 100) {
                mLevel += mDirLevel;
            } else {
                mDirLevel = -1;
            }
        } else {
            if (mLevel > 0) {
                mLevel += mDirLevel;
            } else {
                mDirLevel = 1;
            }
        }
        changeSaturation((float)mLevel / 100);
        if (mLevel != mStopLevel) {
            mUpHandler.postDelayed(animateImage, DELAY);
        } else {
            mUpHandler.removeCallbacks(animateImage);
        }
    }

    private void startAnimation() {
        mUpHandler.removeCallbacks(animateImage);
        mStopLevel = -1;
        mUpHandler.post(animateImage);
    }

    private void startAnimation(int level, int dir) {
        mDirLevel = dir;
        if (level < 0) {
            level = 0;
        } else if (level > 100) {
            level = 100;
        }
        mStopLevel = -1;
        startAnimation();
    }

    private void startAnimation(int level, int dir, int stop) {
        if (level != stop) {
            startAnimation(level, dir);
            stopAnimation(stop);
        }
    }

    private void stopAnimation(int level) {
        mStopLevel = level;
    }
}
