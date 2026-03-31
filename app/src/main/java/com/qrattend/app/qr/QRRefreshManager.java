package com.qrattend.app.qr;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.qrattend.app.security.NonceManager;
import com.qrattend.app.utils.Constants;

/**
 * QRRefreshManager — Member 2 (Core Logic & QR Lead)
 *
 * PRD §5.1 — Refreshes the QR code every Constants.QR_REFRESH_INTERVAL_MS (10 000 ms).
 *
 * Each tick:
 *   1. Generates a fresh nonce via NonceManager.generateNonce()
 *   2. Builds encrypted QR Bitmap via QRGeneratorUtil.generateQRBitmap()
 *   3. Calls onNewNonce() → caller passes to SessionRepository.updateNonce()
 *      which writes the nonce to AttendanceSession.qrCode in Firestore
 *   4. Calls onNewQR() → DisplayQRActivity (Member 1) updates the ImageView
 *
 * Integration with Member 3:
 *   - sessionId  → AttendanceSession.getSessionId()
 *   - teacherId  → AuthManager.getCurrentUserId()
 *   - courseId   → AttendanceSession.getClassId()
 *   - sessionKey → AttendanceSession.getSessionKey() via SessionRepository
 *   - geoHash    → QRGeneratorUtil.buildGeoHash(lat, lng)
 *   - onNewNonce → sessionRepo.updateNonce(sessionId, nonce, task -> {})
 *
 * Usage (in DisplayQRActivity — Member 1):
 *   QRRefreshManager manager = new QRRefreshManager(
 *       session.getSessionId(),
 *       authManager.getCurrentUserId(),
 *       session.getClassId(),
 *       QRGeneratorUtil.buildGeoHash(session.getLatitude(), session.getLongitude()),
 *       session.getSessionKey());
 *
 *   manager.start(new QRRefreshManager.RefreshListener() {
 *       public void onNewQR(Bitmap qr)      { qrImageView.setImageBitmap(qr); }
 *       public void onNewNonce(String nonce) { sessionRepo.updateNonce(sessionId, nonce, t -> {}); }
 *       public void onError(String reason)   { showToast(reason); }
 *   });
 *
 *   // In onDestroy():
 *   manager.stop();
 */
public class QRRefreshManager {

    // -----------------------------------------------------------------------
    // Listener interface
    // -----------------------------------------------------------------------

    public interface RefreshListener {
        /** Called on main thread with fresh QR Bitmap for DisplayQRActivity. */
        void onNewQR(Bitmap qrBitmap);

        /**
         * Called with new nonce — pass to SessionRepository.updateNonce()
         * so students' validateNonce() compares against the live Firestore value.
         */
        void onNewNonce(String nonce, String sessionKey);

        /** Called if QR generation fails. */
        void onError(String reason);
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final String sessionId;
    private final String teacherId;
    private final String courseId;
    private final String geoHash;

    /**
     * AES session key from AttendanceSession.getSessionKey().
     * Used by QRGeneratorUtil to encrypt each new QR payload.
     */
    private final String sessionKey;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private       boolean running = false;
    private       RefreshListener listener;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * @param sessionId  AttendanceSession.getSessionId()
     * @param teacherId  AuthManager.getCurrentUserId() (Member 3)
     * @param courseId   AttendanceSession.getClassId()
     * @param geoHash    QRGeneratorUtil.buildGeoHash(session.lat, session.lng)
     * @param sessionKey AttendanceSession.getSessionKey()
     */
    public QRRefreshManager(String sessionId, String teacherId,
                            String courseId,  String geoHash,
                            String sessionKey) {
        this.sessionId  = sessionId;
        this.teacherId  = teacherId;
        this.courseId   = courseId;
        this.geoHash    = geoHash;
        this.sessionKey = sessionKey;
    }

    // -----------------------------------------------------------------------
    // Start / Stop
    // -----------------------------------------------------------------------

    /**
     * Starts the refresh loop using Constants.QR_REFRESH_INTERVAL_MS.
     * First QR is generated immediately. Safe to call multiple times.
     */
    public void start(RefreshListener listener) {
        if (running) return;
        this.listener = listener;
        running = true;
        handler.post(refreshRunnable);
    }

    /** Stops the refresh loop. Call from onDestroy() or when session ends. */
    public void stop() {
        running = false;
        handler.removeCallbacks(refreshRunnable);
    }

    public boolean isRunning() { return running; }

    // -----------------------------------------------------------------------
    // Refresh runnable
    // -----------------------------------------------------------------------

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!running) return;

            try {
                String nonce = NonceManager.generateNonce();

                Bitmap qrBitmap = QRGeneratorUtil.generateQRBitmap(
                        sessionId, teacherId, courseId, nonce, geoHash, sessionKey);

                if (listener != null) {
                    // Member 3: sessionRepo.updateNonce(sessionId, nonce, task -> {})
                    listener.onNewNonce(nonce, sessionKey);
                    // Member 1: qrImageView.setImageBitmap(qrBitmap)
                    listener.onNewQR(qrBitmap);
                }
            } catch (Exception e) {
                if (listener != null) {
                    listener.onError("qr_generation_failed: " + e.getMessage());
                }
            }

            // Schedule next refresh using Constants.QR_REFRESH_INTERVAL_MS
            if (running) {
                handler.postDelayed(this, Constants.QR_REFRESH_INTERVAL_MS);
            }
        }
    };
}