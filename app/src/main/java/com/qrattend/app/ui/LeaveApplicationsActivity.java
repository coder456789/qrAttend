package com.qrattend.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

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
 * Tapping an application shows details and opens the attachment if present.
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

    /** Shows full application text + open attachment button. */
    private void showDetailDialog(LeaveApplication app) {
        if (app == null) return;

        String when = app.getSubmittedAt() != null
                ? dateFormat.format(app.getSubmittedAt().toDate()) : "Unknown date";

        String msg = "From: " + app.getStudentName()
                + (app.getStudentRollNo() != null && !app.getStudentRollNo().isEmpty()
                        ? "  (PRN: " + app.getStudentRollNo() + ")" : "")
                + "\nSubmitted: " + when
                + "\nStatus: " + app.getStatus()
                + "\n\n" + (app.getReason() != null ? app.getReason() : "");

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Leave Application")
                .setMessage(msg)
                .setNegativeButton("Close", null);

        // If there's an attachment, offer to open it
        if (app.getAttachmentUrl() != null && !app.getAttachmentUrl().isEmpty()) {
            builder.setPositiveButton("📎 Open Attachment", (d, w) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse(app.getAttachmentUrl()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Let Android choose viewer (browser/PDF/image app)
                startActivity(Intent.createChooser(intent, "Open with..."));
            });
        }

        builder.show();
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
