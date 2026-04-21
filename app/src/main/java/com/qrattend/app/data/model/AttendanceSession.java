package com.qrattend.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

/**
 * Represents an active or completed attendance session in the QR-Attend system.
 * <p>
 * Firestore Collection: {@code attendanceSessions}
 * <br>
 * Document ID: Readable format (e.g. {@code "session_2026_03_23_10AM"})
 * </p>
 */
public class AttendanceSession {

    /**
     * Automatically populated with the Firestore Document ID.
     * Useful for passing the ID to the next Activity.
     */
    @DocumentId
    private String sessionId;

    /** Reference to the {@code classes} document ID. */
    private String classId;

    /** Denormalized class name for faster UI rendering (e.g. "TY BSc CS"). */
    private String className;

    /** Denormalized subject name (e.g. "DSA"). */
    private String subject;

    /** Reference to the {@code teachers} document ID. */
    private String teacherId;

    /** Current QR nonce/token, rotated every ~10 seconds while the session is active. */
    private String qrCode;

    /** Classroom GPS latitude. */
    private double latitude;

    /** Classroom GPS longitude. */
    private double longitude;

    /** Geofence radius in meters around the classroom coordinates. */
    private double geofenceRadius;

    /** Timestamp when the session was started. */
    private Timestamp startTime;

    /** AES-256 session key used to encrypt/decrypt QR payloads. */
    private String sessionKey;

    /**
     * Session duration in minutes, stored so expiry can be computed even if the app crashes.
     * FIX: was missing from constructor — defaulted to 0, making isExpired() always return false.
     */
    private int durationMinutes;

    /** Timestamp when the session ended; {@code null} while the session is active. */
    private Timestamp endTime;

    /** Whether the session is currently accepting attendance check-ins. */
    private boolean active;

    /**
     * Empty constructor required by Firestore for deserialization.
     */
    public AttendanceSession() {
    }

    /**
     * Full constructor for creating new AttendanceSession instances.
     *
     * FIX: Added {@code durationMinutes} parameter — it was previously missing,
     * causing the field to always be 0 and {@link #isExpired()} to always
     * return {@code false}, so stale sessions were never auto-expired.
     */
    public AttendanceSession(String classId, String className, String subject, String teacherId,
                             String qrCode, String sessionKey, double latitude, double longitude,
                             double geofenceRadius, Timestamp startTime, int durationMinutes,
                             Timestamp endTime, boolean active) {
        this.classId         = classId;
        this.className       = className;
        this.subject         = subject;
        this.teacherId       = teacherId;
        this.qrCode          = qrCode;
        this.sessionKey      = sessionKey;
        this.latitude        = latitude;
        this.longitude       = longitude;
        this.geofenceRadius  = geofenceRadius;
        this.startTime       = startTime;
        this.durationMinutes = durationMinutes; // FIX: was never assigned before
        this.endTime         = endTime;
        this.active          = active;
    }

    // ── Getters and Setters ─────────────────────────────────────────────

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public String getClassId() { return classId; }
    public void setClassId(String classId) { this.classId = classId; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String teacherId) { this.teacherId = teacherId; }

    public String getQrCode() { return qrCode; }
    public void setQrCode(String qrCode) { this.qrCode = qrCode; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getGeofenceRadius() { return geofenceRadius; }
    public void setGeofenceRadius(double geofenceRadius) { this.geofenceRadius = geofenceRadius; }

    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /**
     * Returns true if the session has exceeded its duration.
     * Used by the student side to skip/auto-deactivate stale sessions
     * when the teacher's app crashed without calling endSession().
     */
    public boolean isExpired() {
        if (startTime == null || durationMinutes <= 0) return false;
        long startMs = startTime.toDate().getTime();
        long endMs   = startMs + (durationMinutes * 60 * 1000L);
        return System.currentTimeMillis() > endMs;
    }

    @Override
    public String toString() {
        return "AttendanceSession{" +
                "sessionId='" + sessionId + '\'' +
                ", subject='" + subject + '\'' +
                ", active=" + active +
                '}';
    }
}