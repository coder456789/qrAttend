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

    /** Firestore sub-collection under each teacher document for weekly timetable entries. */
    public static final String TIMETABLE = "timetable";

    // ── QR Code Settings ────────────────────────────────────────────────

    /** Interval (in milliseconds) between QR code nonce refreshes. */
    public static final int QR_REFRESH_INTERVAL_MS = 10_000; // 10 seconds

    /**
     * Grace period (ms) for the PREVIOUS nonce after a new one is generated.
     * When the QR refreshes, the old nonce stays valid in Firestore for this
     * duration so students whose GPS collection (up to 14s) causes a delay
     * can still validate the nonce they originally scanned.
     */
    public static final long PREVIOUS_NONCE_GRACE_MS = 20_000L; // 20 seconds

    /** Default QR code bitmap size in pixels (width = height). */
    public static final int QR_SIZE = 800;

    // ── Geofence Defaults ───────────────────────────────────────────────

    /**
     * Default geofence radius in meters around the classroom.
     * Raised from 20m → 100m: indoor GPS accuracy is typically 20–100m,
     * so a 20m fence caused false rejections even when students were physically present.
     * 100m covers a standard classroom building and absorbs GPS drift on both devices.
     */
    public static final double DEFAULT_GEOFENCE_RADIUS = 100.0;

    // ── Location Quality Thresholds ─────────────────────────────────────

    /**
     * Maximum GPS accuracy (meters) to accept for validation.
     * Fixes worse than this are rejected outright — a ±200m fix is too inaccurate
     * for meaningful geofence checks.
     */
    public static final float MAX_ACCEPTABLE_ACCURACY = 200f;

    /**
     * Maximum accuracy buffer (meters) added on top of the geofence radius.
     * Caps the GPS-error compensation so the effective geofence never exceeds
     * DEFAULT_GEOFENCE_RADIUS + MAX_ACCURACY_BUFFER (e.g. 100 + 50 = 150m).
     */
    public static final float MAX_ACCURACY_BUFFER = 50f;

    /**
     * GPS accuracy (meters) considered "excellent" — if a fix this good arrives,
     * LocationHelper can stop collecting samples early.
     */
    public static final float EXCELLENT_ACCURACY_THRESHOLD = 20f;

    /**
     * Minimum acceptable GPS accuracy for the TEACHER's location when starting
     * a session. A poor teacher anchor point causes all students to fail validation.
     */
    public static final float TEACHER_MAX_ACCEPTABLE_ACCURACY = 100f;

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