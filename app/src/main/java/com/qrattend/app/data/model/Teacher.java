package com.qrattend.app.data.model;

/**
 * Represents a teacher user in the QR-Attend system.
 * <p>
 * Firestore Collection: {@code teachers}
 * <br>
 * Document ID: Auto-generated or user UID
 * </p>
 *
 * <p>Teachers create attendance sessions and monitor student check-ins.
 * Each document stores basic profile info and an FCM token for notifications.</p>
 */
public class Teacher {

    /** Teacher's full name. */
    private String name;

    /** Email address. */
    private String email;

    /** Primary subject taught. */
    private String subject;

    /** Default classroom assignment (e.g. "Room 301"). */
    private String classroom;

    /** Firebase Cloud Messaging token for push notifications. */
    private String fcmToken;

    /**
     * Empty constructor required by Firestore for deserialization.
     */
    public Teacher() {
    }

    /**
     * Full constructor for creating new Teacher instances.
     *
     * @param name      teacher's full name
     * @param email     email address
     * @param subject   primary subject taught
     * @param classroom default classroom assignment
     * @param fcmToken  Firebase Cloud Messaging token
     */
    public Teacher(String name, String email, String subject,
                   String classroom, String fcmToken) {
        this.name = name;
        this.email = email;
        this.subject = subject;
        this.classroom = classroom;
        this.fcmToken = fcmToken;
    }

    // ── Getters and Setters ─────────────────────────────────────────────

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getClassroom() {
        return classroom;
    }

    public void setClassroom(String classroom) {
        this.classroom = classroom;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    // ── Debug ───────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Teacher{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", subject='" + subject + '\'' +
                ", classroom='" + classroom + '\'' +
                ", fcmToken='" + fcmToken + '\'' +
                '}';
    }
}
