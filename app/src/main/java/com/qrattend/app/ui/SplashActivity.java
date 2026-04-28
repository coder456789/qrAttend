package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.qrattend.app.R;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.utils.Constants;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Force dark mode globally for the entire app
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndRoute, SPLASH_DELAY_MS);
    }

    private void checkAuthAndRoute() {
        AuthManager authManager = new AuthManager();

        if (authManager.isLoggedIn()) {
            authManager.getUserRole(this, role -> {
                if (role == null || "unauthorized".equals(role)) {
                    navigateTo(LoginActivity.class);
                    return;
                }
                switch (role) {
                    case Constants.ROLE_STUDENT:
                        navigateTo(StudentDashboardActivity.class);
                        break;
                    case Constants.ROLE_TEACHER:
                        checkTeacherDeviceAndProceed(authManager);
                        break;
                    default:
                        navigateTo(LoginActivity.class);
                        break;
                }
            });
        } else {
            navigateTo(LoginActivity.class);
        }
    }

    /**
     * Same device-enforcement as LoginActivity:
     * - activeDeviceId set + different device → block (session active elsewhere)
     * - otherwise → overwrite deviceId (auto-logout old device) → proceed
     */
    private void checkTeacherDeviceAndProceed(AuthManager authManager) {
        String uid = authManager.getCurrentUserId();
        if (uid == null) { navigateTo(LoginActivity.class); return; }

        String thisDeviceId = com.qrattend.app.security.DeviceFingerprint.getFingerprint(this);

        new com.qrattend.app.data.repository.TeacherRepository().getTeacher(uid, teacher -> {
            runOnUiThread(() -> {
                if (teacher == null) {
                    registerAndProceed(uid, thisDeviceId);
                    return;
                }

                String activeDevice = teacher.getActiveDeviceId();
                if (activeDevice != null && !activeDevice.isEmpty()
                        && !activeDevice.equals(thisDeviceId)) {
                    // Session active on another device → block
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Session Active on Another Device")
                            .setMessage("You have an active attendance session running on another device.\n\n"
                                    + "Please end that session first, or wait for it to expire.")
                            .setPositiveButton(getString(R.string.ok), (d, w) -> {
                                authManager.logout(this);
                                navigateTo(LoginActivity.class);
                            })
                            .setCancelable(false)
                            .show();
                    return;
                }

                // No active session on another device → overwrite old deviceId
                registerAndProceed(uid, thisDeviceId);
            });
        });
    }

    private void registerAndProceed(String uid, String deviceId) {
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("deviceId", deviceId);
        new com.qrattend.app.data.repository.TeacherRepository().updateTeacher(uid, update, task -> {
            runOnUiThread(() -> navigateTo(TeacherDashboardActivity.class));
        });
    }

    private void navigateTo(Class<?> activityClass) {
        startActivity(new Intent(SplashActivity.this, activityClass));
        finish();
    }
}

