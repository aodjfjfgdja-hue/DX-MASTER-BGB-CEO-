package com.dx.master;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingService extends Service {

    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams lp;
    private boolean on = false;
    private boolean added = false;

    private final BroadcastReceiver stateRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            on = i.getBooleanExtra("on", false);
            paint();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("dx", "DX MASTER", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "dx")
                .setContentTitle("DX MASTER VPN KING")
                .setContentText("Mic panel running")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(1, n);
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        bubble = new TextView(this);
        bubble.setText("DX");
        bubble.setTextColor(Color.WHITE);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setTextSize(18);
        bubble.setGravity(Gravity.CENTER);
        paint();

        lp = new WindowManager.LayoutParams(
                150, 150,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 20;
        lp.y = 300;

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        touchX = e.getRawX();
                        touchY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - touchX);
                        int dy = (int) (e.getRawY() - touchY);
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            moved = true;
                        }
                        lp.x = startX + dx;
                        lp.y = startY + dy;
                        wm.updateViewLayout(bubble, lp);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            Intent i = new Intent("DX_TOGGLE");
                            i.setPackage(getPackageName());
                            sendBroadcast(i);
                        }
                        return true;
                }
                return false;
            }
        });

        wm.addView(bubble, lp);
        added = true;

        IntentFilter f = new IntentFilter("DX_STATE");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateRx, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stateRx, f);
        }
    }

    private void paint() {
        if (bubble == null) return;
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(on ? 0xFF00E676 : 0xFFFF1744);
        g.setStroke(8, 0xFF00B0FF);
        bubble.setBackground(g);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (added) {
            try {
                wm.removeView(bubble);
            } catch (Exception e) {
            }
            try {
                unregisterReceiver(stateRx);
            } catch (Exception e) {
            }
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
