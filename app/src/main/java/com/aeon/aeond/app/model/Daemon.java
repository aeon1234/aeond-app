package com.aeon.aeond.app.model;
import android.util.Log;

import com.aeon.aeond.app.MainActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Daemon {
    private static final String TAG = Daemon.class.getSimpleName();
    private static final int MAX_LOG_SIZE = 5000;
    private enum ProcessState { STOPPED, STARTING, RUNNING, STOPPING }
    private BufferedReader reader=null;
    private BufferedWriter writer=null;
    private String version=null;
    private StringBuffer logs=null;
    private StringBuilder logsBuilder=null;
    private String height;
    private final String options;
    private String target;
    private String peers;
    private String downloading;
    private Process process=null;
    private ProcessState processState = ProcessState.STOPPED;
    private final Pattern versionPattern = Pattern.compile("Aeon ('[^\\)]+\\))");
    private final Pattern heightPattern = Pattern.compile("Height: (\\d+), target: (\\d+)");
    private final Pattern downloadingPattern =Pattern.compile("Downloading at (\\d+)");
    private final Pattern peersPattern =Pattern.compile("(\\d+) peers");
    private final Pattern stoppedPattern =Pattern.compile("Cryptonote protocol stopped successfully");
    public Daemon(String options){
        this.options = options;
    }
    public String start()  {
        try  {
            logs = new StringBuffer();
            logsBuilder = new StringBuilder();
            Log.d(TAG , MainActivity.BINARY_PATH+" "+options);
            process = Runtime.getRuntime().exec(MainActivity.BINARY_PATH+" "+options);
            try {
                Field field = process.getClass().getDeclaredField("pid");
                field.setAccessible(true);
            } catch (Throwable e) {
                Log.e(TAG,e.getMessage());
            }
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
            return e.getMessage();
        }
        processState = ProcessState.STARTING;
        return null;
    }
    public void write(String cmd){
        read();
        try {
            if (writer != null) {
                writer.write(cmd+"\n");
                writer.flush();
                logsBuilder.append(">>> "+cmd+"\n");
            }
        } catch (IOException e) {
            Log.e(TAG,"write: " +e.getMessage());
        }
    }
    private void read(){
        try {
            if (reader != null && reader.ready()) {
                char[] buffer = new char[16384];
                while (reader.ready()) {
                    int read = reader.read(buffer);
                    if (read > 0) {
                        logsBuilder.append(buffer, 0, read);
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
        }
    }
    public String updateStatus() {
        Log.d(TAG,"updateStatus");
        read();
        Matcher m = versionPattern.matcher(logsBuilder.toString());
        if (m.find()) {
            version = m.group(1);
            Log.v(TAG,version);
        }
        m =  heightPattern.matcher(logsBuilder.toString());
        if (m.find()) {
            height = m.group(1);
            target = m.group(2);
            Log.v(TAG,height);
        }
        m = downloadingPattern.matcher(logsBuilder.toString());
        if (m.find()) {
            downloading = m.group(1);
            Log.v(TAG,downloading);
        }
        m = peersPattern.matcher(logsBuilder.toString());
        if (m.find()) {
            peers = m.group(1);
            Log.v(TAG,peers);
        }
        m = stoppedPattern.matcher(logsBuilder.toString());
        if (m.find()) {
            processState = ProcessState.STOPPED;
        }
        logs.append(logsBuilder.toString());
        if (logs.length() > MAX_LOG_SIZE)
            logs.delete(0,logs.length() - MAX_LOG_SIZE);
        Log.d(TAG, "logsBuilder.toString(): " +logsBuilder.toString());
        String out = logsBuilder.toString();
        logsBuilder = new StringBuilder();
        return out;
    }
    public void exit() {
        Log.d(TAG,"exit");
        // sending an exit twice might make it waiting forever
        if (processState == ProcessState.STOPPING)
            return;
        write("exit");
        height = "";
        downloading = "";
        peers = "";
        processState = ProcessState.STOPPING;
    }
    public String getPeers() {
        return peers;
    }
    public String getHeight() {
        return height;
    }
    public String getTarget() {
        return target;
    }
    public String getVersion() {
        return version;
    }
    public String getDownloading() {
        return downloading;
    }
    public String getLogs() {
        return logs == null ? "" : logs.toString();
    }
    public boolean isStopped() {
        return processState == ProcessState.STOPPED;
    }
    public boolean isAlive() {
        Log.d(TAG,"isAlive");
        try {
            ProcessBuilder builder = new ProcessBuilder("ps"); // | /system/bin/grep aeond");
            builder.redirectErrorStream(true);
            Map<String, String> env = builder.environment();
            env.put("PATH","/bin:/system/bin");
            Process process = builder.start();
            InputStreamReader isReader = new InputStreamReader(process.getInputStream());
            BufferedReader bReader = new BufferedReader(isReader);
            String strLine;
            while ((strLine = bReader.readLine()) != null) {
                Log.d(TAG,strLine);
                int i = strLine.lastIndexOf("aeond");
                if (i > 0 && !(strLine.length() > i+5 && strLine.charAt(i+5) == 'a')) {
                    processState = ProcessState.RUNNING;
                    return true;
                }
            }
        } catch (Exception ex) {
            Log.e(TAG,"Got exception using getting the PID"+ ex.getMessage());
        }
        processState = ProcessState.STOPPED;
        return false;
    }
}
