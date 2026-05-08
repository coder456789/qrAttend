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

    /**
     * Looks up a student by PRN / roll number.
     * Returns an {@link android.util.Pair} of (docId, Student), or {@code null} if not found.
     */
    public void getStudentByRollNo(@NonNull String rollNo,
                                   @NonNull OnSuccessListener<android.util.Pair<String, Student>> callback) {
        studentsRef.whereEqualTo("rollNo", rollNo)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc =
                                querySnapshot.getDocuments().get(0);
                        Student s = doc.toObject(Student.class);
                        callback.onSuccess(new android.util.Pair<>(doc.getId(), s));
                    } else {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> callback.onSuccess(null));
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
     * Registers the device fingerprint for a student (single-device binding).
     * <p>
     * Rules (MAX_DEVICES = 1):
     * <ul>
     *   <li>If no device is bound yet, store the fingerprint as {@code deviceId}.</li>
     *   <li>If the same device is already bound, this is a no-op (idempotent).</li>
     *   <li>If a DIFFERENT device is already bound, do NOT overwrite — the scan
     *       will be rejected at the validation layer (device_mismatch).</li>
     * </ul>
     * Always fires the callback so the scan flow can proceed to validation.
     */
    public void registerDevice(@NonNull String studentId,
                               @NonNull String newDeviceId,
                               @NonNull OnCompleteListener<Void> callback) {
        getStudent(studentId, student -> {
            if (student == null) {
                // Student doc doesn't exist yet — write deviceId directly
                Map<String, Object> updates = new HashMap<>();
                updates.put("deviceId", newDeviceId);
                updateStudent(studentId, updates, callback);
                return;
            }

            String currentDeviceId = student.getDeviceId();
            if (currentDeviceId == null || currentDeviceId.isEmpty()
                    || currentDeviceId.equals(newDeviceId)) {
                // No device bound yet, or same device — bind / confirm
                Map<String, Object> updates = new HashMap<>();
                updates.put("deviceId", newDeviceId);
                updateStudent(studentId, updates, callback);
            } else {
                // A DIFFERENT device is already bound — do not overwrite.
                // The validation layer will issue a device_mismatch rejection.
                callback.onComplete(com.google.android.gms.tasks.Tasks.forResult(null));
            }
        });
    }

    /**
     * Student self-service device unbind.
     * Clears {@code deviceId} and {@code deviceId2} using {@code FieldValue.delete()}.
     * <p>
     * NOTE: The 30-day rate-limit timestamp ({@code lastUnbindDate}) is stored in
     * SharedPreferences by {@code SettingsActivity}, NOT in Firestore, so this
     * update only touches fields already covered by the deployed security rule:
     * {@code ['deviceId', 'deviceId2', 'fcmToken']}.
     * </p>
     */
    public void unbindDevice(@NonNull String studentId,
                             @NonNull OnCompleteListener<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("deviceId",  com.google.firebase.firestore.FieldValue.delete());
        updates.put("deviceId2", com.google.firebase.firestore.FieldValue.delete());

        studentsRef.document(studentId)
                .update(updates)
                .addOnCompleteListener(callback);
    }

    /**
     * Teacher-initiated device unbind — same as above but callable without the
     * student self-service rate-limit check.
     */
    public void unbindDeviceByTeacher(@NonNull String studentId,
                                      @NonNull OnCompleteListener<Void> callback) {
        // Reuses the same logic; teachers bypass the rate limit in SettingsActivity.
        unbindDevice(studentId, callback);
    }


    // ── Delete ──────────────────────────────────────────────────────────

    public void deleteStudent(@NonNull String studentId,
                              @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .delete()
                .addOnCompleteListener(callback);
    }
}