package com.qrattend.app.data.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;

/**
 * Represents a single student's attendance record within a session.
 * <p>
 * Firestore Path: {@code attendanceSessions/{sessionId}/records/{studentId}}
 * <br>
 * Document ID: The student's document ID (ensures one record per student per session)
 * </p>
 */
public class AttendanceRecord {

    private String status;
    private Timestamp time;
    private String deviceId;
    private GeoPoint location;
    private String rejectionReason;
    private String studentId;
    private String sessionId;
    private String studentName;
    private String studentRollNo;
    private String subject;

    /**
     * Empty constructor required by Firestore for deserialization.
     */
    public AttendanceRecord() {
    }

    public AttendanceRecord(String status, Timestamp time, String deviceId,
                            GeoPoint location, String rejectionReason,
                            String studentId, String sessionId) {
        this.status = status;
        this.time = time;
        this.deviceId = deviceId;
        this.location = location;
        this.rejectionReason = rejectionReason;
        this.studentId = studentId;
        this.sessionId = sessionId;
    }

    public AttendanceRecord(String status, Timestamp time, String deviceId,
                            GeoPoint location, String rejectionReason,
                            String studentId, String sessionId,
                            String studentName, String studentRollNo, String subject) {
        this.status = status;
        this.time = time;
        this.deviceId = deviceId;
        this.location = location;
        this.rejectionReason = rejectionReason;
        this.studentId = studentId;
        this.sessionId = sessionId;
        this.studentName = studentName;
        this.studentRollNo = studentRollNo;
        this.subject = subject;
    }

    // ── Getters and Setters ─────────────────────────────────────────────

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getTime() { return time; }
    public void setTime(Timestamp time) { this.time = time; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public GeoPoint getLocation() { return location; }
    public void setLocation(GeoPoint location) { this.location = location; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentRollNo() { return studentRollNo; }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    @Override
    public String toString() {
        return "AttendanceRecord{" +
                "status='" + status + '\'' +
                ", time=" + time +
                ", deviceId='" + deviceId + '\'' +
                ", location=" + location +
                ", rejectionReason='" + rejectionReason + '\'' +
                ", studentId='" + studentId + '\'' +
                ", sessionId='" + sessionId + '\'' +
                ", studentName='" + studentName + '\'' +
                ", studentRollNo='" + studentRollNo + '\'' +
                ", subject='" + subject + '\'' +
                '}';
    }
}