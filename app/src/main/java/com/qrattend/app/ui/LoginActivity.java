package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

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
    private TextView tvSignUpLink, tvForgotPassword;
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
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        btnLogin.setOnClickListener(v -> attemptLogin());

        tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());

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

    private void showForgotPasswordDialog() {
        // Build dialog with email input
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        final EditText etResetEmail = new EditText(this);
        etResetEmail.setHint(R.string.email);
        etResetEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        // Pre-fill with email from login field if available
        if (etEmail.getText() != null && !etEmail.getText().toString().trim().isEmpty()) {
            etResetEmail.setText(etEmail.getText().toString().trim());
        }
        layout.addView(etResetEmail);

        new AlertDialog.Builder(this)
                .setTitle(R.string.forgot_password_title)
                .setMessage(R.string.forgot_password_message)
                .setView(layout)
                .setPositiveButton(R.string.send_reset_link, (dialog, which) -> {
                    String email = etResetEmail.getText().toString().trim();
                    if (email.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_empty_email), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    authManager.sendPasswordResetEmail(email, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, getString(R.string.reset_email_sent), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.error_reset_email), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
