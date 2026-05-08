package com.qrattend.app.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.ui.adapters.AttendanceRecordAdapter;
import com.qrattend.app.utils.Constants;
import com.qrattend.app.utils.CsvExporter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class SessionAttendanceActivity extends AppCompatActivity {

    private TextView tvSessionInfo, tvTotalRecords, tvPresentRecords, tvEmpty, tvDateFilter;
    private RecyclerView rvRecords;
    private AttendanceRecordAdapter adapter;
    private MaterialButton btnFilterDate, btnClearFilter, btnExportCsv;
    private FloatingActionButton fabAddManual;
    private ProgressBar progressBar;

    private List<AttendanceRecord> allRecords = new ArrayList<>();
    private List<AttendanceRecord> currentFilteredRecords = new ArrayList<>();
    private String sessionId;
    private AttendanceRepository attendanceRepo;
    private SessionRepository sessionRepo;
    private StudentRepository studentRepo;
    private ListenerRegistration recordsListener;

    private Calendar filterCalendar = null;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session_attendance);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvSessionInfo    = findViewById(R.id.tvSessionInfo);
        tvTotalRecords   = findViewById(R.id.tvTotalRecords);
        tvPresentRecords = findViewById(R.id.tvPresentRecords);
        tvEmpty          = findViewById(R.id.tvEmpty);
        rvRecords        = findViewById(R.id.rvRecords);
        progressBar      = findViewById(R.id.progressBar);
        btnFilterDate    = findViewById(R.id.btnFilterDate);
        btnClearFilter   = findViewById(R.id.btnClearFilter);
        btnExportCsv     = findViewById(R.id.btnExportCsv);
        tvDateFilter     = findViewById(R.id.tvDateFilter);
        fabAddManual     = findViewById(R.id.fabAddManual);

        attendanceRepo = new AttendanceRepository();
        sessionRepo    = new SessionRepository();
        studentRepo    = new StudentRepository();

        adapter = new AttendanceRecordAdapter(AttendanceRecordAdapter.DisplayMode.TEACHER_SESSION);
        rvRecords.setLayoutManager(new LinearLayoutManager(this));
        rvRecords.setAdapter(adapter);

        adapter.setOnItemLongClickListener((record, position) -> showManualMarkDialog(record));

        btnFilterDate.setOnClickListener(v -> showDatePicker());
        btnClearFilter.setOnClickListener(v -> clearDateFilter());
        btnExportCsv.setOnClickListener(v -> exportCurrentRecords());
        fabAddManual.setOnClickListener(v -> showAddStudentDialog());

        sessionId = getIntent().getStringExtra("session_id");
        if (sessionId != null) {
            loadSessionData(sessionId);
        } else {
            tvEmpty.setText("No session selected.");
            tvEmpty.setVisibility(View.VISIBLE);
        }
    }

    private void loadSessionData(String sid) {
        progressBar.setVisibility(View.VISIBLE);

        sessionRepo.getSession(sid, session -> {
            if (session != null) {
                String subject   = session.getSubject()   != null ? session.getSubject()   : "";
                String className = session.getClassName() != null ? session.getClassName() : "";
                String startStr  = session.getStartTime() != null
                        ? new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                                .format(session.getStartTime().toDate())
                        : "";
                String statusTag = session.isActive() ? " [Active]" : " [Ended]";
                tvSessionInfo.setText(subject + " — " + className + "\n" + startStr + statusTag);
            }
        });

        if (recordsListener != null) {
            recordsListener.remove();
        }
        recordsListener = attendanceRepo.listenToSessionRecords(sid, records -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                allRecords = records != null ? records : new ArrayList<>();
                applyFilter();
            });
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (recordsListener != null) {
            recordsListener.remove();
            recordsListener = null;
        }
    }

    private void applyFilter() {
        List<AttendanceRecord> filtered = new ArrayList<>();

        for (AttendanceRecord r : allRecords) {
            if (filterCalendar == null) {
                filtered.add(r);
            } else {
                if (r.getTime() != null) {
                    Calendar recCal = Calendar.getInstance();
                    recCal.setTime(r.getTime().toDate());
                    if (recCal.get(Calendar.YEAR)         == filterCalendar.get(Calendar.YEAR)
                            && recCal.get(Calendar.MONTH) == filterCalendar.get(Calendar.MONTH)
                            && recCal.get(Calendar.DAY_OF_MONTH) == filterCalendar.get(Calendar.DAY_OF_MONTH)) {
                        filtered.add(r);
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(filterCalendar != null
                    ? "No records on " + dateFormat.format(filterCalendar.getTime())
                    : "No records found for this session.");
            rvRecords.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvRecords.setVisibility(View.VISIBLE);
            adapter.updateList(filtered);
        }

        currentFilteredRecords = filtered;

        int total   = filtered.size();
        int present = 0;
        for (AttendanceRecord r : filtered) {
            if (Constants.STATUS_PRESENT.equals(r.getStatus()) || "Present".equals(r.getStatus()))
                present++;
        }
        tvTotalRecords.setText(getString(R.string.total_records, total));
        tvPresentRecords.setText(getString(R.string.present_records, present));
    }

    private void showDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, day) -> {
                    filterCalendar = Calendar.getInstance();
                    filterCalendar.set(year, month, day);
                    tvDateFilter.setText("Showing: " + dateFormat.format(filterCalendar.getTime()));
                    tvDateFilter.setVisibility(View.VISIBLE);
                    btnClearFilter.setVisibility(View.VISIBLE);
                    applyFilter();
                },
                now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void clearDateFilter() {
        filterCalendar = null;
        tvDateFilter.setVisibility(View.GONE);
        btnClearFilter.setVisibility(View.GONE);
        applyFilter();
    }

    // ── Manual Override (long-press existing record) ─────────────────────

    /**
     * Long-press context menu with 4 options:
     * Mark Present | Mark Absent | Mark Leave | Unbind Device
     */
    private void showManualMarkDialog(AttendanceRecord record) {
        if (record == null) return;
        String studentId = record.getStudentId();
        if (studentId == null || sessionId == null) return;

        String studentDisplay = (record.getStudentName() != null && !record.getStudentName().isEmpty())
                ? record.getStudentName() + " (" + record.getStudentRollNo() + ")"
                : studentId;

        String[] options = {"Mark Present", "Mark Absent", "Mark Leave", "Unbind Device"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(studentDisplay)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Mark Present
                            markWithStatus(sessionId, studentId,
                                    "Present",
                                    record.getStudentName(),
                                    record.getStudentRollNo(),
                                    null);
                            break;
                        case 1: // Mark Absent
                            markWithStatus(sessionId, studentId,
                                    com.qrattend.app.utils.Constants.STATUS_ABSENT,
                                    record.getStudentName(),
                                    record.getStudentRollNo(),
                                    null);
                            break;
                        case 2: // Mark Leave
                            markWithStatus(sessionId, studentId,
                                    com.qrattend.app.utils.Constants.STATUS_LEAVE,
                                    record.getStudentName(),
                                    record.getStudentRollNo(),
                                    null);
                            break;
                        case 3: // Unbind Device
                            confirmAndUnbindDevice(studentId,
                                    record.getStudentName() != null
                                            ? record.getStudentName() : "Student");
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markWithStatus(String sid, String studentId, String status,
                                String name, String rollNo, String subject) {
        attendanceRepo.manuallyMarkWithStatus(sid, studentId, status, name, rollNo, subject,
                task -> runOnUiThread(() -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                name + " marked " + status + ".",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to update status.", Toast.LENGTH_LONG).show();
                    }
                }));
    }

    private void confirmAndUnbindDevice(String studentId, String studentName) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Unbind Device")
                .setMessage("Remove the bound device for " + studentName
                        + "?\n\nTheir next QR scan will auto-bind the new device.")
                .setPositiveButton("Unbind", (d, w) ->
                        studentRepo.unbindDeviceByTeacher(studentId, task ->
                                runOnUiThread(() -> {
                                    if (task.isSuccessful()) {
                                        Toast.makeText(this,
                                                "Device unbound for " + studentName + ".",
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, "Failed to unbind device.",
                                                Toast.LENGTH_LONG).show();
                                    }
                                })))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Add Student by PRN (FAB +) ──────────────────────────────────────

    /**
     * Teacher taps "+" → enter PRN, look up student, choose status, submit.
     */
    private void showAddStudentDialog() {
        if (sessionId == null) return;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad / 2, pad, 0);

        final android.widget.EditText etPrn = new android.widget.EditText(this);
        etPrn.setHint("Enter PRN / Roll Number");
        layout.addView(etPrn);

        // Status selection (RadioGroup)
        android.widget.RadioGroup rgStatus = new android.widget.RadioGroup(this);
        rgStatus.setOrientation(android.widget.RadioGroup.HORIZONTAL);
        android.widget.RadioButton rbPresent = new android.widget.RadioButton(this);
        rbPresent.setText("Present"); rbPresent.setId(android.view.View.generateViewId());
        android.widget.RadioButton rbAbsent = new android.widget.RadioButton(this);
        rbAbsent.setText("Absent"); rbAbsent.setId(android.view.View.generateViewId());
        android.widget.RadioButton rbLeave = new android.widget.RadioButton(this);
        rbLeave.setText("Leave"); rbLeave.setId(android.view.View.generateViewId());
        rgStatus.addView(rbPresent);
        rgStatus.addView(rbAbsent);
        rgStatus.addView(rbLeave);
        rbPresent.setChecked(true);
        layout.addView(rgStatus);

        new AlertDialog.Builder(this)
                .setTitle("Add Student Attendance")
                .setMessage("Enter PRN / Roll Number and select status.")
                .setView(layout)
                .setPositiveButton("Submit", (dialog, which) -> {
                    String prn = etPrn.getText().toString().trim();
                    if (prn.isEmpty()) {
                        Toast.makeText(this, "PRN cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String status = rbAbsent.isChecked()
                            ? com.qrattend.app.utils.Constants.STATUS_ABSENT
                            : rbLeave.isChecked()
                                    ? com.qrattend.app.utils.Constants.STATUS_LEAVE
                                    : "Present";
                    lookupAndMarkByPrn(prn, status);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void lookupAndMarkByPrn(String prn, String status) {
        progressBar.setVisibility(View.VISIBLE);

        studentRepo.getStudentByRollNo(prn, result -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);

                if (result == null) {
                    Toast.makeText(this, "No student found with PRN: " + prn, Toast.LENGTH_LONG).show();
                    return;
                }

                String uid    = result.first;
                String name   = result.second != null && result.second.getName() != null
                        ? result.second.getName() : "Unknown";
                String rollNo = result.second != null && result.second.getRollNo() != null
                        ? result.second.getRollNo() : prn;

                sessionRepo.getSession(sessionId, session -> {
                    String subject = session != null ? session.getSubject() : null;
                    attendanceRepo.manuallyMarkWithStatus(
                            sessionId, uid, status, name, rollNo, subject,
                            task -> runOnUiThread(() -> {
                                if (task.isSuccessful()) {
                                    Toast.makeText(this,
                                            name + " (" + rollNo + ") marked " + status + ".",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Failed to mark attendance.",
                                            Toast.LENGTH_LONG).show();
                                }
                            }));
                });
            });
        });
    }

    // ── Export ───────────────────────────────────────────────────────────

    private void exportCurrentRecords() {
        if (currentFilteredRecords == null || currentFilteredRecords.isEmpty()) {
            Toast.makeText(this, "No records to export.", Toast.LENGTH_SHORT).show();
            return;
        }
        String label = (tvSessionInfo.getText() != null)
                ? tvSessionInfo.getText().toString().split("\n")[0].trim()
                : "session";
        CsvExporter.exportAndShare(this, currentFilteredRecords, label);
    }
}
