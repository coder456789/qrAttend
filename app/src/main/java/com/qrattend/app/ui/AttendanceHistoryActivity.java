package com.qrattend.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.AttendanceRecordAdapter;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class AttendanceHistoryActivity extends AppCompatActivity {

    private RecyclerView rvHistory;
    private TextView tvEmpty;
    private ChipGroup chipGroup;
    private Chip chipAll, chipPresent, chipRejected;
    private AttendanceRecordAdapter adapter;
    private List<AttendanceRecord> allRecords = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvHistory = findViewById(R.id.rvHistory);
        tvEmpty = findViewById(R.id.tvEmpty);
        chipGroup = findViewById(R.id.chipGroup);
        chipAll = findViewById(R.id.chipAll);
        chipPresent = findViewById(R.id.chipPresent);
        chipRejected = findViewById(R.id.chipRejected);

        adapter = new AttendanceRecordAdapter();
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        rvHistory.setAdapter(adapter);

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

        loadHistory();
    }

    private void loadHistory() {
        String uid = new AuthManager().getCurrentUserId();
        if (uid == null) return;

        new AttendanceRepository().getStudentHistory(uid, records -> {
            allRecords = records != null ? records : new ArrayList<>();
            applyFilter();
        });
    }

    private void applyFilter() {
        List<AttendanceRecord> filtered;

        if (chipPresent.isChecked()) {
            filtered = new ArrayList<>();
            for (AttendanceRecord r : allRecords) {
                if (Constants.STATUS_PRESENT.equals(r.getStatus())) filtered.add(r);
            }
        } else if (chipRejected.isChecked()) {
            filtered = new ArrayList<>();
            for (AttendanceRecord r : allRecords) {
                if (Constants.STATUS_REJECTED.equals(r.getStatus())) filtered.add(r);
            }
        } else {
            filtered = allRecords;
        }

        adapter.updateList(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        rvHistory.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
