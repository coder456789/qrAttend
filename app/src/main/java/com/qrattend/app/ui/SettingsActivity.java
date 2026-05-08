package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etName, etEmail, etPhone;
    private MaterialButton btnSaveProfile, btnLogout, btnChangePassword;
    private MaterialButton btnUnbindDevice;
    private MaterialCardView cardDeviceBinding;
    private SwitchMaterial switchNotifications, switchDarkMode;
    private TextView tvVersion;

    private AuthManager authManager;
    private StudentRepository studentRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etName              = findViewById(R.id.etSettingsName);
        etEmail             = findViewById(R.id.etSettingsEmail);
        etPhone             = findViewById(R.id.etSettingsPhone);
        btnSaveProfile      = findViewById(R.id.btnSaveProfile);
        btnLogout           = findViewById(R.id.btnLogout);
        btnChangePassword   = findViewById(R.id.btnChangePassword);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchDarkMode      = findViewById(R.id.switchDarkMode);
        tvVersion           = findViewById(R.id.tvVersion);
        cardDeviceBinding   = findViewById(R.id.cardDeviceBinding);
        btnUnbindDevice     = findViewById(R.id.btnUnbindDevice);

        authManager = new AuthManager();
        studentRepo = new StudentRepository();

        tvVersion.setText(getString(R.string.version_info, BuildConfig.VERSION_NAME));

        loadProfile();

        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnLogout.setOnClickListener(v -> confirmLogout());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnUnbindDevice.setOnClickListener(v -> handleUnbindDevice());
    }

    private void loadProfile() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        String cachedRole = authManager.getCachedRole(this);

        if (Constants.ROLE_STUDENT.equals(cachedRole)) {
            // Show the Device Binding card for students
            if (cardDeviceBinding != null) {
                cardDeviceBinding.setVisibility(View.VISIBLE);
            }
            studentRepo.getStudent(uid, student -> {
                if (student != null) {
                    etName.setText(student.getName());
                    etEmail.setText(student.getEmail());
                    etPhone.setText(student.getPhone());
                }
            });
        } else if (Constants.ROLE_TEACHER.equals(cachedRole)) {
            // Teachers do not see the Device Binding card
            if (cardDeviceBinding != null) {
                cardDeviceBinding.setVisibility(View.GONE);
            }
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

        String name  = etName.getText()  != null ? etName.getText().toString().trim()  : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        String cachedRole = authManager.getCachedRole(this);

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);

        if (Constants.ROLE_STUDENT.equals(cachedRole)) {
            updates.put("phone", phone);
            studentRepo.updateStudent(uid, updates, task -> {
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

    // ── Device Unbinding ────────────────────────────────────────────────────

    private static final String PREFS_NAME      = "qrattend_prefs";
    private static final String KEY_LAST_UNBIND = "lastUnbindDate";

    /**
     * Entry point when the student taps "Unbind Device".
     * Reads the 30-day rate-limit from SharedPreferences (NOT Firestore),
     * then shows the confirmation warning dialog if eligible.
     */
    private void handleUnbindDevice() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        // Rate-limit check from SharedPreferences
        String lastUnbindDate = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LAST_UNBIND, null);
        if (lastUnbindDate != null && !lastUnbindDate.isEmpty()) {
            int daysRemaining = daysUntilEligible(lastUnbindDate);
            if (daysRemaining > 0) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.unbind_device_warning_title))
                        .setMessage(getString(R.string.unbind_device_rate_limit, daysRemaining))
                        .setPositiveButton(getString(R.string.ok), null)
                        .show();
                return;
            }
        }

        // Eligible — show the warning / confirmation dialog
        showUnbindWarningDialog(uid);
    }

    /**
     * Shows the two-button warning modal:
     * "Continue to Unbind" → proceeds with unbinding
     * "Go Back"            → dismisses, no action
     */
    private void showUnbindWarningDialog(String uid) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.unbind_device_warning_title))
                .setMessage(getString(R.string.unbind_device_warning_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.unbind_device_continue), (dialog, which) -> {
                    performUnbind(uid);
                })
                .setNegativeButton(getString(R.string.unbind_device_go_back), (dialog, which) -> {
                    dialog.dismiss();
                })
                .show();
    }

    /**
     * Clears deviceId/deviceId2 in Firestore. On success, saves today's date
     * to SharedPreferences as the rate-limit timestamp.
     */
    private void performUnbind(String uid) {
        btnUnbindDevice.setEnabled(false);

        studentRepo.unbindDevice(uid, task -> {
            runOnUiThread(() -> {
                btnUnbindDevice.setEnabled(true);
                if (task.isSuccessful()) {
                    // Save rate-limit date locally
                    String today = new SimpleDateFormat("yyyy-MM-dd",
                            Locale.getDefault()).format(new Date());
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                            .edit()
                            .putString(KEY_LAST_UNBIND, today)
                            .apply();

                    new AlertDialog.Builder(this)
                            .setTitle(getString(R.string.unbind_device_warning_title))
                            .setMessage(getString(R.string.unbind_device_success))
                            .setPositiveButton(getString(R.string.ok), null)
                            .show();
                } else {
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /**
     * Returns how many more days the student must wait before they can unbind again.
     * Returns 0 (or negative) when eligible.
     *
     * @param lastUnbindDate ISO date string "yyyy-MM-dd"
     */
    private int daysUntilEligible(String lastUnbindDate) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date lastDate = sdf.parse(lastUnbindDate);
            if (lastDate == null) return 0;

            long nowMs   = System.currentTimeMillis();
            long lastMs  = lastDate.getTime();
            long diffMs  = nowMs - lastMs;
            long daysPassed = TimeUnit.MILLISECONDS.toDays(diffMs);
            return (int) Math.max(0, 30 - daysPassed);
        } catch (ParseException e) {
            return 0; // Unparseable date — treat as eligible
        }
    }

    // ── Logout ──────────────────────────────────────────────────────────────

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage(R.string.confirm_logout)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    authManager.logout(SettingsActivity.this);
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    // ── Change Password ─────────────────────────────────────────────────────

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        final EditText etCurrentPass = new EditText(this);
        etCurrentPass.setHint(R.string.current_password);
        etCurrentPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etCurrentPass);

        final EditText etNewPass = new EditText(this);
        etNewPass.setHint(R.string.new_password);
        etNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNewPass);

        final EditText etConfirmNewPass = new EditText(this);
        etConfirmNewPass.setHint(R.string.confirm_new_password);
        etConfirmNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirmNewPass);

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_password_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String currentPass = etCurrentPass.getText().toString().trim();
                    String newPass     = etNewPass.getText().toString().trim();
                    String confirmPass = etConfirmNewPass.getText().toString().trim();

                    if (currentPass.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_current_password_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_new_password_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPass.length() < 6) {
                        Toast.makeText(this, getString(R.string.error_new_password_short), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirmPass)) {
                        Toast.makeText(this, getString(R.string.error_new_password_mismatch), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    authManager.changePassword(currentPass, newPass, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, getString(R.string.password_changed), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.error_change_password), Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
