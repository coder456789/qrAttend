package com.qrattend.app.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.qrattend.app.R;
import com.qrattend.app.data.model.LeaveApplication;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.repository.LeaveApplicationRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.firebase.AuthManager;

/**
 * Student submits a leave application with optional proof document.
 */
public class LeaveApplicationActivity extends AppCompatActivity {

    private TextInputEditText etReason;
    private MaterialButton    btnAttachFile, btnSubmitLeave;
    private LinearLayout      layoutAttachmentPreview;
    private TextView          tvAttachmentName;
    private View              btnRemoveAttachment;
    private ProgressBar       progressSubmit;

    private Uri    attachedFileUri      = null;
    private String attachedFileName     = null;
    private String attachedMimeType     = null;

    private AuthManager                authManager;
    private StudentRepository          studentRepo;
    private LeaveApplicationRepository leaveRepo;

    private Student  currentStudent;
    private String   currentUid;

    // ── File picker ──────────────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> filePicker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri == null) return;

                    // Persist read permission
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    attachedFileUri  = uri;
                    attachedMimeType = getContentResolver().getType(uri);
                    attachedFileName = resolveFileName(uri);

                    tvAttachmentName.setText(attachedFileName);
                    layoutAttachmentPreview.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_application);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etReason               = findViewById(R.id.etReason);
        btnAttachFile          = findViewById(R.id.btnAttachFile);
        btnSubmitLeave         = findViewById(R.id.btnSubmitLeave);
        layoutAttachmentPreview = findViewById(R.id.layoutAttachmentPreview);
        tvAttachmentName       = findViewById(R.id.tvAttachmentName);
        btnRemoveAttachment    = findViewById(R.id.btnRemoveAttachment);
        progressSubmit         = findViewById(R.id.progressSubmit);

        authManager = new AuthManager();
        studentRepo = new StudentRepository();
        leaveRepo   = new LeaveApplicationRepository();
        currentUid  = authManager.getCurrentUserId();

        // Load student profile for denormalized name/roll
        if (currentUid != null) {
            studentRepo.getStudent(currentUid, student -> {
                currentStudent = student;
            });
        }

        btnAttachFile.setOnClickListener(v -> openFilePicker());
        btnRemoveAttachment.setOnClickListener(v -> clearAttachment());
        btnSubmitLeave.setOnClickListener(v -> submitApplication());
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Accept PDF and images
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

    private void submitApplication() {
        String reason = etReason.getText() != null
                ? etReason.getText().toString().trim() : "";

        if (reason.isEmpty()) {
            Toast.makeText(this, "Please write a reason for your leave.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUid == null) {
            Toast.makeText(this, "Not logged in.", Toast.LENGTH_SHORT).show();
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
        if (attachedMimeType != null) {
            app.setAttachmentMimeType(attachedMimeType);
        }

        if (attachedFileUri != null && attachedFileName != null) {
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
            Toast.makeText(this, "Application submitted successfully.", Toast.LENGTH_LONG).show();
            finish();
        } else {
            Toast.makeText(this, "Failed to submit. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    /** Resolves a human-readable file name from a content URI. */
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
