package com.qrattend.app.utils;

/**
 * ValidationResult — Shared contract between Member 2 and Member 3.
 *
 * Member 2's ProxyDetectionEngine returns this object.
 * Member 3's AttendanceRepository.markAttendance() reads it to write:
 *   - status      → Constants.STATUS_PRESENT or Constants.STATUS_REJECTED
 *   - rejectionReason → one of Constants.REASON_* values
 *
 * into Firestore: attendanceSessions/{sessionId}/records/{studentId}
 *
 * All rejection strings use Constants so they never go out of sync:
 *   Constants.REASON_LOCATION_MISMATCH  "location_mismatch"
 *   Constants.REASON_DEVICE_MISMATCH    "device_mismatch"
 *   Constants.REASON_NONCE_EXPIRED      "nonce_expired"
 *   Constants.REASON_ROOT_DETECTED      "root_detected"
 *   Constants.REASON_NONE               ""
 */
public class ValidationResult {

    public final boolean success;
    public final String  rejectionReason; // null when success == true

    public ValidationResult(boolean success, String rejectionReason) {
        this.success         = success;
        this.rejectionReason = rejectionReason;
    }

    /** Convenience factory for a passing result. */
    public static ValidationResult pass() {
        return new ValidationResult(true, null);
    }

    /** Convenience factory for a failing result. */
    public static ValidationResult fail(String reason) {
        return new ValidationResult(false, reason);
    }

    @Override
    public String toString() {
        return success ? "✅ PASS" : "❌ FAIL — " + rejectionReason;
    }
}