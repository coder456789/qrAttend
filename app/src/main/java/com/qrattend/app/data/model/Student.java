package com.qrattend.app.data.model;

import com.google.firebase.firestore.PropertyName;

/**
 * Represents a student user in the QR-Attend system.
 * <p>
 * Firestore Collection: {@code students}
 * <br>
 * Document ID: Auto-generated or user UID
 * </p>
 *
 * <p>Each student document stores personal info, device fingerprints for
 * proxy-prevention, and an FCM token for push notifications.</p>
 */
public class Student {

    /** Student's full name. */
    private String name;

    /** Roll number / student ID. */
    private String rollNo;

    /**
     * Class or batch name.
     * <p>Maps to the Firestore field {@code "class"} via {@link PropertyName}
     * because "class" is a reserved word in Java.</p>
     *
     * FIX: Added @PropertyName("class") to the field itself. Without this,
     * Firestore's serializer writes BOTH "class" (from getter annotation) and
     * "className" (from the field name), causing inconsistent reads where
     * getClassName() returns null after deserialization.
     */
    @PropertyName("class")
    private String className;

    /** Email address. */
    private String email;

    /** Phone number. */
    private String phone;

    /** Primary device fingerprint hash used for proxy prevention. */
    private String deviceId;

    /** Optional second device fingerprint hash (empty string if unused). */
    private String deviceId2;

    /** Firebase Cloud Messaging token for push notifications. */
    private String fcmToken;

    /**
     * ISO date (yyyy-MM-dd) of the last time this student unbound their device.
     * Null / empty means the student has never unbound. Used to enforce the
     * 30-day rate limit on device unbinding.
     */
    private String lastUnbindDate;

    /**
     * Empty constructor required by Firestore for deserialization.
     */
    public Student() {
    }

    /**
     * Full constructor for creating new Student instances.
     *
     * @param name      student's full name
     * @param rollNo    roll number / student ID
     * @param className class or batch name
     * @param email     email address
     * @param phone     phone number
     * @param deviceId  primary device fingerprint hash
     * @param deviceId2 optional second device fingerprint (empty string if unused)
     * @param fcmToken  Firebase Cloud Messaging token
     */
    public Student(String name, String rollNo, String className, String email,
                   String phone, String deviceId, String deviceId2, String fcmToken) {
        this.name      = name;
        this.rollNo    = rollNo;
        this.className = className;
        this.email     = email;
        this.phone     = phone;
        this.deviceId  = deviceId;
        this.deviceId2 = deviceId2;
        this.fcmToken  = fcmToken;
    }

    // ── Getters and Setters ─────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }

    /** Transient field — holds the Firestore document ID (Firebase UID). Not stored in Firestore. */
    private String studentId;
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }

    /**
     * Returns the class/batch name.
     * <p>Annotated with {@code @PropertyName("class")} so Firestore reads/writes
     * the field as {@code "class"} rather than {@code "className"}.</p>
     */
    @PropertyName("class")
    public String getClassName() { return className; }

    /**
     * Sets the class/batch name.
     */
    @PropertyName("class")
    public void setClassName(String className) { this.className = className; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getDeviceId2() { return deviceId2; }
    public void setDeviceId2(String deviceId2) { this.deviceId2 = deviceId2; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public String getLastUnbindDate() { return lastUnbindDate; }
    public void setLastUnbindDate(String lastUnbindDate) { this.lastUnbindDate = lastUnbindDate; }

    // ── Debug ───────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "Student{" +
                "name='" + name + '\'' +
                ", rollNo='" + rollNo + '\'' +
                ", className='" + className + '\'' +
                ", email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", deviceId2='" + deviceId2 + '\'' +
                ", fcmToken='" + fcmToken + '\'' +
                '}';
    }
}