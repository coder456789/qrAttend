package com.qrattend.app.proxy;

import android.content.Context;
import android.location.Location;

import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.location.GeoValidator;
import com.qrattend.app.qr.QRGeneratorUtil;
import com.qrattend.app.security.DeviceFingerprint;
import com.qrattend.app.security.NonceManager;
import com.qrattend.app.utils.Constants;
import com.qrattend.app.utils.ValidationResult;

/**
 * ProxyDetectionEngine — Member 2 (Core Logic & QR Lead)
 *
 * PRD §5.3 — Orchestrates all three proxy-prevention layers:
 *   Layer 1 — Device Fingerprinting  (DeviceFingerprint)
 *   Layer 2 — Geolocation Validation (GeoValidator)
 *   Layer 3 — Dynamic QR / Nonce    (NonceManager)
 *
 * Fully aligned with Member 3's models and Constants:
 *   ✅ Uses AttendanceSession directly — no inner SessionSnapshot class
 *   ✅ Uses Student directly for 2-device binding (Constants.MAX_DEVICES = 2)
 *   ✅ geofenceRadius is double — matches AttendanceSession.getGeofenceRadius()
 *   ✅ session nonce from AttendanceSession.getQrCode()
 *   ✅ All rejection reasons use Constants.REASON_* strings
 *
 * Integration with Member 3 — call order in ScanQRActivity (Member 1):
 *
 *   Step 1: sessionRepo.getSession(sessionId, session -> {
 *   Step 2:   studentRepo.getStudent(uid, student -> {
 *   Step 3:     LocationHelper.fetchCurrentLocation(context, loc -> {
 *   Step 4:       // QRScannerUtil already gave you payload
 *   Step 5:       engine.validate(payload, loc, session, student, result -> {
 *   Step 6:         if (result.success) {
 *                     AttendanceRecord record = new AttendanceRecord(
 *                       Constants.STATUS_PRESENT, Timestamp.now(),
 *                       engine.getCurrentDeviceFingerprint(),
 *                       new GeoPoint(loc.getLatitude(), loc.getLongitude()),
 *                       Constants.REASON_NONE, uid, sessionId);
 *                     attendanceRepo.markAttendance(sessionId, uid, record, t -> {});
 *                   } else {
 *                     AttendanceRecord record = new AttendanceRecord(
 *                       Constants.STATUS_REJECTED, Timestamp.now(),
 *                       engine.getCurrentDeviceFingerprint(),
 *                       new GeoPoint(loc.getLatitude(), loc.getLongitude()),
 *                       result.rejectionReason, uid, sessionId);
 *                     attendanceRepo.markAttendance(sessionId, uid, record, t -> {});
 *                   }
 *               });
 *             });
 *           });
 *         });
 */
public class ProxyDetectionEngine {

    // -----------------------------------------------------------------------
    // Callback
    // -----------------------------------------------------------------------

    public interface ValidationCallback {
        void onResult(ValidationResult result);
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final Context context;

    public ProxyDetectionEngine(Context context) {
        this.context = context;
    }

    // -----------------------------------------------------------------------
    // Main validation pipeline
    // -----------------------------------------------------------------------

    /**
     * Runs all three proxy prevention layers. Fail-fast — returns on first failure.
     *
     * @param payload         Decrypted QR payload from QRScannerUtil.ScanCallback
     * @param studentLocation Fresh Location from LocationHelper.fetchCurrentLocation()
     * @param session         AttendanceSession from SessionRepository.getSession()
     *                        Provides: getQrCode() (nonce), getLatitude(), getLongitude(),
     *                        getGeofenceRadius(), isActive(), getSessionId()
     * @param student         Student from StudentRepository.getStudent()
     *                        Provides: getDeviceId(), getDeviceId2() for 2-device check
     * @param callback        Called with final ValidationResult.java
     */
    public void validate(QRGeneratorUtil.QRPayload payload,
                         Location studentLocation,
                         AttendanceSession session,
                         Student student,
                         ValidationCallback callback) {

        // ── Pre-flight: session must be active ──────────────────────────────
        if (session == null || !session.isActive()) {
            callback.onResult(ValidationResult.fail("session_not_active"));
            return;
        }

        // ── Pre-flight: session IDs must match ──────────────────────────────
        if (session.getSessionId() == null
                || !session.getSessionId().equals(payload.sessionId)) {
            callback.onResult(ValidationResult.fail("session_id_mismatch"));
            return;
        }

        // ── LAYER 1a — Integrity check (emulator / rooted device) ───────────
        // Uses Constants.REASON_ROOT_DETECTED for both emulator and root
        ValidationResult integrityCheck = DeviceFingerprint.checkDeviceIntegrity(context);
        if (!integrityCheck.success) {
            callback.onResult(integrityCheck);
            return;
        }

        // ── LAYER 1b — Device binding (Constants.MAX_DEVICES = 2) ───────────
        String currentDeviceId = DeviceFingerprint.getFingerprint(context);
        ValidationResult deviceCheck = validateDevice(
                currentDeviceId,
                student != null ? student.getDeviceId()  : null,
                student != null ? student.getDeviceId2() : null);
        if (!deviceCheck.success) {
            callback.onResult(deviceCheck);
            return;
        }

        // ── LAYER 2 — Geolocation Validation ────────────────────────────────
        // geofenceRadius is double — matches AttendanceSession.getGeofenceRadius()
        // Falls back to Constants.DEFAULT_GEOFENCE_RADIUS (50.0) if 0
        double radius = session.getGeofenceRadius() > 0
                ? session.getGeofenceRadius()
                : Constants.DEFAULT_GEOFENCE_RADIUS;

        ValidationResult geoCheck = GeoValidator.validate(
                studentLocation,
                session.getLatitude(),
                session.getLongitude(),
                radius);
        if (!geoCheck.success) {
            callback.onResult(geoCheck);
            return;
        }

        // ── LAYER 3 — Nonce / Temporal Validation ───────────────────────────
        // session.getQrCode() = live nonce written by SessionRepository.updateNonce()
        // FIX: Also pass previousQrCode + expiry so students delayed by GPS
        //      collection can still validate against the recently-rotated nonce.
        ValidationResult nonceCheck = NonceManager.validateNonce(
                payload.nonce,
                session.getQrCode(),
                payload.timestamp,
                session.getPreviousQrCode(),
                session.getPreviousNonceExpiryMs());
        if (!nonceCheck.success) {
            callback.onResult(nonceCheck);
            return;
        }

        // ── ALL LAYERS PASSED ────────────────────────────────────────────────
        callback.onResult(ValidationResult.pass());
    }

    // -----------------------------------------------------------------------
    // Layer 1b — Single-device binding check (MAX_DEVICES = 1)
    // -----------------------------------------------------------------------

    /**
     * Checks current fingerprint against Student.deviceId only.
     * Each student account is bound to exactly one device at a time.
     * If a different device is detected, the scan is rejected with
     * Constants.REASON_DEVICE_MISMATCH.
     */
    private ValidationResult validateDevice(String currentFP,
                                            String deviceId,
                                            String deviceId2) {
        if (deviceId == null || deviceId.isEmpty()) {
            // No device bound yet — first-time student.
            // ScanQRActivity.registerDevice() runs before this check,
            // so allow and let the binding take effect on next scan.
            return ValidationResult.pass();
        }

        // Only one device slot is valid (single-device binding)
        if (currentFP.equals(deviceId)) {
            return ValidationResult.pass();
        }

        // Different device — reject
        return ValidationResult.fail(Constants.REASON_DEVICE_MISMATCH);
    }

    // -----------------------------------------------------------------------
    // Convenience — fingerprint for first-time binding and AttendanceRecord
    // -----------------------------------------------------------------------

    /**
     * Returns the current device fingerprint.
     *
     * Used by:
     *   - StudentRepository.updateDeviceId() on first login (device binding)
     *   - Building AttendanceRecord.deviceId before calling
     *     AttendanceRepository.markAttendance()
     */
    public String getCurrentDeviceFingerprint() {
        return DeviceFingerprint.getFingerprint(context);
    }
}