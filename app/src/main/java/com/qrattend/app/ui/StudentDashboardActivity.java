package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.SubjectGroupAdapter;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StudentDashboardActivity extends AppCompatActivity {

    private TextView             tvAttendancePercent, tvEmptySubjects;
    private ProgressBar          progressAttendance;
    private AttendanceDonutView  donutChart;
    private RecyclerView         rvSubjects;
    private ExtendedFloatingActionButton fabScanQR;
    private SubjectGroupAdapter  adapter;

    private AuthManager         authManager;
    private StudentRepository   studentRepo;
    private AttendanceRepository attendanceRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvAttendancePercent = findViewById(R.id.tvAttendancePercent);
        tvEmptySubjects     = findViewById(R.id.tvEmptySubjects);
        progressAttendance  = findViewById(R.id.progressAttendance);
        donutChart          = findViewById(R.id.donutChart);
        rvSubjects          = findViewById(R.id.rvSubjects);
        fabScanQR           = findViewById(R.id.fabScanQR);

        authManager    = new AuthManager();
        studentRepo    = new StudentRepository();
        attendanceRepo = new AttendanceRepository();

        // Adapter — tapping a subject opens SubjectAttendanceActivity
        adapter = new SubjectGroupAdapter(subject -> {
            Intent intent = new Intent(this, SubjectAttendanceActivity.class);
            intent.putExtra("subject_name", subject.subjectName);
            startActivity(intent);
        });
        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvSubjects.setAdapter(adapter);

        fabScanQR.setOnClickListener(v ->
                startActivity(new Intent(this, ScanQRActivity.class)));

        findViewById(R.id.cardScanQR).setOnClickListener(v ->
                startActivity(new Intent(this, ScanQRActivity.class)));

        findViewById(R.id.cardHistory).setOnClickListener(v ->
                startActivity(new Intent(this, AttendanceHistoryActivity.class)));

        findViewById(R.id.cardJoinClass).setOnClickListener(v ->
                startActivity(new Intent(this, JoinClassActivity.class)));

        findViewById(R.id.cardLeaveApplication).setOnClickListener(v ->
                startActivity(new Intent(this, LeaveApplicationActivity.class)));

        toolbar.setNavigationOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        // Toolbar title
        studentRepo.getStudent(uid, student -> {
            if (student != null && student.getName() != null) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(
                            getString(R.string.welcome_student, student.getName()));
                }
            }
        });

        // Attendance history → grouped by subject
        attendanceRepo.getStudentHistory(uid, records -> {
            runOnUiThread(() -> {
                if (records == null || records.isEmpty()) {
                    tvAttendancePercent.setText(getString(R.string.attendance_percentage, 0));
                    tvAttendancePercent.setTextColor(
                            ContextCompat.getColor(this, R.color.attendanceLow));
                    progressAttendance.setProgress(0);
                    if (donutChart != null) donutChart.setPercent(0);
                    tvEmptySubjects.setVisibility(View.VISIBLE);
                    rvSubjects.setVisibility(View.GONE);
                    return;
                }

                // ── Overall attendance ────────────────────────────────────
                int total   = records.size();
                int present = countPresent(records);
                int pct     = total > 0 ? (present * 100) / total : 0;

                tvAttendancePercent.setText(getString(R.string.attendance_percentage, pct));
                progressAttendance.setProgress(pct);
                if (donutChart != null) donutChart.setPercent(pct);
                int overallColor = pct >= 75 ? R.color.attendanceHigh
                        : pct >= 60 ? R.color.attendanceMid : R.color.attendanceLow;
                tvAttendancePercent.setTextColor(
                        ContextCompat.getColor(this, overallColor));
                progressAttendance.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(this, overallColor)));

                // ── Group by subject ──────────────────────────────────────
                // LinkedHashMap preserves insertion order for deterministic ordering
                Map<String, int[]> subjectMap = new LinkedHashMap<>();
                for (AttendanceRecord r : records) {
                    String subject = r.getSubject();
                    if (subject == null || subject.isEmpty()) {
                        subject = "Other";   // fallback for older records
                    }
                    int[] counts = subjectMap.getOrDefault(subject, new int[]{0, 0});
                    counts[1]++; // total
                    if (isPresent(r)) counts[0]++; // present
                    subjectMap.put(subject, counts);
                }

                List<SubjectGroupAdapter.SubjectGroup> groups = new ArrayList<>();
                for (Map.Entry<String, int[]> entry : subjectMap.entrySet()) {
                    groups.add(new SubjectGroupAdapter.SubjectGroup(
                            entry.getKey(),
                            entry.getValue()[0],  // presentCount
                            entry.getValue()[1]   // totalCount
                    ));
                }

                // Sort by subject name alphabetically for a consistent order
                groups.sort((a, b) -> a.subjectName.compareToIgnoreCase(b.subjectName));

                tvEmptySubjects.setVisibility(View.GONE);
                rvSubjects.setVisibility(View.VISIBLE);
                adapter.updateList(groups);
            });
        });
    }

    private int countPresent(List<AttendanceRecord> records) {
        int n = 0;
        for (AttendanceRecord r : records) if (isPresent(r)) n++;
        return n;
    }

    private boolean isPresent(AttendanceRecord r) {
        return Constants.STATUS_PRESENT.equals(r.getStatus())
                || "Present".equals(r.getStatus());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.attendance_history);
        menu.add(0, 2, 1, R.string.settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case 1:
                startActivity(new Intent(this, AttendanceHistoryActivity.class));
                return true;
            case 2:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
