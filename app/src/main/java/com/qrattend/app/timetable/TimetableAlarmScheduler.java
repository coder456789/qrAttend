package com.qrattend.app.timetable;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.qrattend.app.data.model.TimetableEntry;

import java.util.Calendar;
import java.util.List;

/**
 * Schedules weekly background timers that trigger the class-reminder notification.
 *
 * Uses setInexactRepeating with a 7-day interval.
 * ✔ No special permissions needed (no SCHEDULE_EXACT_ALARM).
 * ✔ Survives Doze mode (RTC_WAKEUP wakes device).
 * ✔ The notification that fires is a silent OS-level notification
 *   (exactly like WhatsApp) — no alarm sound.
 *
 * After each notification fires, TimetableAlarmReceiver re-schedules the
 * next week automatically.
 */
public class TimetableAlarmScheduler {

    private static final String TAG          = "TimetableAlarmScheduler";
    static final         int    LEAD_MINUTES = 5;
    private static final long   WEEK_MS      = 7L * 24 * 60 * 60 * 1000; // 7 days in ms

    // Extras stored in intent so receiver can reschedule itself next week
    static final String EXTRA_DAY_OF_WEEK  = "ttDay";
    static final String EXTRA_START_HOUR   = "ttHour";
    static final String EXTRA_START_MINUTE = "ttMinute";
    static final String EXTRA_REQUEST_CODE = "ttReqCode";

    // ── Public API ─────────────────────────────────────────────────────────

    public static void scheduleAll(Context ctx, List<TimetableEntry> entries) {
        for (TimetableEntry e : entries) {
            if (e.isNotificationsEnabled()) scheduleEntry(ctx, e);
            else cancelEntry(ctx, e);
        }
    }

    public static void scheduleEntry(Context ctx, TimetableEntry entry) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildPI(ctx, entry);
        am.cancel(pi); // clear any old timer for this slot

        if (!entry.isNotificationsEnabled()) return;

        Calendar trigger = nextOccurrence(
                entry.getDayOfWeek(),
                entry.getStartHour(),
                entry.getStartMinute(),
                LEAD_MINUTES);

        // setInexactRepeating — no special permission, repeats every 7 days
        am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger.getTimeInMillis(),
                WEEK_MS,
                pi);

        Log.d(TAG, "Scheduled notification for [" + entry.getSubject()
                + "] at " + trigger.getTime());
    }

    /** Called from TimetableAlarmReceiver to re-schedule for next week. */
    static void rescheduleNextWeek(Context ctx, int dayOfWeek, int startHour,
                                   int startMinute, int requestCode,
                                   Intent originalIntent) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, requestCode, originalIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar trigger = nextOccurrence(dayOfWeek, startHour, startMinute, LEAD_MINUTES);

        am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger.getTimeInMillis(),
                WEEK_MS,
                pi);

        Log.d(TAG, "Re-scheduled for next week → " + trigger.getTime());
    }

    public static void cancelEntry(Context ctx, TimetableEntry entry) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(buildPI(ctx, entry));
    }

    public static void cancelAll(Context ctx, List<TimetableEntry> entries) {
        for (TimetableEntry e : entries) cancelEntry(ctx, e);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    static PendingIntent buildPI(Context ctx, TimetableEntry entry) {
        Intent intent = new Intent(ctx, TimetableAlarmReceiver.class);
        intent.setAction("com.qrattend.app.TIMETABLE_ALARM_" + entry.getAlarmRequestCode());

        // Notification display data
        intent.putExtra(TimetableAlarmReceiver.EXTRA_SUBJECT,
                entry.getSubject() != null ? entry.getSubject() : "");
        intent.putExtra(TimetableAlarmReceiver.EXTRA_CLASS_NAME,
                entry.getClassName() != null ? entry.getClassName() : "");
        intent.putExtra(TimetableAlarmReceiver.EXTRA_ROOM_NO,
                entry.getRoomNo() != null ? entry.getRoomNo() : "");
        intent.putExtra(TimetableAlarmReceiver.EXTRA_START_TIME,
                fmt12hStatic(entry.getStartHour(), entry.getStartMinute()));
        intent.putExtra(TimetableAlarmReceiver.EXTRA_ENTRY_ID,
                entry.getId() != null ? entry.getId() : "");

        // Rescheduling data (receiver uses these to re-set timer for next week)
        intent.putExtra(EXTRA_DAY_OF_WEEK,  entry.getDayOfWeek());
        intent.putExtra(EXTRA_START_HOUR,   entry.getStartHour());
        intent.putExtra(EXTRA_START_MINUTE, entry.getStartMinute());
        intent.putExtra(EXTRA_REQUEST_CODE, entry.getAlarmRequestCode());

        return PendingIntent.getBroadcast(
                ctx,
                entry.getAlarmRequestCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Returns the next Calendar occurrence of (dayOfWeek at hour:minute - leadMins)
     * that is strictly after right now.
     * dayOfWeek: 1=Monday … 5=Friday
     */
    static Calendar nextOccurrence(int dayOfWeek, int hour, int minute, int leadMins) {
        int notifyHour   = hour;
        int notifyMinute = minute - leadMins;
        if (notifyMinute < 0) {
            notifyMinute += 60;
            notifyHour   -= 1;
            if (notifyHour < 0) notifyHour += 24;
        }

        // Calendar.DAY_OF_WEEK: Sun=1, Mon=2, Tue=3, Wed=4, Thu=5, Fri=6, Sat=7
        int calDay = dayOfWeek + 1; // 1(Mon) → 2(Calendar.MONDAY)

        Calendar now     = Calendar.getInstance();
        Calendar trigger = Calendar.getInstance();
        trigger.set(Calendar.DAY_OF_WEEK,  calDay);
        trigger.set(Calendar.HOUR_OF_DAY,  notifyHour);
        trigger.set(Calendar.MINUTE,       notifyMinute);
        trigger.set(Calendar.SECOND,       0);
        trigger.set(Calendar.MILLISECOND,  0);

        if (!trigger.after(now)) {
            trigger.add(Calendar.DAY_OF_YEAR, 7);
        }
        return trigger;
    }

    /** Formats a 24h hour+minute into "h:mm AM/PM" for use in notification text. */
    static String fmt12hStatic(int hour24, int minute) {
        String amPm   = hour24 < 12 ? "AM" : "PM";
        int    hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format("%d:%02d %s", hour12, minute, amPm);
    }
}
