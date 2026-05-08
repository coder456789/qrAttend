package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.data.repository.TeacherRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.ClassGroupAdapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Teacher dashboard.
 * <p>
 * "My Classes" shows unique class+subject combinations (e.g. "OOP — SY IT")
 * grouped from all sessions created by this teacher. Tapping a class row
 * opens {@link ClassSessionsActivity}, which lists every session for that class.
 * </p>
 */
public class TeacherDashboardActivity extends AppCompatActivity {

    private TextView tvTeacherName, tvTeacherSubject, tvEmptyClasses;
    private RecyclerView rvClasses;
    private ExtendedFloatingActionButton fabStartSession;
    private ClassGroupAdapter adapter;

    private AuthManager authManager;
    private TeacherRepository teacherRepo;
    private SessionRepository sessionRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvTeacherName  = findViewById(R.id.tvTeacherName);
        tvTeacherSubject = findViewById(R.id.tvTeacherSubject);
        tvEmptyClasses = findViewById(R.id.tvEmptyClasses);
        rvClasses      = findViewById(R.id.rvClasses);
        fabStartSession = findViewById(R.id.fabStartSession);

        authManager = new AuthManager();
        teacherRepo = new TeacherRepository();
        sessionRepo = new SessionRepository();

        adapter = new ClassGroupAdapter(group -> {
            // Open the class-level session list
            Intent intent = new Intent(this, ClassSessionsActivity.class);
            intent.putExtra("class_id", group.classId);
            intent.putExtra("subject", group.subject);
            intent.putExtra("class_name", group.className);
            startActivity(intent);
        });

        rvClasses.setLayoutManager(new LinearLayoutManager(this));
        rvClasses.setAdapter(adapter);

        fabStartSession.setOnClickListener(v ->
                startActivity(new Intent(this, StartSessionActivity.class)));

        View cardStart = findViewById(R.id.cardStartSession);
        if (cardStart != null) {
            cardStart.setOnClickListener(v ->
                    startActivity(new Intent(this, StartSessionActivity.class)));
        }

        View cardTimetable = findViewById(R.id.cardTimetable);
        if (cardTimetable != null) {
            cardTimetable.setOnClickListener(v ->
                    startActivity(new Intent(this, TimetableActivity.class)));
        }

        View cardLeaveApplications = findViewById(R.id.cardLeaveApplications);
        if (cardLeaveApplications != null) {
            cardLeaveApplications.setOnClickListener(v ->
                    startActivity(new Intent(this, LeaveApplicationsActivity.class)));
        }

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
        startDeviceWatcher();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopDeviceWatcher();
    }

    // ── Single-device enforcement ───────────────────────────────────────

    private com.google.firebase.firestore.ListenerRegistration deviceWatcher;

    /**
     * Listens to the teacher's Firestore doc in real-time.
     * If another device logs in and overwrites {@code deviceId}, this device
     * detects the mismatch and force-logouts.
     */
    private void startDeviceWatcher() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        String thisDeviceId = com.qrattend.app.security.DeviceFingerprint.getFingerprint(this);

        deviceWatcher = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(com.qrattend.app.utils.Constants.TEACHERS)
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null || !snapshot.exists()) return;

                    String currentDeviceId = snapshot.getString("deviceId");
                    if (currentDeviceId != null && !currentDeviceId.equals(thisDeviceId)) {
                        // Another device took over → force logout
                        runOnUiThread(() -> {
                            stopDeviceWatcher();
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Logged Out")
                                    .setMessage("Your account has been logged in from another device.\n\n"
                                            + "You have been logged out of this device automatically.")
                                    .setPositiveButton(getString(R.string.ok), (d, w) -> {
                                        authManager.logout(TeacherDashboardActivity.this);
                                        Intent intent = new Intent(this, LoginActivity.class);
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .setCancelable(false)
                                    .show();
                        });
                    }
                });
    }

    private void stopDeviceWatcher() {
        if (deviceWatcher != null) {
            deviceWatcher.remove();
            deviceWatcher = null;
        }
    }

    private void loadData() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        teacherRepo.getTeacher(uid, teacher -> {
            if (teacher != null) {
                runOnUiThread(() -> {
                    tvTeacherName.setText(getString(R.string.welcome_teacher, teacher.getName()));
                    tvTeacherSubject.setText(teacher.getSubject());
                });
            }
        });

        // Get ALL sessions by this teacher and GROUP them by unique class
        sessionRepo.getAllSessionsByTeacher(uid, sessions -> {
            runOnUiThread(() -> {
                if (sessions == null || sessions.isEmpty()) {
                    tvEmptyClasses.setVisibility(View.VISIBLE);
                    tvEmptyClasses.setText("No classes yet.\nStart a session to create your first class.");
                    rvClasses.setVisibility(View.GONE);
                    return;
                }

                // Group sessions by classId (className) → deduplicated class list
                Map<String, ClassGroupAdapter.ClassGroup> groupMap = new LinkedHashMap<>();
                for (AttendanceSession s : sessions) {
                    String key = s.getClassId(); // className is used as classId
                    if (key == null) continue;
                    ClassGroupAdapter.ClassGroup g = groupMap.get(key);
                    if (g == null) {
                        g = new ClassGroupAdapter.ClassGroup();
                        g.classId   = key;
                        g.className = s.getClassName() != null ? s.getClassName() : key;
                        g.subject   = s.getSubject()   != null ? s.getSubject()   : "";
                        g.sessionCount = 0;
                        g.activeCount  = 0;
                    }
                    g.sessionCount++;
                    if (s.isActive()) g.activeCount++;
                    groupMap.put(key, g);
                }

                List<ClassGroupAdapter.ClassGroup> groups = new ArrayList<>(groupMap.values());

                if (groups.isEmpty()) {
                    tvEmptyClasses.setVisibility(View.VISIBLE);
                    rvClasses.setVisibility(View.GONE);
                } else {
                    tvEmptyClasses.setVisibility(View.GONE);
                    rvClasses.setVisibility(View.VISIBLE);
                    adapter.updateList(groups);
                }
            });
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
