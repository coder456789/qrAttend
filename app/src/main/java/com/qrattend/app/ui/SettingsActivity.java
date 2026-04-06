package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.qrattend.app.BuildConfig;
import com.qrattend.app.R;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.model.Teacher;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.data.repository.TeacherRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.utils.Constants;

import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPhone;
    private MaterialButton btnSaveProfile, btnLogout;
    private SwitchMaterial switchNotifications, switchDarkMode;
    private TextView tvVersion;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etName = findViewById(R.id.etSettingsName);
        etEmail = findViewById(R.id.etSettingsEmail);
        etPhone = findViewById(R.id.etSettingsPhone);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchDarkMode = findViewById(R.id.switchDarkMode);
        tvVersion = findViewById(R.id.tvVersion);

        authManager = new AuthManager();

        tvVersion.setText(getString(R.string.version_info, BuildConfig.VERSION_NAME));

        loadProfile();

        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void loadProfile() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        String cachedRole = authManager.getCachedRole(this);

        if (Constants.ROLE_STUDENT.equals(cachedRole)) {
            new StudentRepository().getStudent(uid, student -> {
                if (student != null) {
                    etName.setText(student.getName());
                    etEmail.setText(student.getEmail());
                    etPhone.setText(student.getPhone());
                }
            });
        } else if (Constants.ROLE_TEACHER.equals(cachedRole)) {
            new TeacherRepository().getTeacher(uid, teacher -> {
                if (teacher != null) {
                    etName.setText(teacher.getName());
                    etEmail.setText(teacher.getEmail());
                    // Teacher model doesn't have phone — leave blank
                }
            });
        }
    }

    private void saveProfile() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        String cachedRole = authManager.getCachedRole(this);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);

        if (Constants.ROLE_STUDENT.equals(cachedRole)) {
            updates.put("phone", phone);
            new StudentRepository().updateStudent(uid, updates, task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.error_save_profile), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (Constants.ROLE_TEACHER.equals(cachedRole)) {
            new TeacherRepository().updateTeacher(uid, updates, task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.error_save_profile), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.confirm_logout)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    // AuthManager.logout(Context) — actual signature requires Context
                    authManager.logout(SettingsActivity.this);
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
}
