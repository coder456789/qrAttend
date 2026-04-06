package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.AttendanceRecordAdapter;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentDashboardActivity extends AppCompatActivity {

    private TextView tvAttendancePercent, tvEmptySubjects;
    private RecyclerView rvSubjects;
    private FloatingActionButton fabScanQR;
    private AttendanceRecordAdapter adapter;

    private AuthManager authManager;
    private StudentRepository studentRepo;
    private AttendanceRepository attendanceRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvAttendancePercent = findViewById(R.id.tvAttendancePercent);
        tvEmptySubjects = findViewById(R.id.tvEmptySubjects);
        rvSubjects = findViewById(R.id.rvSubjects);
        fabScanQR = findViewById(R.id.fabScanQR);

        authManager = new AuthManager();
        studentRepo = new StudentRepository();
        attendanceRepo = new AttendanceRepository();

        adapter = new AttendanceRecordAdapter();
        rvSubjects.setLayoutManager(new LinearLayoutManager(this));
        rvSubjects.setAdapter(adapter);

        fabScanQR.setOnClickListener(v ->
                startActivity(new Intent(this, ScanQRActivity.class)));

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

        // Load student name for toolbar
        studentRepo.getStudent(uid, student -> {
            if (student != null && student.getName() != null) {
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(getString(R.string.welcome_student, student.getName()));
                }
            }
        });

        // Load attendance history
        attendanceRepo.getStudentHistory(uid, records -> {
            if (records == null || records.isEmpty()) {
                tvAttendancePercent.setText(getString(R.string.attendance_percentage, 0));
                tvAttendancePercent.setTextColor(ContextCompat.getColor(this, R.color.attendanceLow));
                tvEmptySubjects.setVisibility(View.VISIBLE);
                rvSubjects.setVisibility(View.GONE);
                return;
            }

            // Compute overall attendance
            int total = records.size();
            int present = 0;
            for (AttendanceRecord r : records) {
                if (Constants.STATUS_PRESENT.equals(r.getStatus())) {
                    present++;
                }
            }
            int percent = total > 0 ? (present * 100) / total : 0;
            tvAttendancePercent.setText(getString(R.string.attendance_percentage, percent));

            // Color code
            if (percent >= 75) {
                tvAttendancePercent.setTextColor(ContextCompat.getColor(this, R.color.attendanceHigh));
            } else if (percent >= 60) {
                tvAttendancePercent.setTextColor(ContextCompat.getColor(this, R.color.attendanceMid));
            } else {
                tvAttendancePercent.setTextColor(ContextCompat.getColor(this, R.color.attendanceLow));
            }

            // Show subject-wise breakdown (grouped by sessionId as proxy for class)
            tvEmptySubjects.setVisibility(View.GONE);
            rvSubjects.setVisibility(View.VISIBLE);
            adapter.updateList(records);
        });
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
