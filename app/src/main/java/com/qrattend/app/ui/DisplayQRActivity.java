package com.qrattend.app.ui;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;
import com.qrattend.app.R;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.qr.QRGeneratorUtil;
import com.qrattend.app.qr.QRRefreshManager;
import com.qrattend.app.utils.Constants;

public class DisplayQRActivity extends AppCompatActivity {

    private ImageView ivQRCode;
    private TextView tvCountdown, tvLiveCount;
    private MaterialButton btnEndSession;

    private QRRefreshManager refreshManager;
    private SessionRepository sessionRepo;
    private ListenerRegistration countListener;
    private CountDownTimer countDownTimer;

    private String sessionId;
    private String sessionKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_qr);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ivQRCode = findViewById(R.id.ivQRCode);
        tvCountdown = findViewById(R.id.tvCountdown);
        tvLiveCount = findViewById(R.id.tvLiveCount);
        btnEndSession = findViewById(R.id.btnEndSession);

        sessionRepo = new SessionRepository();

        // Receive intent extras
        sessionId = getIntent().getStringExtra("session_id");
        sessionKey = getIntent().getStringExtra("session_key");
        String teacherId = getIntent().getStringExtra("teacher_id");
        String courseId = getIntent().getStringExtra("course_id");
        double lat = getIntent().getDoubleExtra("latitude", 0);
        double lng = getIntent().getDoubleExtra("longitude", 0);

        if (sessionId == null || sessionKey == null) {
            Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Build geoHash using actual static method from QRGeneratorUtil
        String geoHash = QRGeneratorUtil.buildGeoHash(lat, lng);

        // QRRefreshManager constructor: (sessionId, teacherId, courseId, geoHash, sessionKey)
        refreshManager = new QRRefreshManager(sessionId, teacherId, courseId, geoHash, sessionKey);

        // start(RefreshListener) — listener has onNewQR(Bitmap), onNewNonce(nonce, sessionKey), onError(String)
        refreshManager.start(new QRRefreshManager.RefreshListener() {
            @Override
            public void onNewQR(Bitmap qrBitmap) {
                runOnUiThread(() -> {
                    ivQRCode.setImageBitmap(qrBitmap);
                    resetCountdown();
                });
            }

            @Override
            public void onNewNonce(String nonce, String key) {
                // SessionRepository.updateSecureNonce(sessionId, nonce, sessionKey, callback)
                sessionRepo.updateSecureNonce(sessionId, nonce, key, task -> {
                    // Nonce updated in Firestore
                });
            }

            @Override
            public void onError(String reason) {
                runOnUiThread(() ->
                        Toast.makeText(DisplayQRActivity.this,
                                getString(R.string.error_qr_generation), Toast.LENGTH_SHORT).show());
            }
        });

        // Listen to live attendance count
        countListener = sessionRepo.listenToAttendanceCount(sessionId,
                new SessionRepository.OnCountChangedListener() {
                    @Override
                    public void onCountChanged(int count) {
                        runOnUiThread(() -> tvLiveCount.setText(String.valueOf(count)));
                    }

                    @Override
                    public void onError(String errorMessage) {
                        // Silently handle
                    }
                });

        btnEndSession.setOnClickListener(v -> confirmEndSession());
    }

    private void resetCountdown() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        countDownTimer = new CountDownTimer(Constants.QR_REFRESH_INTERVAL_MS, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) (millisUntilFinished / 1000);
                tvCountdown.setText(getString(R.string.refresh_countdown, seconds));
            }

            @Override
            public void onFinish() {
                tvCountdown.setText(getString(R.string.refresh_countdown, 0));
            }
        };
        countDownTimer.start();
    }

    private void confirmEndSession() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_end_session)
                .setMessage(R.string.confirm_end_session_message)
                .setPositiveButton(R.string.yes, (d, w) -> endSession())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void endSession() {
        if (refreshManager != null) {
            refreshManager.stop();
        }

        sessionRepo.endSession(sessionId, task -> {
            Toast.makeText(this, getString(R.string.session_ended), Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshManager != null) refreshManager.stop();
        if (countListener != null) countListener.remove();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}
