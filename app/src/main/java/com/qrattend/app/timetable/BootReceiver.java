package com.qrattend.app.timetable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.qrattend.app.data.repository.TimetableRepository;

/**
 * Re-registers all timetable alarms after device reboot
 * (AlarmManager alarms are lost on reboot).
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            return;
        }

        Log.d(TAG, "Boot detected — re-scheduling timetable alarms");

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.d(TAG, "No logged-in teacher; skipping alarm restore");
            return;
        }

        String teacherId = user.getUid();
        new TimetableRepository().getEntriesForTeacher(teacherId, entries -> {
            if (entries != null && !entries.isEmpty()) {
                TimetableAlarmScheduler.scheduleAll(context, entries);
                Log.d(TAG, "Re-scheduled " + entries.size() + " alarm(s)");
            }
        });
    }
}
