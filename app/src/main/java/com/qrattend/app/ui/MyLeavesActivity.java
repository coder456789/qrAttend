package com.qrattend.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.qrattend.app.R;
import com.qrattend.app.data.model.LeaveApplication;
import com.qrattend.app.data.repository.LeaveApplicationRepository;
import com.qrattend.app.firebase.AuthManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows all leave applications submitted by the current student.
 * Each card shows status (Approved / Rejected / Pending) colour-coded,
 * the date, class/subject, and a tap to open any attached file.
 */
public class MyLeavesActivity extends AppCompatActivity {

    private RecyclerView rvLeaves;
    private TextView     tvEmpty;
    private LeaveApplicationRepository leaveRepo;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_leaves);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvLeaves   = findViewById(R.id.rvLeaves);
        tvEmpty    = findViewById(R.id.tvEmpty);
        leaveRepo  = new LeaveApplicationRepository();
        authManager = new AuthManager();

        rvLeaves.setLayoutManager(new LinearLayoutManager(this));
        loadLeaves();
    }

    private void loadLeaves() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) { tvEmpty.setVisibility(View.VISIBLE); return; }

        leaveRepo.getApplicationsByStudent(uid, list -> runOnUiThread(() -> {
            if (list == null || list.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvLeaves.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                rvLeaves.setVisibility(View.VISIBLE);
                rvLeaves.setAdapter(new MyLeavesAdapter(list));
            }
        }));
    }

    // ── Inner Adapter ─────────────────────────────────────────────────────

    private class MyLeavesAdapter
            extends RecyclerView.Adapter<MyLeavesAdapter.VH> {

        private final List<LeaveApplication> items;
        private final SimpleDateFormat fmt =
                new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        MyLeavesAdapter(List<LeaveApplication> items) {
            this.items = items != null ? items : new ArrayList<>();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_my_leave, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            LeaveApplication app = items.get(pos);

            // Status chip colour
            String status = app.getStatus() != null ? app.getStatus() : "Pending";
            h.tvStatus.setText(status);
            int chipColor;
            switch (status) {
                case "Approved": chipColor = getResources().getColor(R.color.attendanceHigh, getTheme()); break;
                case "Rejected": chipColor = getResources().getColor(R.color.attendanceLow, getTheme()); break;
                default:         chipColor = getResources().getColor(R.color.attendanceMid, getTheme()); break;
            }
            h.tvStatus.setTextColor(chipColor);

            // Date submitted
            String when = app.getSubmittedAt() != null
                    ? fmt.format(app.getSubmittedAt().toDate()) : "Unknown";
            h.tvDate.setText(when);

            // Leave date
            h.tvLeaveDate.setText(app.getLeaveDate() != null
                    ? "Leave: " + app.getLeaveDate() : "");

            // Class / subject
            String classInfo = "";
            if (app.getSubject() != null) classInfo = app.getSubject();
            if (app.getClassName() != null && !app.getClassName().isEmpty())
                classInfo += (classInfo.isEmpty() ? "" : " — ") + app.getClassName();
            h.tvClassInfo.setText(classInfo);

            // Teacher
            h.tvTeacher.setText(app.getTeacherName() != null
                    ? "Teacher: " + app.getTeacherName() : "");

            // Reason (truncated)
            h.tvReason.setText(app.getReason() != null ? app.getReason() : "");

            // Attachment — show if URL or Base64 is present
            boolean hasAttachment = app.hasAttachment();
            h.cardAttachment.setVisibility(hasAttachment ? View.VISIBLE : View.GONE);
            if (hasAttachment) {
                h.tvAttachmentName.setText(app.getAttachmentFileName() != null
                        ? app.getAttachmentFileName() : "View attachment");
                h.cardAttachment.setOnClickListener(v -> openAttachment(app));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView         tvStatus, tvDate, tvLeaveDate,
                                   tvClassInfo, tvTeacher, tvReason, tvAttachmentName;
            final MaterialCardView cardAttachment;

            VH(@NonNull View itemView) {
                super(itemView);
                tvStatus        = itemView.findViewById(R.id.tvLeaveStatus);
                tvDate          = itemView.findViewById(R.id.tvDate);
                tvLeaveDate     = itemView.findViewById(R.id.tvLeaveDate);
                tvClassInfo     = itemView.findViewById(R.id.tvClassInfo);
                tvTeacher       = itemView.findViewById(R.id.tvTeacher);
                tvReason        = itemView.findViewById(R.id.tvReason);
                tvAttachmentName = itemView.findViewById(R.id.tvAttachmentName);
                cardAttachment  = itemView.findViewById(R.id.cardAttachment);
            }
        }
    }

    private void openAttachment(LeaveApplication app) {
        String url  = app.getAttachmentUrl();
        String b64  = app.getAttachmentBase64();
        String mime = app.getAttachmentBase64MimeType() != null
                ? app.getAttachmentBase64MimeType() : app.getAttachmentMimeType();

        // Strategy 1: URL
        if (url != null && !url.isEmpty()) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(intent, "Open with…"));
                return;
            } catch (Exception ignored) {}
        }

        // Strategy 2: Base64
        if (b64 != null && !b64.isEmpty()) {
            new Thread(() -> {
                try {
                    byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP);
                    String ext = "";
                    if (mime != null) {
                        if (mime.contains("pdf")) ext = ".pdf";
                        else if (mime.contains("png")) ext = ".png";
                        else if (mime.contains("jpeg") || mime.contains("jpg")) ext = ".jpg";
                    }
                    String name = (app.getAttachmentFileName() != null
                            && !app.getAttachmentFileName().isEmpty())
                            ? app.getAttachmentFileName() : "attachment" + ext;
                    java.io.File tmp = new java.io.File(getCacheDir(), name);
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                        fos.write(bytes);
                    }
                    Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                            this, "com.qrattend.app.fileprovider", tmp);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(fileUri,
                            mime != null ? mime : "application/octet-stream");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_ACTIVITY_NEW_TASK);
                    runOnUiThread(() -> {
                        try {
                            startActivity(Intent.createChooser(intent, "Open with…"));
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
            return;
        }

        com.qrattend.app.utils.SnackbarHelper.error(this, "Cannot open attachment.");
    }
}
