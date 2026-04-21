package com.qrattend.app.utils;

/**
 * Application-wide constants for the QR-Attend system.
 * <p>
 * Centralizes Firestore collection names, QR configuration values,
 * geofence defaults, device limits, and SharedPreferences keys so
 * that magic strings and numbers are never scattered across the codebase.
 * </p>
 *
 * @author QR-Attend Team — Shared
 * @version 1.0
 * @since 2026-03-27
 */
public final class Constants {

    /** Suppress instantiation. */
    private Constants() {
        throw new UnsupportedOperationException("Constants class — do not instantiate.");
    }

    // ── Firestore Collection Names ──────────────────────────────────────

    /** Firestore collection for student documents. */
    public static final String STUDENTS = "students";

    /** Firestore collection for teacher documents. */
    public static final String TEACHERS = "teachers";

    /** Firestore collection for class/batch documents. */
    public static final String CLASSES = "classes";

    /** Firestore collection for attendance session documents. */
    public static final String SESSIONS = "attendanceSessions";

    /** Firestore subcollection name under each session for attendance records. */
    public static final String RECORDS = "records";

    // ── QR Code Settings ────────────────────────────────────────────────

    /** Interval (in milliseconds) between QR code nonce refreshes. */
    public static final int QR_REFRESH_INTERVAL_MS = 10_000; // 10 seconds

    /** Default QR code bitmap size in pixels (width = height). */
    public static final int QR_SIZE = 800;

    // ── Geofence Defaults ───────────────────────────────────────────────

    /** Default geofence radius in meters around the classroom. */
    public static final double DEFAULT_GEOFENCE_RADIUS = 50.0;

    // ── Device ──────────────────────────────────────────────────────────

    /** Maximum number of registered devices per student account. */
    public static final int MAX_DEVICES = 2;

    // ── SharedPreferences Keys ──────────────────────────────────────────

    /** Preference key for persisting the current user's role. */
    public static final String PREF_USER_ROLE = "user_role";

    /** Preference key for persisting the current user's Firestore document ID. */
    public static final String PREF_USER_ID = "user_id";

    // ── User Roles ──────────────────────────────────────────────────────

    /** Role value for students. */
    public static final String ROLE_STUDENT = "student";

    /** Role value for teachers. */
    public static final String ROLE_TEACHER = "teacher";

    /** Role value for administrators. */
    public static final String ROLE_ADMIN = "admin";

    // ── Attendance Status Values ────────────────────────────────────────

    /** Status value when a student's attendance is accepted. */
    public static final String STATUS_PRESENT = "present";

    /** Status value when a student's attendance is rejected. */
    public static final String STATUS_REJECTED = "rejected";

    // ── Rejection Reason Codes ──────────────────────────────────────────

    /** Empty string — no rejection (attendance accepted). */
    public static final String REASON_NONE = "";

    /** Rejection: student is outside the classroom geofence. */
    public static final String REASON_LOCATION_MISMATCH = "location_mismatch";

    /** Rejection: device fingerprint does not match registered devices. */
    public static final String REASON_DEVICE_MISMATCH = "device_mismatch";

    /** Rejection: the QR nonce has expired (scanned too late). */
    public static final String REASON_NONCE_EXPIRED = "nonce_expired";

    /** Rejection: rooted or emulator device detected. */
    public static final String REASON_ROOT_DETECTED = "root_detected";
}
