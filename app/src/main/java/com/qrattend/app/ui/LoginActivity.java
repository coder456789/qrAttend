package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.qrattend.app.R;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.utils.Constants;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressLogin;
    private TextView tvSignUpLink;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager();

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressLogin = findViewById(R.id.progressLogin);
        tvSignUpLink = findViewById(R.id.tvSignUpLink);

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvSignUpLink.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, SignupActivity.class));
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        // Validate
        if (email.isEmpty()) {
            tilEmail.setError(getString(R.string.error_empty_email));
            return;
        }
        tilEmail.setError(null);

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.error_invalid_email));
            return;
        }
        tilEmail.setError(null);

        if (password.isEmpty()) {
            tilPassword.setError(getString(R.string.error_empty_password));
            return;
        }
        tilPassword.setError(null);

        if (password.length() < 6) {
            tilPassword.setError(getString(R.string.error_short_password));
            return;
        }
        tilPassword.setError(null);

        setLoading(true);

        authManager.loginWithEmail(email, password, task -> {
            if (task.isSuccessful()) {
                authManager.getUserRole(LoginActivity.this, role -> {
                    setLoading(false);
                    if (role == null || "unauthorized".equals(role)) {
                        Toast.makeText(this, getString(R.string.error_login_failed), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    navigateByRole(role);
                });
            } else {
                setLoading(false);
                Toast.makeText(this, getString(R.string.error_login_failed), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void navigateByRole(String role) {
        Intent intent;
        switch (role) {
            case Constants.ROLE_STUDENT:
                intent = new Intent(this, StudentDashboardActivity.class);
                break;
            case Constants.ROLE_TEACHER:
                intent = new Intent(this, TeacherDashboardActivity.class);
                break;
            case Constants.ROLE_ADMIN:
                intent = new Intent(this, AdminDashboardActivity.class);
                break;
            default:
                Toast.makeText(this, getString(R.string.error_login_failed), Toast.LENGTH_SHORT).show();
                return;
        }
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}
