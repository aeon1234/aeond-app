package com.aeon.aeond.app.model;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.aeon.aeond.app.MainActivity;
import com.aeon.aeond.app.R;

public class DaemonService extends Service {
    private static final String TAG = DaemonService.class.getSimpleName();
    private static Daemon daemon = null;
    private static Thread thread = null;
    private static final long RefreshInterval = 500;
    private int counter = 30;
    private static final int SendSyncCmd = 40;
    public DaemonService() {
    }
    private void setupNotification(){
        //https://github.com/greenaddress/abcore/blob/master/app/src/main/java/com/greenaddress/abcore/ABCoreService.java
        final Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);
        final PendingIntent pI;
        pI = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_ONE_SHOT);
        final NotificationManager nM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final Notification.Builder b = new Notification.Builder(this)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.app_name)+" is running.")
                .setContentIntent(pI)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final int importance = NotificationManager.IMPORTANCE_LOW;
            final NotificationChannel mChannel = new NotificationChannel("channel_00", "Aeon daemon", importance);
            nM.createNotificationChannel(mChannel);
            b.setChannelId("channel_00");
        }
        final Notification n = b.build();
        startForeground(101, n);
    }
    @Override
    public void onDestroy() {
        daemon.exit();
        thread.interrupt();
        super.onDestroy();
    }
    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null)
            return START_STICKY;
        setupNotification();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"run");
                while (true) {
                    if (daemon == null) {
                        daemon = new Daemon(intent.getStringExtra("options"));
                    }
                    if (daemon.isStopped()) {
                        String status = daemon.start();
                        if (status != null) {
                            daemon.updateStatus();
                        }
                    } else if (!daemon.isStopped()) {
                        if (counter >= SendSyncCmd) {
                            daemon.write("sync_info");
                            counter = 0;
                        }
                        String logsNew = daemon.updateStatus();
                        if (!logsNew.equals("") ){
                            if(MainActivity.adapter!=null) {
                                if (MainActivity.adapter.getItemCount() == 0) {
                                    MainActivity.adapter.addItem(daemon.getLogs());
                                } else {
                                    MainActivity.adapter.addItem(logsNew);
                                }
                            }
                        }
                    }
                    try {
                        Thread.sleep(RefreshInterval);
                    } catch (InterruptedException e) {
                        while(!daemon.isStopped()) {
                            if (counter >= SendSyncCmd) {
                                daemon.write("sync_info");
                                counter = 0;
                            }
                            String logs = daemon.updateStatus();
                            if (!logs.equals("")) {
                                if (MainActivity.adapter != null) {
                                    if (MainActivity.adapter.getItemCount() == 0) {
                                        MainActivity.adapter.addItem(daemon.getLogs());
                                    } else {
                                        MainActivity.adapter.addItem(logs);
                                    }
                                }
                            }
                        }
                        return;
                    }
                    counter ++;
                }
            }
        });
        thread.start();
        return START_STICKY;
    }

    public static Daemon getDaemon() {
        return daemon;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
