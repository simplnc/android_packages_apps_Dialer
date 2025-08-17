package com.android.dialer.callrecord;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.dialer.R;
import com.android.dialer.app.settings.SoundSettingsFragment;

/**
 * Service to handle scheduled periodic reminder notifications for auto-record
 * when it stays enabled for long periods.
 */
public class CallRecordingReminderService extends Service {
    private static final String TAG = "CallRecordingReminder";
    
    // Notification channel ID for Android 8.0+
    private static final String CHANNEL_ID = "call_recording_reminder";
    
    // Notification ID
    private static final int NOTIFICATION_ID = 1001;
    
    // Reminder intervals (in milliseconds)
    private static final long REMINDER_INTERVAL_1_WEEK = 7 * 24 * 60 * 60 * 1000L;
    private static final long REMINDER_INTERVAL_1_MONTH = 30 * 24 * 60 * 60 * 1000L;
    
    // Preference keys
    private static final String PREF_AUTO_RECORD_ENABLED_TIME = "auto_record_enabled_time";
    private static final String PREF_LAST_REMINDER_TIME = "last_reminder_time";
    private static final String PREF_REMINDER_DISMISSED = "reminder_dismissed";
    
    private AlarmManager alarmManager;
    private SharedPreferences prefs;
    
    @Override
    public void onCreate() {
        super.onCreate();
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        prefs = getSharedPreferences(getPackageName() + "_preferences", Context.MODE_PRIVATE);
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, String name, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("CHECK_REMINDER".equals(action)) {
                checkAndShowReminder();
            } else if ("DISMISS_REMINDER".equals(action)) {
                dismissReminder();
            } else if ("SCHEDULE_REMINDER".equals(action)) {
                scheduleReminder();
            }
        }
        return START_NOT_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    /**
     * Schedule the reminder alarm
     */
    private void scheduleReminder() {
        long autoRecordEnabledTime = prefs.getLong(PREF_AUTO_RECORD_ENABLED_TIME, 0);
        if (autoRecordEnabledTime == 0) {
            // First time auto-record is enabled
            prefs.edit().putLong(PREF_AUTO_RECORD_ENABLED_TIME, System.currentTimeMillis()).apply();
            autoRecordEnabledTime = System.currentTimeMillis();
        }
        
        // Calculate next reminder time
        long timeSinceEnabled = System.currentTimeMillis() - autoRecordEnabledTime;
        long nextReminderTime;
        
        if (timeSinceEnabled < REMINDER_INTERVAL_1_WEEK) {
            // Schedule for 1 week
            nextReminderTime = autoRecordEnabledTime + REMINDER_INTERVAL_1_WEEK;
        } else if (timeSinceEnabled < REMINDER_INTERVAL_1_MONTH) {
            // Schedule for 1 month
            nextReminderTime = autoRecordEnabledTime + REMINDER_INTERVAL_1_MONTH;
        } else {
            // Schedule for next month
            nextReminderTime = System.currentTimeMillis() + REMINDER_INTERVAL_1_MONTH;
        }
        
        Intent intent = new Intent(this, CallRecordingReminderService.class);
        intent.setAction("CHECK_REMINDER");
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + (nextReminderTime - System.currentTimeMillis()),
                pendingIntent);
    }
    
    /**
     * Check if reminder should be shown and display it
     */
    private void checkAndShowReminder() {
        if (!isAutoRecordEnabled()) {
            return;
        }
        
        if (prefs.getBoolean(PREF_REMINDER_DISMISSED, false)) {
            return;
        }
        
        long autoRecordEnabledTime = prefs.getLong(PREF_AUTO_RECORD_ENABLED_TIME, 0);
        if (autoRecordEnabledTime == 0) {
            return;
        }
        
        long timeSinceEnabled = System.currentTimeMillis() - autoRecordEnabledTime;
        long lastReminderTime = prefs.getLong(PREF_LAST_REMINDER_TIME, 0);
        
        // Check if enough time has passed since last reminder
        if (timeSinceEnabled >= REMINDER_INTERVAL_1_WEEK && 
            (System.currentTimeMillis() - lastReminderTime) >= REMINDER_INTERVAL_1_WEEK) {
            showReminderNotification();
        }
    }
    
    /**
     * Show the reminder notification
     */
    private void showReminderNotification() {
        Intent settingsIntent = new Intent(this, SoundSettingsFragment.class);
        PendingIntent settingsPendingIntent = PendingIntent.getActivity(this, 0, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        Intent dismissIntent = new Intent(this, CallRecordingReminderService.class);
        dismissIntent.setAction("DISMISS_REMINDER");
        PendingIntent dismissPendingIntent = PendingIntent.getService(this, 0, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.quantum_ic_record_white_36)
                .setContentTitle(getString(R.string.auto_record_reminder_title))
                .setContentText(getString(R.string.auto_record_reminder_text))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.auto_record_reminder_long_text)))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(R.drawable.quantum_ic_edit_vd_theme_24, 
                        getString(R.string.auto_record_reminder_settings), 
                        settingsPendingIntent)
                .addAction(R.drawable.quantum_ic_close_vd_theme_24, 
                        getString(R.string.auto_record_reminder_dismiss), 
                        dismissPendingIntent);
        
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
            prefs.edit().putLong(PREF_LAST_REMINDER_TIME, System.currentTimeMillis()).apply();
        }
    }
    
    /**
     * Dismiss the reminder permanently
     */
    private void dismissReminder() {
        prefs.edit().putBoolean(PREF_REMINDER_DISMISSED, true).apply();
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID);
    }
    
    /**
     * Check if auto-record is currently enabled
     */
    private boolean isAutoRecordEnabled() {
        try {
            return prefs.getBoolean(getString(R.string.call_recording_auto_key), false);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Create notification channel for Android 8.0+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.auto_record_reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.auto_record_reminder_channel_description));
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * Static method to schedule reminder when auto-record is enabled
     */
    public static void scheduleReminder(Context context) {
        Intent intent = new Intent(context, CallRecordingReminderService.class);
        intent.setAction("SCHEDULE_REMINDER");
        context.startService(intent);
    }
    
    /**
     * Static method to cancel reminder when auto-record is disabled
     */
    public static void cancelReminder(Context context) {
        Intent intent = new Intent(context, CallRecordingReminderService.class);
        intent.setAction("CANCEL_REMINDER");
        context.startService(intent);
    }
}
