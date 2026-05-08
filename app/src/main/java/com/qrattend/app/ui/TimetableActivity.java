package com.qrattend.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.qrattend.app.R;
import com.qrattend.app.data.model.TimetableEntry;
import com.qrattend.app.data.repository.TimetableRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.timetable.TimetableAlarmScheduler;
import com.qrattend.app.ui.adapters.TimetableEntryAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Teacher's weekly timetable screen.
 * Tabs → Mon / Tue / Wed / Thu / Fri
 * Each tab shows the classes for that day; FAB opens an Add/Edit dialog.
 */
public class TimetableActivity extends AppCompatActivity
        implements TimetableEntryAdapter.OnEntryActionListener {

    private static final String[] DAY_LABELS = {"Mon", "Tue", "Wed", "Thu", "Fri"};

    private TabLayout        tabLayout;
    private RecyclerView     recyclerView;
    private TextView         tvEmpty;
    private FloatingActionButton fabAdd;

    private TimetableEntryAdapter adapter;
    private TimetableRepository   repo;
    private AuthManager           authManager;

    private List<TimetableEntry> allEntries = new ArrayList<>();
    private int selectedDay = 1; // 1 = Monday

    // Runtime notification permission launcher (Android 13+)
    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (!granted) {
                            Toast.makeText(this,
                                    "Notification permission denied. " +
                                    "You won't receive class reminders.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timetable);

        Toolbar toolbar = findViewById(R.id.toolbarTimetable);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Timetable");
        }

        tabLayout    = findViewById(R.id.tabLayoutDays);
        recyclerView = findViewById(R.id.rvTimetableEntries);
        tvEmpty      = findViewById(R.id.tvTimetableEmpty);
        fabAdd       = findViewById(R.id.fabAddEntry);

        authManager = new AuthManager();
        repo        = new TimetableRepository();

        adapter = new TimetableEntryAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Build day tabs
        for (String label : DAY_LABELS) {
            tabLayout.addTab(tabLayout.newTab().setText(label));
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                selectedDay = tab.getPosition() + 1; // 1-indexed
                refreshList();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Auto-select today's day (Mon=1 … Fri=5; weekends → Monday)
        int calToday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        // Calendar: Sun=1, Mon=2, Tue=3, Wed=4, Thu=5, Fri=6, Sat=7
        if (calToday >= Calendar.MONDAY && calToday <= Calendar.FRIDAY) {
            selectedDay = calToday - 1; // Mon→1, Tue→2 … Fri→5
        } else {
            selectedDay = 1; // weekend → show Monday
        }
        TabLayout.Tab todayTab = tabLayout.getTabAt(selectedDay - 1);
        if (todayTab != null) todayTab.select();

        fabAdd.setOnClickListener(v -> showEntryDialog(null));

        // Request POST_NOTIFICATIONS permission on Android 13+
        requestNotificationPermissionIfNeeded();

        loadEntries();
    }

    /** Asks for POST_NOTIFICATIONS permission on Android 13+ if not already granted. */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEntries();
    }

    // ── Data ─────────────────────────────────────────────────────────────

    private void loadEntries() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        repo.getEntriesForTeacher(uid, entries -> runOnUiThread(() -> {
            allEntries = entries != null ? entries : new ArrayList<>();
            // Re-schedule alarms whenever we load (covers boot + manual refresh)
            TimetableAlarmScheduler.scheduleAll(this, allEntries);
            refreshList();
        }));
    }

    private void refreshList() {
        List<TimetableEntry> dayEntries = new ArrayList<>();
        for (TimetableEntry e : allEntries) {
            if (e.getDayOfWeek() == selectedDay) dayEntries.add(e);
        }
        // Sort by start time
        dayEntries.sort((a, b) -> {
            int ha = a.getStartHour() * 60 + a.getStartMinute();
            int hb = b.getStartHour() * 60 + b.getStartMinute();
            return Integer.compare(ha, hb);
        });

        adapter.setEntries(dayEntries);
        tvEmpty.setVisibility(dayEntries.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(dayEntries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────

    private void showEntryDialog(TimetableEntry existing) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_timetable_entry, null);

        EditText etSubject   = dialogView.findViewById(R.id.etDialogSubject);
        EditText etClass     = dialogView.findViewById(R.id.etDialogClass);
        EditText etRoomNo    = dialogView.findViewById(R.id.etDialogRoomNo);
        TextView tvStartTime = dialogView.findViewById(R.id.tvDialogStartTime);
        TextView tvEndTime   = dialogView.findViewById(R.id.tvDialogEndTime);

        // State holders
        final int[] startH = {8},  startM = {0};
        final int[] endH   = {9},  endM   = {0};

        if (existing != null) {
            etSubject.setText(existing.getSubject());
            etClass.setText(existing.getClassName() != null ? existing.getClassName() : "");
            if (existing.getRoomNo() != null) etRoomNo.setText(existing.getRoomNo());
            startH[0] = existing.getStartHour();
            startM[0] = existing.getStartMinute();
            endH[0]   = existing.getEndHour();
            endM[0]   = existing.getEndMinute();
        }

        // Update time labels (12-hour AM/PM display)
        Runnable updateLabels = () -> {
            tvStartTime.setText(fmt12h(startH[0], startM[0]));
            tvEndTime.setText(fmt12h(endH[0], endM[0]));
        };
        updateLabels.run();

        tvStartTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .setHour(startH[0])
                    .setMinute(startM[0])
                    .setTitleText("Select start time")
                    .build();
            picker.addOnPositiveButtonClickListener(btn -> {
                startH[0] = picker.getHour();
                startM[0] = picker.getMinute();
                updateLabels.run();
            });
            picker.show(getSupportFragmentManager(), "start_time");
        });

        tvEndTime.setOnClickListener(v -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                    .setHour(endH[0])
                    .setMinute(endM[0])
                    .setTitleText("Select end time")
                    .build();
            picker.addOnPositiveButtonClickListener(btn -> {
                endH[0] = picker.getHour();
                endM[0] = picker.getMinute();
                updateLabels.run();
            });
            picker.show(getSupportFragmentManager(), "end_time");
        });

        String title = existing == null ? "Add Class" : "Edit Class";

        // Use create()+show() so we can prevent auto-dismiss on validation failure
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("Save", null)   // null → we handle click manually
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        // Override positive button AFTER show() to prevent auto-dismiss
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btnView -> {
            String subject = etSubject.getText().toString().trim();
            String cls     = etClass.getText().toString().trim();
            String roomNo  = etRoomNo.getText().toString().trim();

            if (TextUtils.isEmpty(subject)) {
                etSubject.setError("Subject is required");
                etSubject.requestFocus();
                return; // dialog stays open
            }

            int startTotal = startH[0] * 60 + startM[0];
            int endTotal   = endH[0]   * 60 + endM[0];
            if (endTotal <= startTotal) {
                Toast.makeText(this,
                        "End time must be after start time", Toast.LENGTH_SHORT).show();
                return; // dialog stays open
            }

            String uid = authManager.getCurrentUserId();
            if (uid == null) { dialog.dismiss(); return; }

            TimetableEntry entry = existing != null ? existing : new TimetableEntry();
            entry.setTeacherId(uid);
            entry.setDayOfWeek(selectedDay);
            entry.setSubject(subject);
            entry.setClassName(cls);
            entry.setRoomNo(roomNo);
            entry.setStartHour(startH[0]);
            entry.setStartMinute(startM[0]);
            entry.setEndHour(endH[0]);
            entry.setEndMinute(endM[0]);
            entry.setNotificationsEnabled(true);

            dialog.dismiss(); // safe to close now

            repo.saveEntry(uid, entry, (ok, newId) -> runOnUiThread(() -> {
                if (ok) {
                    if (newId != null) entry.setId(newId);
                    TimetableAlarmScheduler.scheduleEntry(this, entry);
                    Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
                    loadEntries();
                } else {
                    Toast.makeText(this,
                            "Save failed — ask your teammate to deploy Firestore rules.",
                            Toast.LENGTH_LONG).show();
                }
            }));
        });
    }

    // ── Adapter callbacks ─────────────────────────────────────────────────

    @Override
    public void onEdit(TimetableEntry entry) {
        showEntryDialog(entry);
    }

    @Override
    public void onDelete(TimetableEntry entry) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Entry")
                .setMessage("Remove " + entry.getSubject() + " from your timetable?")
                .setPositiveButton("Delete", (d, w) -> {
                    String uid = authManager.getCurrentUserId();
                    if (uid == null || entry.getId() == null) return;

                    TimetableAlarmScheduler.cancelEntry(this, entry);
                    repo.deleteEntry(uid, entry.getId(), ok -> runOnUiThread(() -> {
                        if (ok) {
                            Toast.makeText(this, "Entry deleted", Toast.LENGTH_SHORT).show();
                            loadEntries();
                        } else {
                            Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show();
                        }
                    }));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Formats a 24-hour (0-23) hour + minute into a 12-hour AM/PM string.
     * e.g. (14, 30) → "2:30 PM",  (8, 5) → "8:05 AM"
     */
    private String fmt12h(int hour24, int minute) {
        String amPm   = hour24 < 12 ? "AM" : "PM";
        int    hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(Locale.getDefault(), "%d:%02d %s", hour12, minute, amPm);
    }
}
