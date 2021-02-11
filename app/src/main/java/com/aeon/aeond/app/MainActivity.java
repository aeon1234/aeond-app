package com.aeon.aeond.app;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.aeon.aeond.app.model.Daemon;
import com.aeon.aeond.app.model.DaemonService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    public static LogAdapter adapter;
    private static boolean initDone = false;
    private static final long RefreshInterval = 1000;
    public static Button controller;
    public static String BINARY_PATH;
    public static String PACKAGE_NAME;
    public static boolean isOnScreen;
    private boolean isDisabled = true;
    private RecyclerView recyclerView;
    private TextView text_height;
    private TextView text_peers;
    private TextView text_downloading;
    private String options;
    private static final int MAX_COUNT = 10;
    private Button button_exec;
    private EditText text_cmd;
    private Preferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        collect();
        preferences = new Preferences();
        PACKAGE_NAME = getPackageName();
        if (!initDone) copyBinaryFile();
        if(preferences.getString("options",TAG).equals("")) {
            options = "--limit-rate=300 --restricted-rpc --db-sync-mode=safe:sync --data-dir=" + getDataDir();
            preferences.putString("options",options,TAG);
        } else {
            options = preferences.getString("options",TAG);
        }
        adapter = new LogAdapter(MAX_COUNT);
        recyclerView.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
        recyclerView.setAdapter(adapter);
        button_exec.setOnClickListener(this::writeCommand);
        if(DaemonService.getDaemon()==null ||
                (DaemonService.getDaemon()!=null &&
                DaemonService.getDaemon().isStopped())) {
            text_cmd.setText(options);
            setDisabledUI();
        } else {
            setEnabledUI();
        }
        final Handler handler = new Handler();
        Runnable r = new Runnable()  {
            @Override
            public void run() {
                doUpdate();
                handler.postDelayed( this, RefreshInterval );
            }
        };
        handler.postDelayed(r,RefreshInterval);
    }
    private void  collect(){
        text_height = (TextView) findViewById(R.id.height);
        text_peers = (TextView) findViewById(R.id.peers);
        text_downloading = (TextView) findViewById(R.id.downloading);
        controller = findViewById(R.id.controller);
        recyclerView = (RecyclerView) findViewById(R.id.rv_log_list);
        button_exec = findViewById(R.id.button_exec);
        text_cmd = findViewById(R.id.text_cmd);
    }
    private void setEnabledUI(){
        isDisabled = false;
        button_exec.setVisibility(View.VISIBLE);
        controller.setText("Stop");
        controller.setOnClickListener(view -> {
            if(DaemonService.getDaemon()!=null && !DaemonService.getDaemon().isStopped()) {
                stopService(new Intent(getApplicationContext(), DaemonService.class));
                text_cmd.setText(preferences.getString("options",TAG));
                setDisabledUI();
            }
        });
    }
    private void setDisabledUI(){
        isDisabled = true;
        text_peers.setText("");
        button_exec.setVisibility(View.GONE);
        text_downloading.setText(getText(R.string.daemon_not_running));
        controller.setText("Start");
        controller.setOnClickListener(view -> {
            if(DaemonService.getDaemon()==null ||(DaemonService.getDaemon()!=null && DaemonService.getDaemon().isStopped())) {
                text_downloading.setText(getText(R.string.sync_starting));
                Intent intent = new Intent(getApplicationContext(), DaemonService.class);
                intent.putExtra("options",text_cmd.getText().toString());
                preferences.putString("options",text_cmd.getText().toString(),TAG);
                startService(intent);
                text_cmd.setText("");
                text_cmd.setHint("help");
                setEnabledUI();
            }
        });
    }
    private void writeCommand(View v){
        String cmd = text_cmd.getText().toString();
        if( !DaemonService.getDaemon().isStopped()) {
            DaemonService.getDaemon().write(cmd);
        }
        text_cmd.setText("");
    }
    private void doUpdate() {
        Daemon daemon = DaemonService.getDaemon();
        if (daemon == null) return;
        if (!isDisabled) {
            setEnabledUI();
            text_height.setText(daemon.getHeight() + " >> " + daemon.getTarget());
            if (daemon.getPeers() != null) {
                Log.d(TAG,daemon.getPeers());
                text_peers.setText(daemon.getPeers() + " " + getString(R.string.msg_peers_connected));
            } else{
                text_peers.setText( "0 " + getString(R.string.msg_peers_connected));
            }
            if (daemon.getDownloading() != null) {
                text_downloading.setText(daemon.getDownloading() + " kB/s >> "+String.format("%.1f", getUsedSpace()) + " " + getString(R.string.disk_used));
            }else {
                text_downloading.setText(String.format("%.1f", getUsedSpace()) + " " +getString(R.string.disk_used));
            }
        } else {
            setDisabledUI();
        }
    }
    @Override
    public void onResume(){
        super.onResume();
        isOnScreen = true;
    }
    @Override
    public void onPause(){
        super.onPause();
        isOnScreen = false;
    }
    private void copyBinaryFile() {
        String libpath = getApplicationInfo().nativeLibraryDir;
        BINARY_PATH = is64bitsProcessor() ? libpath+"/libaeond64.so":libpath+"/libaeond32.so";
    }
    private boolean is64bitsProcessor() {
        String[] supported = Build.SUPPORTED_ABIS;
        for (String s : supported) {
            if (s.equals("arm64-v8a")) return true;
        }
        return false;
    }
    private float getUsedSpace() {
        File f = new File(MainActivity.BINARY_PATH);
        return f.getFreeSpace() / 1024.0f / 1024.0f / 1024.0f;
    }
    public static class LogAdapter extends RecyclerView.Adapter<LogAdapter.ViewHolder> {

        private static final String TAG = LogAdapter.class.getSimpleName();
        private final List<String> mValues;
        private static int COUNT;

        public LogAdapter(int max_items) {
            mValues = new ArrayList<>();
            COUNT = max_items;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.log_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            holder.mItem = mValues.get(position);
            holder.mContentView.setText(mValues.get(position));
        }

        public void addItem(String item) {
            mValues.add(item);
            while(mValues.size()>COUNT){
                mValues.remove(0);
            }
            notifyChanged();
        }

        private void notifyChanged(){
            Log.d(TAG,"notifyChanged");
            if(MainActivity.isOnScreen) {
                Log.d(TAG,"notifyDataSetChanged");
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(() -> notifyDataSetChanged());
            }
        }
        @Override
        public int getItemCount() {
            return mValues.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View mView;
            public final TextView mContentView;
            public String mItem;

            public ViewHolder(View view) {
                super(view);
                mView = view;
                mContentView = (TextView) view.findViewById(R.id.content);
            }

            @Override
            public String toString() {
                return super.toString() + " '" + mContentView.getText() + "'";
            }
        }
    }
    public class Preferences {
        public void putString(String key, String value, String prefsName){
            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(prefsName,MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString(key,value);
            editor.apply();
        }
        public void removeString(String key, String prefsName){
            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(prefsName,MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.remove(key);
            editor.apply();
        }
        public String getString(String key, String prefsName){
            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(prefsName,MODE_PRIVATE);
            return sharedPref.getString(key,"");
        }
        public Set<String> getKeys(String prefsName){
            SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(prefsName,MODE_PRIVATE);
            return sharedPref.getAll().keySet();
        }
        public String getString(int key) {
            return getApplicationContext().getResources().getString(key);
        }
    }

}