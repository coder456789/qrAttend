package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.card.MaterialCardView;
import com.qrattend.app.R;
import com.qrattend.app.data.repository.ClassRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.data.repository.TeacherRepository;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView tvStudentCount, tvTeacherCount, tvClassCount;
    private MaterialCardView cardManageUsers, cardManageClasses;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvStudentCount = findViewById(R.id.tvStudentCount);
        tvTeacherCount = findViewById(R.id.tvTeacherCount);
        tvClassCount = findViewById(R.id.tvClassCount);
        cardManageUsers = findViewById(R.id.cardManageUsers);
        cardManageClasses = findViewById(R.id.cardManageClasses);

        cardManageUsers.setOnClickListener(v ->
                startActivity(new Intent(this, ManageUsersActivity.class)));

        cardManageClasses.setOnClickListener(v ->
                startActivity(new Intent(this, ManageClassesActivity.class)));

        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
    }

    private void loadStats() {
        new StudentRepository().getAllStudents(students -> {
            int count = students != null ? students.size() : 0;
            tvStudentCount.setText(String.valueOf(count));
        });

        new TeacherRepository().getAllTeachers(teachers -> {
            int count = teachers != null ? teachers.size() : 0;
            tvTeacherCount.setText(String.valueOf(count));
        });

        new ClassRepository().getAllClasses(classes -> {
            int count = classes != null ? classes.size() : 0;
            tvClassCount.setText(String.valueOf(count));
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
