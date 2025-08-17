package com.android.dialer.callrecord.impl;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.dialer.R;
import com.android.incallui.InCallPresenter;
import com.android.incallui.call.CallList;
import com.android.incallui.call.CallRecorder;
import com.android.incallui.call.DialerCall;

/**
 * Foreground service that shows a draggable overlay bubble allowing quick call recording toggle.
 */
public class CallRecordOverlayService extends Service {

    private static final String NOTIF_CHANNEL_ID = "call_record_overlay";
    private static final int NOTIF_ID = 0xC011EC; // arbitrary

    private WindowManager windowManager;
    private View bubbleView;

    public static void start(Context context) {
        Context app = context.getApplicationContext();
        Intent i = new Intent(app, CallRecordOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.startForegroundService(i);
        } else {
            app.startService(i);
        }
    }

    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        app.stopService(new Intent(app, CallRecordOverlayService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Settings gate
        if (!isBubbleEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Overlay permission gate
        if (!canDrawOverlays()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // Only show if there is an active call
        DialerCall active = CallList.getInstance().getActiveCall();
        if (active == null || active.getState() != com.android.incallui.call.state.DialerCallState.ACTIVE) {
            stopSelf();
            return START_NOT_STICKY;
        }

        ensureForegroundNotification();
        showBubble();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        hideBubble();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isBubbleEnabled() {
        String key = getString(R.string.call_recording_bubble_key);
        return getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getBoolean(key, true);
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void ensureForegroundNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    getString(R.string.call_recording_category_title),
                    NotificationManager.IMPORTANCE_MIN);
            nm.createNotificationChannel(ch);
        }
        Intent dialerIntent = InCallPresenter.getInstance().getInCallState().isConnectingOrConnected()
                ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS)
                : new Intent(Intent.ACTION_MAIN);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, dialerIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, NOTIF_CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.quantum_ic_record_white_36)
                .setOngoing(true)
                .setContentTitle(getString(R.string.call_recording_category_title))
                .setContentText(getString(R.string.onscreenCallRecordText))
                .setContentIntent(pi);
        startForeground(NOTIF_ID, b.build());
    }

    private void showBubble() {
        if (bubbleView != null) return;
        final ImageView bubble = new ImageView(this);
        bubble.setImageResource(R.drawable.quantum_ic_record_white_36);
        bubble.setContentDescription(getString(R.string.onscreenCallRecordText));
        bubble.setBackgroundResource(R.drawable.btn_default_material);
        int size = getResources().getDimensionPixelSize(R.dimen.fab_min_touch_target);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 24;
        params.y = 200;

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX - (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubble, params);
                        return true;
                }
                return false;
            }
        });

        bubble.setOnClickListener(v -> toggleRecording());

        windowManager.addView(bubble, params);
        bubbleView = bubble;
    }

    private void hideBubble() {
        if (bubbleView != null) {
            windowManager.removeView(bubbleView);
            bubbleView = null;
        }
        stopForeground(true);
    }

    private void toggleRecording() {
        CallRecorder recorder = CallRecorder.getInstance();
        if (recorder.isRecording()) {
            recorder.finishRecording();
            return;
        }
        DialerCall call = CallList.getInstance().getActiveCall();
        if (call != null && call.getState() == com.android.incallui.call.state.DialerCallState.ACTIVE) {
            recorder.startRecording(call.getNumber(), call.getCreationTimeMillis());
        }
    }
}