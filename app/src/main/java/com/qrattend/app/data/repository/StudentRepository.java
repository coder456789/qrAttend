package com.qrattend.app.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.utils.Constants;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for CRUD operations on the {@code students} Firestore collection.
 */
public class StudentRepository {

    private final CollectionReference studentsRef;

    public StudentRepository() {
        this.studentsRef = FirebaseFirestore.getInstance()
                .collection(Constants.STUDENTS);
    }

    // ── Create ──────────────────────────────────────────────────────────

    public void addStudent(@NonNull String docId, @NonNull Student student,
                           @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(docId)
                .set(student)
                .addOnCompleteListener(callback);
    }

    // ── Read ────────────────────────────────────────────────────────────

    public void getStudent(@NonNull String studentId,
                           @NonNull OnSuccessListener<Student> callback) {
        studentsRef.document(studentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    callback.onSuccess(snapshot.toObject(Student.class));
                });
    }

    public void getAllStudents(@NonNull OnSuccessListener<List<Student>> callback) {
        studentsRef.get()
                .addOnSuccessListener(querySnapshot -> {
                    callback.onSuccess(querySnapshot.toObjects(Student.class));
                });
    }

    // ── Update ──────────────────────────────────────────────────────────

    public void updateStudent(@NonNull String studentId,
                              @NonNull Map<String, Object> updates,
                              @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .update(updates)
                .addOnCompleteListener(callback);
    }

    /**
     * Updates the primary device fingerprint for a student.
     */
    public void updateDeviceId(@NonNull String studentId,
                               @NonNull String newDeviceId,
                               @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .update("deviceId", newDeviceId)
                .addOnCompleteListener(callback);
    }

    /**
     * Updates the FCM registration token for push notifications.
     * Essential for ensuring notifications are delivered to the correct device.
     */
    public void updateFcmToken(@NonNull String studentId,
                               @NonNull String fcmToken,
                               @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .update("fcmToken", fcmToken)
                .addOnCompleteListener(callback);
    }

    /**
     * Registers a new device for the student, supporting up to Constants.MAX_DEVICES.
     * This is a safer way to handle device registration than just overwriting 'deviceId'.
     */
    public void registerDevice(@NonNull String studentId,
                               @NonNull String newDeviceId,
                               @NonNull OnCompleteListener<Void> callback) {
        getStudent(studentId, student -> {
            if (student == null) return;

            Map<String, Object> updates = new HashMap<>();
            // If primary is empty or same, set primary
            if (student.getDeviceId() == null || student.getDeviceId().isEmpty() || student.getDeviceId().equals(newDeviceId)) {
                updates.put("deviceId", newDeviceId);
            } 
            // Otherwise, if secondary is empty or same, set secondary
            else if (student.getDeviceId2() == null || student.getDeviceId2().isEmpty() || student.getDeviceId2().equals(newDeviceId)) {
                updates.put("deviceId2", newDeviceId);
            }
            
            if (!updates.isEmpty()) {
                updateStudent(studentId, updates, callback);
            }
        });
    }

    // ── Delete ──────────────────────────────────────────────────────────

    public void deleteStudent(@NonNull String studentId,
                              @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .delete()
                .addOnCompleteListener(callback);
    }
}