package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.qrattend.app.R;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.model.Teacher;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.data.repository.TeacherRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.utils.Constants;

public class SignupActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPhone, etPassword, etConfirmPassword, etRollNumber;
    private TextInputLayout tilName, tilSignupEmail, tilPhone, tilSignupPassword, tilConfirmPassword, tilRollNumber;
    private RadioGroup rgRole;
    private MaterialButton btnSignup;
    private ProgressBar progressSignup;
    private TextView tvLoginLink;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        authManager = new AuthManager();

        tilName = findViewById(R.id.tilName);
        tilSignupEmail = findViewById(R.id.tilSignupEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilSignupPassword = findViewById(R.id.tilSignupPassword);
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword);
        tilRollNumber = findViewById(R.id.tilRollNumber);

        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etSignupEmail);
        etPhone = findViewById(R.id.etPhone);
        etPassword = findViewById(R.id.etSignupPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        etRollNumber = findViewById(R.id.etRollNumber);

        rgRole = findViewById(R.id.rgRole);
        btnSignup = findViewById(R.id.btnSignup);
        progressSignup = findViewById(R.id.progressSignup);
        tvLoginLink = findViewById(R.id.tvLoginLink);

        // Show/hide roll number based on role
        rgRole.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbStudent) {
                tilRollNumber.setVisibility(View.VISIBLE);
            } else {
                tilRollNumber.setVisibility(View.GONE);
            }
        });

        btnSignup.setOnClickListener(v -> attemptSignup());

        tvLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    private boolean isStudentRole() {
        return rgRole.getCheckedRadioButtonId() == R.id.rbStudent;
    }

    private void attemptSignup() {
        String name = getText(etName);
        String email = getText(etEmail);
        String phone = getText(etPhone);
        String password = getText(etPassword);
        String confirmPassword = getText(etConfirmPassword);
        String rollNumber = getText(etRollNumber);

        // Validation
        if (name.isEmpty()) { tilName.setError(getString(R.string.error_empty_name)); return; }
        tilName.setError(null);

        if (email.isEmpty()) { tilSignupEmail.setError(getString(R.string.error_empty_email)); return; }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilSignupEmail.setError(getString(R.string.error_invalid_email)); return;
        }
        tilSignupEmail.setError(null);

        if (phone.isEmpty()) { tilPhone.setError(getString(R.string.error_empty_phone)); return; }
        tilPhone.setError(null);

        if (password.isEmpty()) { tilSignupPassword.setError(getString(R.string.error_empty_password)); return; }
        if (password.length() < 6) { tilSignupPassword.setError(getString(R.string.error_short_password)); return; }
        tilSignupPassword.setError(null);

        if (!password.equals(confirmPassword)) {
            tilConfirmPassword.setError(getString(R.string.error_password_mismatch)); return;
        }
        tilConfirmPassword.setError(null);

        if (isStudentRole() && rollNumber.isEmpty()) {
            tilRollNumber.setError(getString(R.string.error_empty_roll_number)); return;
        }
        tilRollNumber.setError(null);

        setLoading(true);

        authManager.signupWithEmail(email, password, task -> {
            if (task.isSuccessful()) {
                String uid = authManager.getCurrentUserId();
                if (uid == null) {
                    setLoading(false);
                    Toast.makeText(this, getString(R.string.error_signup_failed), Toast.LENGTH_LONG).show();
                    return;
                }

                if (isStudentRole()) {
                    // Build Student using actual constructor:
                    // Student(name, rollNo, className, email, phone, deviceId, deviceId2, fcmToken)
                    Student student = new Student(name, rollNumber, "", email, phone, "", "", "");
                    StudentRepository studentRepo = new StudentRepository();
                    studentRepo.addStudent(uid, student, addTask -> {
                        setLoading(false);
                        if (addTask.isSuccessful()) {
                            navigateTo(StudentDashboardActivity.class);
                        } else {
                            Toast.makeText(this, getString(R.string.error_signup_failed), Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    // Build Teacher using actual constructor:
                    // Teacher(name, email, subject, classroom, fcmToken)
                    Teacher teacher = new Teacher(name, email, "", "", "");
                    TeacherRepository teacherRepo = new TeacherRepository();
                    teacherRepo.addTeacher(uid, teacher, addTask -> {
                        setLoading(false);
                        if (addTask.isSuccessful()) {
                            navigateTo(TeacherDashboardActivity.class);
                        } else {
                            Toast.makeText(this, getString(R.string.error_signup_failed), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } else {
                setLoading(false);
                String errorMsg = task.getException() != null
                        ? task.getException().getMessage()
                        : getString(R.string.error_signup_failed);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateTo(Class<?> activityClass) {
        Intent intent = new Intent(this, activityClass);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressSignup.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSignup.setEnabled(!loading);
    }

    private String getText(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
