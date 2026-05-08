package com.qrattend.app.data.model;

import com.google.firebase.Timestamp;

/**
 * Represents a student leave application.
 * Firestore path: {@code leaveApplications/{applicationId}}
 */
public class LeaveApplication {

    private String applicationId;
    private String studentId;
    private String studentName;
    private String studentRollNo;
    private String reason;
    private String attachmentUrl;
    private String attachmentMimeType;
    /** Original file name of the attachment. */
    private String attachmentFileName;
    /** Base64-encoded file content (fallback when Firebase Storage is unavailable). */
    private String attachmentBase64;
    /** MIME type for the base64 fallback content. */
    private String attachmentBase64MimeType;
    private Timestamp submittedAt;
    private String status;

    // ── Class / teacher / date (new) ─────────────────────────────────────
    /** Firestore document ID of the class this leave is for. */
    private String classId;
    /** Human-readable class name (e.g. "TY BSc CS"). */
    private String className;
    /** Firestore UID of the teacher this leave is addressed to. */
    private String teacherId;
    /** Teacher display name (denormalised). */
    private String teacherName;
    /** Subject name for the leave (e.g. "DSA"). */
    private String subject;
    /** ISO-8601 date string for the leave date (e.g. "2026-05-10"). */
    private String leaveDate;

    /** Required no-arg constructor for Firestore. */
    public LeaveApplication() {
        this.status = "Pending";
    }

    // ── Getters & Setters ────────────────────────────────────────────────

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String v) { this.applicationId = v; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String v) { this.studentId = v; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String v) { this.studentName = v; }

    public String getStudentRollNo() { return studentRollNo; }
    public void setStudentRollNo(String v) { this.studentRollNo = v; }

    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String v) { this.attachmentUrl = v; }

    public String getAttachmentMimeType() { return attachmentMimeType; }
    public void setAttachmentMimeType(String v) { this.attachmentMimeType = v; }

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String v) { this.attachmentFileName = v; }

    public String getAttachmentBase64() { return attachmentBase64; }
    public void setAttachmentBase64(String v) { this.attachmentBase64 = v; }

    public String getAttachmentBase64MimeType() { return attachmentBase64MimeType; }
    public void setAttachmentBase64MimeType(String v) { this.attachmentBase64MimeType = v; }

    /** Returns true if this application has any viewable attachment (URL or Base64). */
    public boolean hasAttachment() {
        return (attachmentUrl != null && !attachmentUrl.isEmpty())
                || (attachmentBase64 != null && !attachmentBase64.isEmpty());
    }

    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp v) { this.submittedAt = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }

    public String getClassId() { return classId; }
    public void setClassId(String v) { this.classId = v; }

    public String getClassName() { return className; }
    public void setClassName(String v) { this.className = v; }

    public String getTeacherId() { return teacherId; }
    public void setTeacherId(String v) { this.teacherId = v; }

    public String getTeacherName() { return teacherName; }
    public void setTeacherName(String v) { this.teacherName = v; }

    public String getSubject() { return subject; }
    public void setSubject(String v) { this.subject = v; }

    public String getLeaveDate() { return leaveDate; }
    public void setLeaveDate(String v) { this.leaveDate = v; }
}
