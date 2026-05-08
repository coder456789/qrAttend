package com.qrattend.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.AttendanceRecordAdapter;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a student's session-by-session attendance for a single subject.
 * <p>
 * Reached from {@link StudentDashboardActivity} when the student taps a subject row.
 * Displays:
 * <ul>
 *   <li>Subject name as toolbar title</li>
 *   <li>Overall attendance % with a colour-coded progress bar</li>
 *   <li>A list of sessions in chronological order: "Session 1, Session 2, …"
 *       each with date/time and the student's status (Present / Absent / Leave / Rejected)</li>
 * </ul>
 * Intent extras required:
 * <ul>
 *   <li>{@code "subject_name"} — the subject string to filter history by</li>
 * </ul>
 */
public class SubjectAttendanceActivity extends AppCompatActivity {

    private Toolbar   toolbar;
    private TextView  tvSubjectTitle, tvSubjectPercent, tvSubjectSessionCount, tvEmpty;
    private ProgressBar progressSubject;
    private RecyclerView rvSessions;
    private AttendanceRecordAdapter adapter;

    private AuthManager        authManager;
    private AttendanceRepository attendanceRepo;
    private String subjectName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_attendance);

        toolbar               = findViewById(R.id.toolbar);
        tvSubjectTitle        = findViewById(R.id.tvSubjectTitle);
        tvSubjectPercent      = findViewById(R.id.tvSubjectPercent);
        tvSubjectSessionCount = findViewById(R.id.tvSubjectSessionCount);
        progressSubject       = findViewById(R.id.progressSubject);
        tvEmpty               = findViewById(R.id.tvEmpty);
        rvSessions            = findViewById(R.id.rvSessions);

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        authManager    = new AuthManager();
        attendanceRepo = new AttendanceRepository();

        subjectName = getIntent().getStringExtra("subject_name");
        if (subjectName == null || subjectName.isEmpty()) subjectName = "Subject";

        toolbar.setTitle(subjectName);
        tvSubjectTitle.setText(subjectName);

        adapter = new AttendanceRecordAdapter(
                AttendanceRecordAdapter.DisplayMode.STUDENT_SUBJECT_SESSION);
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(adapter);

        loadData();
    }

    private void loadData() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        attendanceRepo.getStudentHistory(uid, allRecords -> {
            runOnUiThread(() -> {
                // Filter to only records for this subject
                List<AttendanceRecord> filtered = new ArrayList<>();
                for (AttendanceRecord r : allRecords) {
                    if (subjectName.equals(r.getSubject())) {
                        filtered.add(r);
                    }
                }

                // Sort oldest first → Session 1, Session 2, …
                filtered.sort((a, b) -> {
                    if (a.getTime() == null && b.getTime() == null) return 0;
                    if (a.getTime() == null) return -1;
                    if (b.getTime() == null) return 1;
                    return a.getTime().compareTo(b.getTime());
                });

                // Compute attendance %
                int total   = filtered.size();
                int present = 0;
                for (AttendanceRecord r : filtered) {
                    if (Constants.STATUS_PRESENT.equals(r.getStatus())
                            || "Present".equals(r.getStatus())) {
                        present++;
                    }
                }
                int pct = total > 0 ? (present * 100) / total : 0;

                tvSubjectPercent.setText(pct + "%");
                progressSubject.setProgress(pct);
                tvSubjectSessionCount.setText(present + " / " + total + " sessions present");

                // Colour by threshold
                int colorRes = pct >= 75 ? R.color.attendanceHigh
                        : pct >= 60 ? R.color.attendanceMid : R.color.attendanceLow;
                int color = ContextCompat.getColor(this, colorRes);
                tvSubjectPercent.setTextColor(color);
                progressSubject.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(color));

                if (filtered.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvSessions.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvSessions.setVisibility(View.VISIBLE);
                    adapter.updateList(filtered);
                }
            });
        });
    }
}
