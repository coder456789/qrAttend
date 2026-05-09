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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    // All enrolled students (as placeholder Absent records for display)
    private final Map<String, AttendanceRecord> enrolledPlaceholders = new HashMap<>();

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

        // Step 1: Load session info and enrolled students
        sessionRepo.getSession(sid, session -> {
            if (session == null) return;

            // Update header
            String subject   = session.getSubject()   != null ? session.getSubject()   : "";
            String className = session.getClassName() != null ? session.getClassName() : "";
            String startStr  = session.getStartTime() != null
                    ? new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                            .format(session.getStartTime().toDate())
                    : "";
            String statusTag = session.isActive() ? " [Active]" : " [Ended]";
            runOnUiThread(() ->
                    tvSessionInfo.setText(subject + " — " + className + "\n" + startStr + statusTag));

            // Step 2: Resolve classDocId from classId (which equals className) + teacherId
            String classId  = session.getClassId();
            String teacherId = session.getTeacherId();
            if (classId != null && teacherId != null) {
                FirebaseFirestore.getInstance()
                        .collection(Constants.CLASSES)
                        .whereEqualTo("className", classId)
                        .whereEqualTo("teacherId",  teacherId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(qs -> {
                            if (qs != null && !qs.isEmpty()) {
                                DocumentSnapshot classDoc = qs.getDocuments().get(0);
                                @SuppressWarnings("unchecked")
                                List<String> enrolled = (List<String>) classDoc.get("enrolledStudents");
                                if (enrolled != null) {
                                    buildEnrolledPlaceholders(enrolled, subject, sid);
                                }
                            }
                        });
            }
        });

        // Step 3: Real-time listener for actual scan records
        if (recordsListener != null) recordsListener.remove();
        recordsListener = attendanceRepo.listenToSessionRecords(sid, records -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                allRecords = records != null ? records : new ArrayList<>();
                applyFilter();
            });
        });
    }

    /**
     * For each enrolled student ID, fetch their name/roll and create a placeholder
     * AttendanceRecord with status "Absent" (will be overridden by actual records in applyFilter).
     */
    private void buildEnrolledPlaceholders(List<String> enrolledIds, String subject, String sid) {
        for (String studentId : enrolledIds) {
            FirebaseFirestore.getInstance()
                    .collection(Constants.STUDENTS)
                    .document(studentId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        AttendanceRecord placeholder = new AttendanceRecord();
                        placeholder.setStudentId(studentId);
                        placeholder.setSessionId(sid);
                        placeholder.setStatus(Constants.STATUS_ABSENT);
                        placeholder.setSubject(subject);
                        if (doc != null && doc.exists()) {
                            placeholder.setStudentName(doc.getString("name"));
                            placeholder.setStudentRollNo(doc.getString("rollNo"));
                        }
                        synchronized (enrolledPlaceholders) {
                            enrolledPlaceholders.put(studentId, placeholder);
                        }
                        runOnUiThread(this::applyFilter);
                    });
        }
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
        // Merge enrolled placeholders with actual scanned records.
        // Actual records always win (student scanned → override the "Absent" placeholder).
        Map<String, AttendanceRecord> merged = new HashMap<>();

        // 1. Put all enrolled placeholders first (status = Absent)
        synchronized (enrolledPlaceholders) {
            merged.putAll(enrolledPlaceholders);
        }

        // 2. Override with real scanned/manual records
        for (AttendanceRecord r : allRecords) {
            if (r.getStudentId() != null) {
                merged.put(r.getStudentId(), r);
            }
        }

        // 3. Apply date filter
        List<AttendanceRecord> filtered = new ArrayList<>();
        for (AttendanceRecord r : merged.values()) {
            if (filterCalendar == null) {
                filtered.add(r);
            } else {
                if (r.getTime() != null) {
                    Calendar recCal = Calendar.getInstance();
                    recCal.setTime(r.getTime().toDate());
                    if (recCal.get(Calendar.YEAR)          == filterCalendar.get(Calendar.YEAR)
                            && recCal.get(Calendar.MONTH)  == filterCalendar.get(Calendar.MONTH)
                            && recCal.get(Calendar.DAY_OF_MONTH) == filterCalendar.get(Calendar.DAY_OF_MONTH)) {
                        filtered.add(r);
                    }
                } else if (filterCalendar == null) {
                    filtered.add(r); // absent placeholders have no time — show always when no filter
                }
            }
        }

        // Sort: Present first, then Leave, then Absent at the bottom
        filtered.sort((a, b) -> {
            int rankA = statusRank(a.getStatus());
            int rankB = statusRank(b.getStatus());
            return Integer.compare(rankA, rankB);
        });

        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(filterCalendar != null
                    ? "No records on " + dateFormat.format(filterCalendar.getTime())
                    : "No enrolled students found.");
            rvRecords.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvRecords.setVisibility(View.VISIBLE);
            adapter.updateList(filtered);
        }

        currentFilteredRecords = filtered;

        // Stats: total = all enrolled, present = those who actually scanned
        int total   = filtered.size();
        int present = 0;
        int absent  = 0;
        for (AttendanceRecord r : filtered) {
            if (Constants.STATUS_PRESENT.equals(r.getStatus()) || "Present".equals(r.getStatus()))
                present++;
            else if (Constants.STATUS_ABSENT.equals(r.getStatus()) || "Absent".equals(r.getStatus()))
                absent++;
        }
        tvTotalRecords.setText(getString(R.string.total_records, total));
        tvPresentRecords.setText(getString(R.string.present_records, present)
                + "  |  Absent: " + absent);
    }

    /** Present=0, Leave=1, Rejected=2, Absent=3 — so absent students sink to bottom */
    private int statusRank(String status) {
        if (status == null) return 3;
        switch (status) {
            case "Present":  return 0;
            case "Leave":    return 1;
            case "Rejected": return 2;
            default:         return 3; // Absent
        }
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

        String name = (record.getStudentName() != null && !record.getStudentName().isEmpty())
                ? record.getStudentName() : "Student";
        String roll = record.getStudentRollNo() != null
                ? "PRN: " + record.getStudentRollNo() : "";

        // Inflate custom layout
        android.view.View dialogView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_student_action, null);

        android.widget.TextView tvName = dialogView.findViewById(R.id.tvDialogStudentName);
        android.widget.TextView tvRoll = dialogView.findViewById(R.id.tvDialogStudentRoll);
        tvName.setText(name);
        tvRoll.setText(roll);

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(dialogView)
                        .create();

        // Transparent window background so the rounded card shape shows
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        String subject = null; // subject resolved per-action if needed

        dialogView.findViewById(R.id.btnMarkPresent).setOnClickListener(v -> {
            markWithStatus(sessionId, studentId, "Present",
                    record.getStudentName(), record.getStudentRollNo(), subject);
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btnMarkAbsent).setOnClickListener(v -> {
            markWithStatus(sessionId, studentId,
                    com.qrattend.app.utils.Constants.STATUS_ABSENT,
                    record.getStudentName(), record.getStudentRollNo(), subject);
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btnMarkLeave).setOnClickListener(v -> {
            markWithStatus(sessionId, studentId,
                    com.qrattend.app.utils.Constants.STATUS_LEAVE,
                    record.getStudentName(), record.getStudentRollNo(), subject);
            dialog.dismiss();
        });
        dialogView.findViewById(R.id.btnUnbindDevice).setOnClickListener(v -> {
            dialog.dismiss();
            confirmAndUnbindDevice(studentId, name);
        });
        dialogView.findViewById(R.id.btnDialogCancel).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void markWithStatus(String sid, String studentId, String status,
                                String name, String rollNo, String subject) {
        attendanceRepo.manuallyMarkWithStatus(sid, studentId, status, name, rollNo, subject,
                task -> runOnUiThread(() -> {
                    if (task.isSuccessful()) {
                        com.qrattend.app.utils.SnackbarHelper.success(this,
                                name + " marked " + status + ".");
                    } else {
                        com.qrattend.app.utils.SnackbarHelper.error(this,
                                "Failed to update status.");
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
                                        com.qrattend.app.utils.SnackbarHelper.success(this,
                                                "Device unbound for " + studentName + ".");
                                    } else {
                                        com.qrattend.app.utils.SnackbarHelper.error(this,
                                                "Failed to unbind device.");
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
                        com.qrattend.app.utils.SnackbarHelper.warning(this, "PRN cannot be empty.");
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
                    com.qrattend.app.utils.SnackbarHelper.error(this,
                            "No student found with PRN: " + prn);
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
                                    com.qrattend.app.utils.SnackbarHelper.success(this,
                                            name + " (" + rollNo + ") marked " + status + ".");
                                } else {
                                    com.qrattend.app.utils.SnackbarHelper.error(this,
                                            "Failed to mark attendance.");
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
