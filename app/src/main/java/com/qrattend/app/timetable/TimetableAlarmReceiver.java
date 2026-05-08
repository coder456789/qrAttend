package com.qrattend.app.timetable;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.qrattend.app.R;
import com.qrattend.app.ui.TimetableActivity;

/**
 * Receives the alarm, posts the "class in 5 minutes" system notification,
 * then re-schedules itself for the SAME slot next week.
 */
public class TimetableAlarmReceiver extends BroadcastReceiver {

    public static final String EXTRA_SUBJECT    = "subject";
    public static final String EXTRA_CLASS_NAME = "className";
    public static final String EXTRA_ROOM_NO    = "roomNo";
    public static final String EXTRA_START_TIME = "startTime";  // 12h formatted
    public static final String EXTRA_ENTRY_ID   = "entryId";

    private static final String CHANNEL_ID   = "qrattend_timetable";
    private static final String CHANNEL_NAME = "Class Reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        // ── Extract extras ────────────────────────────────────────────────
        String subject   = getStr(intent, EXTRA_SUBJECT,    "Class");
        String className = getStr(intent, EXTRA_CLASS_NAME, "");
        String roomNo    = getStr(intent, EXTRA_ROOM_NO,    "");
        String startTime = getStr(intent, EXTRA_START_TIME, "");

        int dayOfWeek   = intent.getIntExtra(TimetableAlarmScheduler.EXTRA_DAY_OF_WEEK,  1);
        int startHour   = intent.getIntExtra(TimetableAlarmScheduler.EXTRA_START_HOUR,   8);
        int startMinute = intent.getIntExtra(TimetableAlarmScheduler.EXTRA_START_MINUTE, 0);
        int requestCode = intent.getIntExtra(TimetableAlarmScheduler.EXTRA_REQUEST_CODE, 0);

        // ── Show system notification ──────────────────────────────────────
        createChannel(context);

        Intent tap = new Intent(context, TimetableActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context, requestCode, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = "📚 Class starts in 5 minutes!";

        // Format: Subject  ·  Class  ·  Room   Time
        StringBuilder body = new StringBuilder(subject);
        if (!className.isEmpty()) body.append("  ·  ").append(className);
        if (!roomNo.isEmpty())    body.append("  ·  ").append(roomNo);
        body.append("   ").append(startTime);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_bell)
                .setContentTitle(title)
                .setContentText(body.toString())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.toString()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            int notifId = 9000 + (Math.abs(requestCode) % 1000);
            nm.notify(notifId, builder.build());
        }

        // ── Re-schedule for NEXT week ─────────────────────────────────────
        TimetableAlarmScheduler.rescheduleNextWeek(
                context, dayOfWeek, startHour, startMinute, requestCode, intent);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String getStr(Intent intent, String key, String fallback) {
        String v = intent.getStringExtra(key);
        return (v != null && !v.isEmpty()) ? v : fallback;
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Reminds you 5 minutes before each scheduled class");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }
}
