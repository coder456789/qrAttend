package com.qrattend.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.qrattend.app.R;
import com.qrattend.app.data.model.ClassInfo;
import com.qrattend.app.data.repository.ClassRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.EnrolledClassAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Lets a student enter a 6-character class join code.
 * On success the student's UID is appended to {@code enrolledStudents[]} in that class doc.
 * Also shows the list of classes the student is already enrolled in.
 */
public class JoinClassActivity extends AppCompatActivity {

    private TextInputEditText etJoinCode;
    private TextView          tvJoinStatus;
    private ProgressBar       progressJoin;
    private MaterialButton    btnJoinClass;
    private RecyclerView      rvEnrolledClasses;
    private TextView          tvNoEnrolled;

    private AuthManager      authManager;
    private ClassRepository  classRepo;
    private EnrolledClassAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_class);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etJoinCode        = findViewById(R.id.etJoinCode);
        tvJoinStatus      = findViewById(R.id.tvJoinStatus);
        progressJoin      = findViewById(R.id.progressJoin);
        btnJoinClass      = findViewById(R.id.btnJoinClass);
        rvEnrolledClasses = findViewById(R.id.rvEnrolledClasses);
        tvNoEnrolled      = findViewById(R.id.tvNoEnrolled);

        authManager = new AuthManager();
        classRepo   = new ClassRepository();

        adapter = new EnrolledClassAdapter();
        rvEnrolledClasses.setLayoutManager(new LinearLayoutManager(this));
        rvEnrolledClasses.setAdapter(adapter);

        // Auto-uppercase code as user types
        etJoinCode.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                tvJoinStatus.setVisibility(View.GONE);
            }
        });

        btnJoinClass.setOnClickListener(v -> attemptJoin());

        loadEnrolledClasses();
    }

    private void attemptJoin() {
        String code = etJoinCode.getText() != null
                ? etJoinCode.getText().toString().trim().toUpperCase() : "";

        if (code.length() != 6) {
            showStatus("Enter a valid 6-character code.", false);
            return;
        }

        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        progressJoin.setVisibility(View.VISIBLE);
        btnJoinClass.setEnabled(false);
        tvJoinStatus.setVisibility(View.GONE);

        classRepo.getClassByJoinCode(code, result -> {
            runOnUiThread(() -> {
                progressJoin.setVisibility(View.GONE);
                btnJoinClass.setEnabled(true);

                if (result == null) {
                    showStatus("❌  No class found with that code. Check with your teacher.", false);
                    return;
                }

                String docId    = result.first;
                ClassInfo info  = result.second;

                // Already enrolled?
                List<String> enrolled = info != null && info.getEnrolledStudents() != null
                        ? info.getEnrolledStudents() : new ArrayList<>();
                if (enrolled.contains(uid)) {
                    showStatus("✅  You are already enrolled in \""
                            + (info.getSubject() != null ? info.getSubject() : "")
                            + " — " + info.getClassName() + "\".", true);
                    return;
                }

                // Enroll
                classRepo.enrollStudent(docId, uid, task -> {
                    runOnUiThread(() -> {
                        if (task.isSuccessful()) {
                            String label = (info != null)
                                    ? (info.getSubject() != null ? info.getSubject() : "") + " — " + info.getClassName()
                                    : code;
                            showStatus("✅  Successfully joined \"" + label + "\"!", true);
                            etJoinCode.setText("");
                            loadEnrolledClasses();
                        } else {
                            showStatus("Failed to join. Please try again.", false);
                        }
                    });
                });
            });
        });
    }

    private void showStatus(String msg, boolean success) {
        tvJoinStatus.setText(msg);
        tvJoinStatus.setTextColor(ContextCompat.getColor(this,
                success ? R.color.attendanceHigh : R.color.attendanceLow));
        tvJoinStatus.setVisibility(View.VISIBLE);
    }

    private void loadEnrolledClasses() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        // Query classes where enrolledStudents array contains this student's UID
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("classes")
                .whereArrayContains("enrolledStudents", uid)
                .get()
                .addOnSuccessListener(qs -> {
                    List<ClassInfo> list = qs.toObjects(ClassInfo.class);
                    runOnUiThread(() -> {
                        if (list.isEmpty()) {
                            tvNoEnrolled.setVisibility(View.VISIBLE);
                            rvEnrolledClasses.setVisibility(View.GONE);
                        } else {
                            tvNoEnrolled.setVisibility(View.GONE);
                            rvEnrolledClasses.setVisibility(View.VISIBLE);
                            adapter.updateList(list);
                        }
                    });
                });
    }
}
