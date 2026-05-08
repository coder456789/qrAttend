package com.qrattend.app.data.model;

import com.google.firebase.Timestamp;

/**
 * Represents a student leave application.
 *
 * Firestore path: {@code leaveApplications/{applicationId}}
 */
public class LeaveApplication {

    /** Firestore document ID — set transient after fetch. */
    private String applicationId;

    /** UID of the student who submitted the application. */
    private String studentId;

    /** Student's display name (denormalised for easy teacher view). */
    private String studentName;

    /** Student roll number. */
    private String studentRollNo;

    /** The leave reason / body text written by the student. */
    private String reason;

    /** Firebase Storage download URL of the attached proof document (may be null). */
    private String attachmentUrl;

    /** MIME type of the attachment (e.g. "application/pdf", "image/jpeg"). */
    private String attachmentMimeType;

    /** Original file name of the attachment. */
    private String attachmentFileName;

    /** When the application was submitted. */
    private Timestamp submittedAt;

    /** Status: "Pending" | "Approved" | "Rejected" */
    private String status;

    // ── Required no-arg constructor for Firestore ────────────────────────
    public LeaveApplication() {
        this.status = "Pending";
    }

    // ── Getters & Setters ────────────────────────────────────────────────

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }

    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentRollNo() { return studentRollNo; }
    public void setStudentRollNo(String studentRollNo) { this.studentRollNo = studentRollNo; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getAttachmentMimeType() { return attachmentMimeType; }
    public void setAttachmentMimeType(String attachmentMimeType) { this.attachmentMimeType = attachmentMimeType; }

    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }

    public Timestamp getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Timestamp submittedAt) { this.submittedAt = submittedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
