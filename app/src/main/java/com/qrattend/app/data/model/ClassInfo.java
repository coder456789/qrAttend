package com.qrattend.app.data.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a class/batch in the QR-Attend system.
 * <p>
 * Firestore Collection: {@code classes}
 * <br>
 * Document ID: Auto-generated (e.g. {@code "class_001"})
 * </p>
 *
 * <p>Each document links a named class to a teacher and maintains
 * a list of enrolled student document IDs.</p>
 */
public class ClassInfo {

    /** Name of the class or batch (e.g. "TY BSc CS"). */
    private String className;

    /** Subject name (e.g. "DSA"). */
    private String subject;

    /** Reference to the teacher's document ID in the {@code teachers} collection. */
    private String teacherId;

    /**
     * List of enrolled student document IDs.
     * <p>Initialized to an empty list in the empty constructor to avoid
     * {@code NullPointerException} issues.</p>
     */
    private List<String> enrolledStudents;

    /** 6-character alphanumeric join code students enter to enroll. */
    private String joinCode;

    /**
     * Empty constructor required by Firestore for deserialization.
     * <p>Initializes {@code enrolledStudents} to an empty {@link ArrayList}
     * to prevent null-related issues.</p>
     */
    public ClassInfo() {
        this.enrolledStudents = new ArrayList<>();
    }

    /**
     * Full constructor for creating new ClassInfo instances.
     *
     * @param className        name of the class or batch
     * @param subject          subject name
     * @param teacherId        reference to the teacher's document ID
     * @param enrolledStudents list of enrolled student document IDs
     */
    public ClassInfo(String className, String subject, String teacherId,
                     List<String> enrolledStudents) {
        this.className = className;
        this.subject = subject;
        this.teacherId = teacherId;
        this.enrolledStudents = enrolledStudents != null
                ? enrolledStudents
                : new ArrayList<>();
    }

    // ── Getters and Setters ─────────────────────────────────────────────

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public List<String> getEnrolledStudents() {
        return enrolledStudents;
    }

    public void setEnrolledStudents(List<String> enrolledStudents) {
        this.enrolledStudents = enrolledStudents;
    }

    public String getJoinCode() {
        return joinCode;
    }

    public void setJoinCode(String joinCode) {
        this.joinCode = joinCode;
    }

    // ── Debug ───────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "ClassInfo{" +
                "className='" + className + '\'' +
                ", subject='" + subject + '\'' +
                ", teacherId='" + teacherId + '\'' +
                ", enrolledStudents=" + enrolledStudents +
                '}';
    }
}
