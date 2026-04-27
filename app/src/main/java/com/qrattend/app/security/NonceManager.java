package com.qrattend.app.security;

import com.qrattend.app.utils.Constants;
import com.qrattend.app.utils.ValidationResult;

import java.security.SecureRandom;

/**
 * NonceManager — Member 2 (Core Logic & QR Lead)
 *
 * Generates and validates one-time nonces embedded in QR payloads.
 *
 * Integration with Member 3:
 *   - Teacher side: generateNonce() is called every Constants.QR_REFRESH_INTERVAL_MS
 *     (10 000 ms) by QRRefreshManager. The new nonce is passed to
 *     SessionRepository.updateNonce(sessionId, nonce, callback) which writes
 *     it to AttendanceSession.qrCode in Firestore.
 *   - Student side: validateNonce() compares the scanned nonce against
 *     AttendanceSession.getQrCode() fetched by SessionRepository.getSession().
 *     Duplicate submissions are blocked at the Firestore level because each
 *     AttendanceRecord uses studentId as the document ID.
 */
public class NonceManager {

    /**
     * How long (ms) a nonce remains valid.
     * Matches Constants.QR_REFRESH_INTERVAL_MS.
     */
    public static final long NONCE_VALIDITY_MS = Constants.QR_REFRESH_INTERVAL_MS;

    /**
     * Allowed clock skew + processing delay between teacher and student devices.
     * Increased from 15s to 30s to accommodate multi-sample location collection
     * (LocationHelper collects up to 5 GPS samples over ~12-14 seconds after the
     * QR is scanned, so by validation time the nonce may have rotated once).
     */
    public static final long CLOCK_SKEW_MS = 30_000L;

    private static final int NONCE_BYTE_LENGTH = 24; // 192 bits → 32-char Base64

    // -----------------------------------------------------------------------
    // Generation (Teacher side)
    // -----------------------------------------------------------------------

    /**
     * Generates a cryptographically secure random nonce.
     * Called every Constants.QR_REFRESH_INTERVAL_MS by QRRefreshManager.
     * Result passed to SessionRepository.updateNonce() by Member 3.
     *
     * @return URL-safe Base64-encoded nonce (no padding)
     */
    public static String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTE_LENGTH];
        new SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // -----------------------------------------------------------------------
    // Validation (Student side)
    // -----------------------------------------------------------------------

    /**
     * Validates a scanned nonce against the session's current nonce and timestamp.
     * Does NOT check the previous nonce grace window — use the overloaded version
     * with previousNonce parameters for full grace-window support.
     *
     * @param scannedNonce   Nonce extracted from the decrypted QR payload
     * @param sessionNonce   AttendanceSession.getQrCode() from SessionRepository.getSession()
     * @param qrTimestamp    Epoch millis embedded in the QR payload
     * @return               ValidationResult using Constants.REASON_NONCE_EXPIRED or pass()
     */
    public static ValidationResult validateNonce(String scannedNonce,
                                                 String sessionNonce,
                                                 long   qrTimestamp) {
        return validateNonce(scannedNonce, sessionNonce, qrTimestamp, null, 0L);
    }

    /**
     * Validates a scanned nonce against the session's current nonce AND the
     * previous nonce (if still within its grace window).
     *
     * FIX: When the QR refreshes every 10s, the old nonce is preserved in
     * Firestore as {@code previousQrCode} with a 20-second grace period.
     * Students whose GPS multi-sample collection (up to 14s) delays validation
     * can still pass by matching the previous nonce.
     *
     * @param scannedNonce           Nonce from the decrypted QR payload
     * @param sessionNonce           Current AttendanceSession.getQrCode()
     * @param qrTimestamp            Epoch millis embedded in the QR payload
     * @param previousNonce          AttendanceSession.getPreviousQrCode() (nullable)
     * @param previousNonceExpiryMs  AttendanceSession.getPreviousNonceExpiryMs()
     * @return                       ValidationResult
     */
    public static ValidationResult validateNonce(String scannedNonce,
                                                 String sessionNonce,
                                                 long   qrTimestamp,
                                                 String previousNonce,
                                                 long   previousNonceExpiryMs) {
        long now = System.currentTimeMillis();
        long age = now - qrTimestamp;

        // Time-window check — rejects screenshots shared even seconds later
        if (age < 0 || age > NONCE_VALIDITY_MS + CLOCK_SKEW_MS) {
            return ValidationResult.fail(Constants.REASON_NONCE_EXPIRED);
        }

        // Check 1: Match against current nonce
        if (scannedNonce != null && scannedNonce.equals(sessionNonce)) {
            return ValidationResult.pass();
        }

        // Check 2: Match against previous nonce (grace window)
        if (previousNonce != null && !previousNonce.isEmpty()
                && scannedNonce != null && scannedNonce.equals(previousNonce)
                && now <= previousNonceExpiryMs) {
            return ValidationResult.pass();
        }

        return ValidationResult.fail("nonce_mismatch");
    }

    // -----------------------------------------------------------------------
    // Timestamp helpers
    // -----------------------------------------------------------------------

    /** Returns current epoch millis — used when building QR payload. */
    public static long currentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * Checks whether a QR timestamp is fresh enough to be valid.
     *
     * @param qrTimestamp Epoch millis embedded in the QR payload
     * @return true if within the valid window
     */
    public static boolean isTimestampFresh(long qrTimestamp) {
        long age = System.currentTimeMillis() - qrTimestamp;
        return age >= 0 && age <= NONCE_VALIDITY_MS + CLOCK_SKEW_MS;
    }
}