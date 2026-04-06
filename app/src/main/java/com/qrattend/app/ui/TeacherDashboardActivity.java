package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qrattend.app.R;
import com.qrattend.app.data.model.ClassInfo;
import com.qrattend.app.data.repository.ClassRepository;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.data.repository.TeacherRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.ui.adapters.ClassListAdapter;

public class TeacherDashboardActivity extends AppCompatActivity {

    private TextView tvTeacherName, tvTeacherSubject, tvEmptyClasses;
    private RecyclerView rvClasses;
    private FloatingActionButton fabStartSession;
    private ClassListAdapter adapter;

    private AuthManager authManager;
    private TeacherRepository teacherRepo;
    private ClassRepository classRepo;
    private SessionRepository sessionRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teacher_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvTeacherName = findViewById(R.id.tvTeacherName);
        tvTeacherSubject = findViewById(R.id.tvTeacherSubject);
        tvEmptyClasses = findViewById(R.id.tvEmptyClasses);
        rvClasses = findViewById(R.id.rvClasses);
        fabStartSession = findViewById(R.id.fabStartSession);

        authManager = new AuthManager();
        teacherRepo = new TeacherRepository();
        classRepo = new ClassRepository();
        sessionRepo = new SessionRepository();

        adapter = new ClassListAdapter(new ClassListAdapter.OnClassClickListener() {
            @Override
            public void onClassClick(ClassInfo classInfo, int position) {
                // Find active session for this class and navigate
                sessionRepo.getActiveSession(classInfo.getClassName(), session -> {
                    if (session != null && session.getSessionId() != null) {
                        Intent intent = new Intent(TeacherDashboardActivity.this,
                                SessionAttendanceActivity.class);
                        intent.putExtra("session_id", session.getSessionId());
                        startActivity(intent);
                    }
                });
            }

            @Override
            public void onClassLongClick(ClassInfo classInfo, int position) {
                // No-op for teacher dashboard
            }
        });

        rvClasses.setLayoutManager(new LinearLayoutManager(this));
        rvClasses.setAdapter(adapter);

        fabStartSession.setOnClickListener(v ->
                startActivity(new Intent(this, StartSessionActivity.class)));

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        teacherRepo.getTeacher(uid, teacher -> {
            if (teacher != null) {
                tvTeacherName.setText(getString(R.string.welcome_teacher, teacher.getName()));
                tvTeacherSubject.setText(teacher.getSubject());
            }
        });

        classRepo.getClassesByTeacher(uid, classes -> {
            if (classes == null || classes.isEmpty()) {
                tvEmptyClasses.setVisibility(View.VISIBLE);
                rvClasses.setVisibility(View.GONE);
            } else {
                tvEmptyClasses.setVisibility(View.GONE);
                rvClasses.setVisibility(View.VISIBLE);
                adapter.updateList(classes);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.settings);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
