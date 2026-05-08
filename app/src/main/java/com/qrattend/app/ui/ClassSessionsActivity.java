package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.SessionListAdapter;
import com.qrattend.app.utils.CsvExporter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows all sessions for a specific class (e.g. "OOP — SY IT").
 * <p>
 * Reached from the teacher dashboard when a class group row is tapped.
 * The teacher can tap any session to view its per-session records,
 * or tap "Export CSV" to export a whole-class attendance report.
 * </p>
 */
public class ClassSessionsActivity extends AppCompatActivity {

    private RecyclerView rvSessions;
    private TextView tvTitle, tvEmpty;
    private MaterialButton btnExportClassCsv;
    private ProgressBar progressBar;

    private SessionListAdapter adapter;
    private SessionRepository sessionRepo;
    private AttendanceRepository attendanceRepo;
    private AuthManager authManager;

    private String classId, subject, className;
    private List<AttendanceSession> allSessions = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_sessions);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTitle   = findViewById(R.id.tvClassTitle);
        tvEmpty   = findViewById(R.id.tvEmpty);
        rvSessions = findViewById(R.id.rvSessions);
        btnExportClassCsv = findViewById(R.id.btnExportClassCsv);
        progressBar = findViewById(R.id.progressBar);

        authManager    = new AuthManager();
        sessionRepo    = new SessionRepository();
        attendanceRepo = new AttendanceRepository();

        classId   = getIntent().getStringExtra("class_id");
        subject   = getIntent().getStringExtra("subject");
        className = getIntent().getStringExtra("class_name");

        tvTitle.setText((subject != null ? subject : "") + " — " + (className != null ? className : ""));

        adapter = new SessionListAdapter(session -> {
            if (session.getSessionId() == null) return;
            Intent intent = new Intent(this, SessionAttendanceActivity.class);
            intent.putExtra("session_id", session.getSessionId());
            startActivity(intent);
        });

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(adapter);

        btnExportClassCsv.setOnClickListener(v -> exportWholeClassCsv());

        loadSessions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSessions();
    }

    private void loadSessions() {
        String uid = authManager.getCurrentUserId();
        if (uid == null || classId == null) return;

        progressBar.setVisibility(View.VISIBLE);

        sessionRepo.getSessionsByClass(classId, uid, sessions -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                allSessions = sessions != null ? sessions : new ArrayList<>();

                if (allSessions.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvSessions.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvSessions.setVisibility(View.VISIBLE);
                    adapter.updateList(allSessions);
                }
            });
        });
    }

    /**
     * Export whole-class attendance as CSV.
     * Format: Student Name | PRN | Date1 | Date2 | ... | Attendance %
     */
    private void exportWholeClassCsv() {
        if (allSessions.isEmpty()) {
            Toast.makeText(this, "No sessions to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnExportClassCsv.setEnabled(false);

        // Collect records from every session
        final int totalSessions = allSessions.size();
        final List<List<AttendanceRecord>> allRecordBuckets = new ArrayList<>();
        final int[] completed = {0};

        for (int i = 0; i < totalSessions; i++) {
            allRecordBuckets.add(new ArrayList<>()); // placeholder
            final int idx = i;
            String sid = allSessions.get(i).getSessionId();
            attendanceRepo.getSessionRecords(sid, records -> {
                allRecordBuckets.set(idx, records != null ? records : new ArrayList<>());
                completed[0]++;

                if (completed[0] == totalSessions) {
                    // All sessions loaded — generate whole-class CSV
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnExportClassCsv.setEnabled(true);

                        String label = (subject != null ? subject : "") + "_" + (className != null ? className : "");
                        CsvExporter.exportWholeClassCsv(
                                ClassSessionsActivity.this,
                                allSessions,
                                allRecordBuckets,
                                label);
                    });
                }
            });
        }
    }
}
