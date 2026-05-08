package com.qrattend.app.ui;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.qrattend.app.R;
import com.qrattend.app.data.model.ClassInfo;
import com.qrattend.app.data.model.LeaveApplication;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.repository.ClassRepository;
import com.qrattend.app.data.repository.LeaveApplicationRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.data.repository.TeacherRepository;
import com.qrattend.app.firebase.AuthManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Student submits a leave application with:
 * – Class / subject selector (loaded from enrolled classes in Firestore)
 * – Auto-filled teacher name from the selected class
 * – Date picker for the leave date
 * – Optional file attachment (PDF / image)
 */
public class LeaveApplicationActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────
    private Spinner            spinnerClass;
    private TextView           tvTeacherName;
    private MaterialButton     btnPickDate;
    private TextInputEditText  etReason;
    private MaterialButton     btnAttachFile, btnSubmitLeave;
    private LinearLayout       layoutAttachmentPreview;
    private TextView           tvAttachmentName;
    private View               btnRemoveAttachment;
    private ProgressBar        progressSubmit;

    // ── Data ──────────────────────────────────────────────────────────────
    private final List<ClassInfo> classList    = new ArrayList<>();
    private final List<String>    classDocIds  = new ArrayList<>();
    private String                selectedDate = null;   // ISO format yyyy-MM-dd
    private String                selectedTeacherId   = null;
    private String                selectedTeacherName = null;
    private String                selectedClassId     = null;
    private String                selectedClassName   = null;
    private String                selectedSubject     = null;

    private Uri    attachedFileUri  = null;
    private String attachedFileName = null;
    private String attachedMimeType = null;

    private AuthManager                authManager;
    private StudentRepository          studentRepo;
    private ClassRepository            classRepo;
    private TeacherRepository          teacherRepo;
    private LeaveApplicationRepository leaveRepo;

    private Student currentStudent;
    private String  currentUid;

    private final SimpleDateFormat isoFormat    = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private final SimpleDateFormat displayFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    // ── File picker ───────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri == null) return;
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    attachedFileUri  = uri;
                    attachedMimeType = getContentResolver().getType(uri);
                    attachedFileName = resolveFileName(uri);
                    tvAttachmentName.setText(attachedFileName);
                    layoutAttachmentPreview.setVisibility(View.VISIBLE);
                }
            });

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_application);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerClass            = findViewById(R.id.spinnerClass);
        tvTeacherName           = findViewById(R.id.tvTeacherName);
        btnPickDate             = findViewById(R.id.btnPickDate);
        etReason                = findViewById(R.id.etReason);
        btnAttachFile           = findViewById(R.id.btnAttachFile);
        btnSubmitLeave          = findViewById(R.id.btnSubmitLeave);
        layoutAttachmentPreview = findViewById(R.id.layoutAttachmentPreview);
        tvAttachmentName        = findViewById(R.id.tvAttachmentName);
        btnRemoveAttachment     = findViewById(R.id.btnRemoveAttachment);
        progressSubmit          = findViewById(R.id.progressSubmit);

        authManager  = new AuthManager();
        studentRepo  = new StudentRepository();
        classRepo    = new ClassRepository();
        teacherRepo  = new TeacherRepository();
        leaveRepo    = new LeaveApplicationRepository();
        currentUid   = authManager.getCurrentUserId();

        // Load student profile
        if (currentUid != null) {
            studentRepo.getStudent(currentUid, s -> currentStudent = s);
        }

        // My Leaves button → history screen
        MaterialButton btnMyLeaves = toolbar.findViewById(R.id.btnMyLeaves);
        if (btnMyLeaves != null) {
            btnMyLeaves.setOnClickListener(v ->
                    startActivity(new Intent(this, MyLeavesActivity.class)));
        }

        btnPickDate.setOnClickListener(v -> openDatePicker());
        btnAttachFile.setOnClickListener(v -> openFilePicker());
        btnRemoveAttachment.setOnClickListener(v -> clearAttachment());
        btnSubmitLeave.setOnClickListener(v -> submitApplication());

        loadEnrolledClasses();
    }

    // ── Load enrolled classes ─────────────────────────────────────────────

    /**
     * Fetches all Firestore class documents where the current student's UID
     * appears in enrolledStudents[]. Populates the spinner.
     */
    private void loadEnrolledClasses() {
        if (currentUid == null) return;
        classRepo.getAllClasses(allClasses -> {
            classList.clear();
            classDocIds.clear();
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < allClasses.size(); i++) {
                ClassInfo ci = allClasses.get(i);
                if (ci.getEnrolledStudents() != null
                        && ci.getEnrolledStudents().contains(currentUid)) {
                    classList.add(ci);
                    classDocIds.add(""); // doc ID resolved below
                    labels.add(ci.getSubject() + " — " + ci.getClassName());
                }
            }

            // We need doc IDs too — fetch all classes with doc IDs
            // Use a secondary fetch to get proper doc IDs via snapshot
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection(com.qrattend.app.utils.Constants.CLASSES)
                    .get()
                    .addOnSuccessListener(qs -> runOnUiThread(() -> {
                        classList.clear();
                        classDocIds.clear();
                        List<String> finalLabels = new ArrayList<>();

                        for (com.google.firebase.firestore.DocumentSnapshot doc : qs.getDocuments()) {
                            ClassInfo ci = doc.toObject(ClassInfo.class);
                            if (ci == null) continue;
                            if (ci.getEnrolledStudents() != null
                                    && ci.getEnrolledStudents().contains(currentUid)) {
                                classList.add(ci);
                                classDocIds.add(doc.getId());
                                finalLabels.add(ci.getSubject() + " — " + ci.getClassName());
                            }
                        }

                        if (finalLabels.isEmpty()) {
                            finalLabels.add("No enrolled classes found");
                        }

                        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                                this, android.R.layout.simple_spinner_item, finalLabels);
                        adapter.setDropDownViewResource(
                                android.R.layout.simple_spinner_dropdown_item);
                        spinnerClass.setAdapter(adapter);

                        // Spinner listener
                        spinnerClass.setOnItemSelectedListener(
                                new android.widget.AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(android.widget.AdapterView<?> p,
                                                               View v, int pos, long id) {
                                        if (pos >= classList.size()) return;
                                        onClassSelected(pos);
                                    }
                                    @Override
                                    public void onNothingSelected(android.widget.AdapterView<?> p) {}
                                });

                        // Auto-select first class
                        if (!classList.isEmpty()) onClassSelected(0);
                    }));
        });
    }

    private void onClassSelected(int pos) {
        ClassInfo ci = classList.get(pos);
        selectedClassId    = classDocIds.get(pos);
        selectedClassName  = ci.getClassName();
        selectedSubject    = ci.getSubject();
        selectedTeacherId  = ci.getTeacherId();
        selectedTeacherName = null;

        tvTeacherName.setText("Loading…");
        tvTeacherName.setTextColor(getResources().getColor(R.color.textHint, getTheme()));

        if (ci.getTeacherId() != null) {
            teacherRepo.getTeacher(ci.getTeacherId(), teacher -> runOnUiThread(() -> {
                if (teacher != null && teacher.getName() != null) {
                    selectedTeacherName = teacher.getName();
                    tvTeacherName.setText(teacher.getName());
                    tvTeacherName.setTextColor(
                            getResources().getColor(R.color.textPrimary, getTheme()));
                } else {
                    tvTeacherName.setText("Unknown teacher");
                }
            }));
        } else {
            tvTeacherName.setText("No teacher assigned");
        }
    }

    // ── Date picker ───────────────────────────────────────────────────────

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, day) -> {
            cal.set(year, month, day);
            selectedDate = isoFormat.format(cal.getTime());
            btnPickDate.setText("📅  " + displayFormat.format(cal.getTime()));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    // ── File picker ───────────────────────────────────────────────────────

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf", "image/jpeg", "image/png", "image/jpg"
        });
        filePicker.launch(intent);
    }

    private void clearAttachment() {
        attachedFileUri  = null;
        attachedFileName = null;
        attachedMimeType = null;
        layoutAttachmentPreview.setVisibility(View.GONE);
    }

    // ── Submit ────────────────────────────────────────────────────────────

    private void submitApplication() {
        String reason = etReason.getText() != null ? etReason.getText().toString().trim() : "";

        if (classList.isEmpty()) {
            com.qrattend.app.utils.SnackbarHelper.warning(this,
                    "You are not enrolled in any class yet.");
            return;
        }
        if (selectedDate == null || selectedDate.isEmpty()) {
            com.qrattend.app.utils.SnackbarHelper.warning(this,
                    "Please pick the date of leave.");
            return;
        }
        if (reason.isEmpty()) {
            com.qrattend.app.utils.SnackbarHelper.warning(this,
                    "Please write a reason for your leave.");
            return;
        }
        if (currentUid == null) {
            com.qrattend.app.utils.SnackbarHelper.error(this, "Not logged in.");
            return;
        }

        progressSubmit.setVisibility(View.VISIBLE);
        btnSubmitLeave.setEnabled(false);

        LeaveApplication app = new LeaveApplication();
        app.setStudentId(currentUid);
        app.setStudentName(currentStudent != null ? currentStudent.getName() : "");
        app.setStudentRollNo(currentStudent != null ? currentStudent.getRollNo() : "");
        app.setReason(reason);
        app.setSubmittedAt(Timestamp.now());
        app.setStatus("Pending");
        app.setLeaveDate(selectedDate);
        app.setClassId(selectedClassId);
        app.setClassName(selectedClassName);
        app.setSubject(selectedSubject);
        app.setTeacherId(selectedTeacherId);
        app.setTeacherName(selectedTeacherName);
        if (attachedMimeType != null) app.setAttachmentMimeType(attachedMimeType);

        if (attachedFileUri != null && attachedFileName != null) {
            leaveRepo.setContentResolver(getContentResolver());
            leaveRepo.submitWithAttachment(app, attachedFileUri, currentUid, attachedFileName,
                    task -> runOnUiThread(() -> onSubmitResult(task.isSuccessful())));
        } else {
            leaveRepo.submitApplication(app,
                    task -> runOnUiThread(() -> onSubmitResult(task.isSuccessful())));
        }
    }

    private void onSubmitResult(boolean success) {
        progressSubmit.setVisibility(View.GONE);
        btnSubmitLeave.setEnabled(true);
        if (success) {
            com.qrattend.app.utils.SnackbarHelper.success(this,
                    "Application submitted successfully.");
            finish();
        } else {
            com.qrattend.app.utils.SnackbarHelper.error(this,
                    "Failed to submit. Please try again.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String resolveFileName(Uri uri) {
        String name = null;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return name != null ? name : "attachment";
    }
}
