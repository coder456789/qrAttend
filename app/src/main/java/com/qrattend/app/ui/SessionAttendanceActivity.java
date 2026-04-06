package com.qrattend.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.ui.adapters.AttendanceRecordAdapter;
import com.qrattend.app.utils.Constants;

import java.util.List;

public class SessionAttendanceActivity extends AppCompatActivity {

    private TextView tvSessionInfo, tvTotalRecords, tvPresentRecords, tvEmpty;
    private RecyclerView rvRecords;
    private AttendanceRecordAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_attendance);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSessionInfo = findViewById(R.id.tvSessionInfo);
        tvTotalRecords = findViewById(R.id.tvTotalRecords);
        tvPresentRecords = findViewById(R.id.tvPresentRecords);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvRecords = findViewById(R.id.rvRecords);

        adapter = new AttendanceRecordAdapter();
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);

        String sessionId = getIntent().getStringExtra("session_id");
        if (sessionId != null) {
            loadSessionData(sessionId);
        }
    }

    private void loadSessionData(String sessionId) {
        SessionRepository sessionRepo = new SessionRepository();
        AttendanceRepository attendanceRepo = new AttendanceRepository();

        // Load session header
        sessionRepo.getSession(sessionId, session -> {
            if (session != null) {
                String subject = session.getSubject() != null ? session.getSubject() : "";
                String className = session.getClassName() != null ? session.getClassName() : "";
                tvSessionInfo.setText(getString(R.string.session_info, subject, className));
            }
        });

        // Load records
        attendanceRepo.getSessionRecords(sessionId, records -> {
            if (records == null || records.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvRecords.setVisibility(View.GONE);
                tvTotalRecords.setText(getString(R.string.total_records, 0));
                tvPresentRecords.setText(getString(R.string.present_records, 0));
            } else {
                tvEmpty.setVisibility(View.GONE);
                rvRecords.setVisibility(View.VISIBLE);
                adapter.updateList(records);

                int total = records.size();
                int present = 0;
                for (AttendanceRecord r : records) {
                    if (Constants.STATUS_PRESENT.equals(r.getStatus())) present++;
                }
                tvTotalRecords.setText(getString(R.string.total_records, total));
                tvPresentRecords.setText(getString(R.string.present_records, present));
            }
        });
    }
}
