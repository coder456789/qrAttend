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
    private TextView tvCountdown, tvLiveCount, tvSessionTimer;
    private MaterialButton btnEndSession;

    private QRRefreshManager refreshManager;
    private SessionRepository sessionRepo;
    private ListenerRegistration countListener;
    private CountDownTimer countDownTimer;
    private CountDownTimer sessionTimer;

    private String sessionId;
    private String sessionKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_qr);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ivQRCode        = findViewById(R.id.ivQRCode);
        tvCountdown     = findViewById(R.id.tvCountdown);
        tvLiveCount     = findViewById(R.id.tvLiveCount);
        tvSessionTimer  = findViewById(R.id.tvSessionTimer);
        btnEndSession   = findViewById(R.id.btnEndSession);

        sessionRepo = new SessionRepository();

        // Receive intent extras
        sessionId        = getIntent().getStringExtra("session_id");
        sessionKey       = getIntent().getStringExtra("session_key");
        String teacherId = getIntent().getStringExtra("teacher_id");
        String courseId  = getIntent().getStringExtra("course_id");
        double lat       = getIntent().getDoubleExtra("latitude", 0);
        double lng       = getIntent().getDoubleExtra("longitude", 0);

        if (sessionId == null || sessionKey == null) {
            com.qrattend.app.utils.SnackbarHelper.error(this, getString(R.string.error_generic));
            finish();
            return;
        }

        String geoHash = QRGeneratorUtil.buildGeoHash(lat, lng);

        refreshManager = new QRRefreshManager(sessionId, teacherId, courseId, geoHash, sessionKey);

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
                // FIX: was calling sessionRepo.updateSecureNonce() which doesn't exist.
                // Correct method name is updateNonce() — matches SessionRepository contract.
                sessionRepo.updateNonce(sessionId, nonce, key, task -> {
                    // Nonce updated in Firestore — students will get the fresh nonce on next read
                });
            }

            @Override
            public void onError(String reason) {
                runOnUiThread(() ->
                        com.qrattend.app.utils.SnackbarHelper.error(DisplayQRActivity.this,
                                getString(R.string.error_qr_generation)));
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

        int durationMinutes = getIntent().getIntExtra("duration_minutes", 2);
        startSessionTimer(durationMinutes);
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

    private void startSessionTimer(int durationMinutes) {
        long durationMs = durationMinutes * 60 * 1000L;
        sessionTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int minutes = (int) (millisUntilFinished / 60000);
                int seconds = (int) ((millisUntilFinished % 60000) / 1000);
                tvSessionTimer.setText(getString(R.string.session_time_remaining, minutes, seconds));
            }

            @Override
            public void onFinish() {
                tvSessionTimer.setText(getString(R.string.session_auto_ended));
                endSession();
            }
        };
        sessionTimer.start();
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
            // Clear active-device lock so teacher can log in from other devices
            String teacherId = getIntent().getStringExtra("teacher_id");
            if (teacherId != null) {
                java.util.Map<String, Object> unlock = new java.util.HashMap<>();
                unlock.put("activeDeviceId", com.google.firebase.firestore.FieldValue.delete());
                new com.qrattend.app.data.repository.TeacherRepository()
                        .updateTeacher(teacherId, unlock, t -> { /* fire-and-forget */ });
            }

            // Auto-mark absent: find class doc and write Absent for non-scanners
            String courseId  = getIntent().getStringExtra("course_id");
            if (teacherId != null && courseId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection(com.qrattend.app.utils.Constants.CLASSES)
                        .whereEqualTo("className",  courseId)
                        .whereEqualTo("teacherId",  teacherId)
                        .limit(1)
                        .get()
                        .addOnSuccessListener(qs -> {
                            if (qs != null && !qs.isEmpty()) {
                                String classDocId = qs.getDocuments().get(0).getId();
                                // Get the subject from the session for the record
                                sessionRepo.getSession(sessionId, session -> {
                                    String subject = session != null ? session.getSubject() : null;
                                    new com.qrattend.app.data.repository.AttendanceRepository()
                                            .markAbsentForMissing(sessionId, classDocId, subject);
                                });
                            }
                        });
            }

            com.qrattend.app.utils.SnackbarHelper.info(this, getString(R.string.session_ended));
            finish();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshManager  != null) refreshManager.stop();
        if (countListener   != null) countListener.remove();
        if (countDownTimer  != null) countDownTimer.cancel();
        if (sessionTimer    != null) sessionTimer.cancel();
    }
}