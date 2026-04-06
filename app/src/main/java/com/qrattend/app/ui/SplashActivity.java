package com.qrattend.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.qrattend.app.R;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.utils.Constants;

public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
                        navigateTo(TeacherDashboardActivity.class);
                        break;
                    case Constants.ROLE_ADMIN:
                        navigateTo(AdminDashboardActivity.class);
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

    private void navigateTo(Class<?> activityClass) {
        startActivity(new Intent(SplashActivity.this, activityClass));
        finish();
    }
}
