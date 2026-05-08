package com.qrattend.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;
import com.qrattend.app.R;
import com.qrattend.app.data.model.LeaveApplication;
import com.qrattend.app.data.repository.LeaveApplicationRepository;
import com.qrattend.app.ui.adapters.LeaveApplicationAdapter;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Teacher view — lists all student leave applications in real time.
 * Tap  → full detail + open attachment.
 * Long-press → Approve / Reject action sheet.
 */
public class LeaveApplicationsActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView    tvEmpty;
    private RecyclerView rvApplications;
    private LeaveApplicationAdapter adapter;

    private LeaveApplicationRepository leaveRepo;
    private ListenerRegistration       listener;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leave_applications);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        progressBar     = findViewById(R.id.progressBar);
        tvEmpty         = findViewById(R.id.tvEmpty);
        rvApplications  = findViewById(R.id.rvApplications);

        leaveRepo = new LeaveApplicationRepository();

        adapter = new LeaveApplicationAdapter(this::showDetailDialog);
        adapter.setOnItemLongClickListener(this::showApproveRejectDialog);
        rvApplications.setLayoutManager(new LinearLayoutManager(this));
        rvApplications.setAdapter(adapter);

        loadApplications();
    }

    private void loadApplications() {
        progressBar.setVisibility(View.VISIBLE);

        listener = leaveRepo.listenAllApplications(apps -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (apps == null || apps.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rvApplications.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rvApplications.setVisibility(View.VISIBLE);
                    adapter.updateList(apps);
                }
            });
        });
    }

    /** Tap → custom leave detail dialog with attachment card + approve/reject buttons. */
    private void showDetailDialog(LeaveApplication app) {
        if (app == null) return;

        android.view.View dialogView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_leave_detail, null);

        // Header
        android.widget.TextView tvName   = dialogView.findViewById(R.id.tvLeaveStudentName);
        android.widget.TextView tvDate   = dialogView.findViewById(R.id.tvLeaveSubmittedAt);
        android.widget.TextView tvStatus = dialogView.findViewById(R.id.tvLeaveStatus);
        android.widget.TextView tvReason = dialogView.findViewById(R.id.tvLeaveReason);

        // Build header subtitle: class / teacher / leave date
        StringBuilder subtitle = new StringBuilder();
        if (app.getStudentRollNo() != null && !app.getStudentRollNo().isEmpty())
            subtitle.append("PRN: ").append(app.getStudentRollNo());
        if (app.getSubject() != null)
            subtitle.append(subtitle.length() > 0 ? "  |  " : "").append(app.getSubject());
        if (app.getClassName() != null)
            subtitle.append(" — ").append(app.getClassName());
        if (app.getLeaveDate() != null)
            subtitle.append("\nLeave date: ").append(app.getLeaveDate());
        if (app.getTeacherName() != null)
            subtitle.append("\nTeacher: ").append(app.getTeacherName());

        tvName.setText(app.getStudentName() != null ? app.getStudentName() : "Student");
        tvDate.setText(subtitle.length() > 0
                ? subtitle.toString()
                : (app.getSubmittedAt() != null
                        ? dateFormat.format(app.getSubmittedAt().toDate()) : "Unknown date"));
        tvStatus.setText(app.getStatus() != null ? app.getStatus() : "Pending");
        tvReason.setText(app.getReason() != null ? app.getReason() : "(No reason provided)");

        // Colour the status chip
        int chipColor;
        switch (app.getStatus() != null ? app.getStatus() : "Pending") {
            case "Approved": chipColor = getResources().getColor(R.color.attendanceHigh, getTheme()); break;
            case "Rejected": chipColor = getResources().getColor(R.color.attendanceLow, getTheme()); break;
            default:         chipColor = getResources().getColor(R.color.attendanceMid, getTheme()); break;
        }
        tvStatus.setTextColor(chipColor);

        // Attachment card — always show when URL is present
        com.google.android.material.card.MaterialCardView cardAttachment =
                dialogView.findViewById(R.id.cardAttachment);
        android.widget.TextView tvAttachName = dialogView.findViewById(R.id.tvAttachmentName);

        String attachUrl = app.getAttachmentUrl();
        boolean hasAttachment = app.hasAttachment();
        if (hasAttachment) {
            cardAttachment.setVisibility(android.view.View.VISIBLE);
            String displayName = (app.getAttachmentFileName() != null
                    && !app.getAttachmentFileName().isEmpty())
                    ? app.getAttachmentFileName() : "View attachment";
            tvAttachName.setText(displayName);
            cardAttachment.setOnClickListener(v -> openAttachment(app));
            // Long-press on attachment card → copy URL to clipboard
            cardAttachment.setOnLongClickListener(v -> {
                if (attachUrl != null && !attachUrl.isEmpty()) {
                    copyToClipboard("Attachment URL", attachUrl);
                    com.qrattend.app.utils.SnackbarHelper.info(this,
                            "URL copied — paste in browser to open.");
                }
                return true;
            });
        }

        // Approve / Reject buttons — show for both Pending and already-decided
        com.google.android.material.button.MaterialButton btnApprove =
                dialogView.findViewById(R.id.btnLeaveApprove);
        com.google.android.material.button.MaterialButton btnReject =
                dialogView.findViewById(R.id.btnLeaveReject);
        com.google.android.material.button.MaterialButton btnClose =
                dialogView.findViewById(R.id.btnLeaveClose);

        androidx.appcompat.app.AlertDialog dialog =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(dialogView)
                        .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // Always show approve/reject — teacher may override
        btnApprove.setVisibility(android.view.View.VISIBLE);
        btnReject.setVisibility(android.view.View.VISIBLE);
        if ("Approved".equals(app.getStatus())) {
            btnApprove.setText("✅ Approved");
        } else if ("Rejected".equals(app.getStatus())) {
            btnReject.setText("❌ Rejected");
        }

        btnApprove.setOnClickListener(v -> { applyStatus(app, "Approved"); dialog.dismiss(); });
        btnReject.setOnClickListener(v  -> { applyStatus(app, "Rejected"); dialog.dismiss(); });
        btnClose.setOnClickListener(v   -> dialog.dismiss());

        dialog.show();
    }

    /**
     * Long-press → same detail dialog (reuse showDetailDialog which already has Approve/Reject).
     */
    private void showApproveRejectDialog(LeaveApplication app) {
        showDetailDialog(app);
    }

    private void applyStatus(LeaveApplication app, String newStatus) {
        leaveRepo.updateStatus(app.getApplicationId(), newStatus, task -> {
            runOnUiThread(() -> {
                if (task.isSuccessful()) {
                    if ("Approved".equals(newStatus)) {
                        com.qrattend.app.utils.SnackbarHelper.success(this,
                                app.getStudentName() + ": Approved");
                    } else if ("Rejected".equals(newStatus)) {
                        com.qrattend.app.utils.SnackbarHelper.error(this,
                                app.getStudentName() + ": Rejected");
                    } else {
                        com.qrattend.app.utils.SnackbarHelper.info(this,
                                app.getStudentName() + ": " + newStatus);
                    }
                } else {
                    com.qrattend.app.utils.SnackbarHelper.error(this,
                            "Failed to update status.");
                }
            });
        });
    }

    /** Opens an attachment — handles both Firebase Storage URL and Base64 encoded content. */
    private void openAttachment(LeaveApplication app) {
        String url = app.getAttachmentUrl();
        String b64 = app.getAttachmentBase64();
        String mime = app.getAttachmentBase64MimeType() != null
                ? app.getAttachmentBase64MimeType()
                : app.getAttachmentMimeType();

        // ── Strategy 1: Firebase Storage URL ──────────────────────────────
        if (url != null && !url.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "Open attachment with…"));
                return;
            } catch (Exception e) {
                // Fall through to Base64 or clipboard
            }
        }

        // ── Strategy 2: Base64 in Firestore ───────────────────────────────
        if (b64 != null && !b64.isEmpty()) {
            openBase64Attachment(b64, mime, app.getAttachmentFileName());
            return;
        }

        // ── Strategy 3: Copy URL to clipboard ────────────────────────────
        if (url != null && !url.isEmpty()) {
            copyToClipboard("Attachment URL", url);
            com.qrattend.app.utils.SnackbarHelper.info(this,
                    "Could not open. URL copied — paste it in a browser.");
        } else {
            com.qrattend.app.utils.SnackbarHelper.error(this,
                    "No attachment available.");
        }
    }

    /** Decodes Base64 bytes, writes to a temp file in the cache, and opens it. */
    private void openBase64Attachment(String b64, String mime, String fileName) {
        new Thread(() -> {
            try {
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
                String ext = "";
                if (mime != null) {
                    if (mime.contains("pdf"))  ext = ".pdf";
                    else if (mime.contains("png")) ext = ".png";
                    else if (mime.contains("jpeg") || mime.contains("jpg")) ext = ".jpg";
                }
                String name = (fileName != null && !fileName.isEmpty()) ? fileName : "attachment" + ext;
                java.io.File tmp = new java.io.File(getCacheDir(), name);
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                    fos.write(bytes);
                }

                Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "com.qrattend.app.fileprovider",
                        tmp);

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri,
                        mime != null ? mime : "application/octet-stream");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_ACTIVITY_NEW_TASK);

                runOnUiThread(() -> {
                    try {
                        startActivity(Intent.createChooser(intent, "Open attachment with…"));
                    } catch (Exception e) {
                        com.qrattend.app.utils.SnackbarHelper.error(this,
                                "No app can open this file type.");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> com.qrattend.app.utils.SnackbarHelper.error(this,
                        "Failed to decode attachment."));
            }
        }).start();
    }

    private void copyToClipboard(String label, String text) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }
}
