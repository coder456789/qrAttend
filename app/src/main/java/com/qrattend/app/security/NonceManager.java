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

    /** Allowed clock skew between teacher and student devices. */
    public static final long CLOCK_SKEW_MS = 15_000L;

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
     *
     * @param scannedNonce   Nonce extracted from the decrypted QR payload
     * @param sessionNonce   AttendanceSession.getQrCode() from SessionRepository.getSession()
     * @param qrTimestamp    Epoch millis embedded in the QR payload
     * @return               ValidationResult using Constants.REASON_NONCE_EXPIRED or pass()
     */
    public static ValidationResult validateNonce(String scannedNonce,
                                                 String sessionNonce,
                                                 long   qrTimestamp) {
        long now = System.currentTimeMillis();
        long age = now - qrTimestamp;

        // Time-window check — rejects screenshots shared even seconds later
        if (age < 0 || age > NONCE_VALIDITY_MS + CLOCK_SKEW_MS) {
            return ValidationResult.fail(Constants.REASON_NONCE_EXPIRED);
        }

        // Nonce match — must equal AttendanceSession.getQrCode() from Firestore
        if (scannedNonce == null || !scannedNonce.equals(sessionNonce)) {
            return ValidationResult.fail("nonce_mismatch");
        }

        return ValidationResult.pass();
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