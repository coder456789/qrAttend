package com.qrattend.app.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.utils.Constants;

import java.util.List;
import java.util.Map;

/**
 * Repository for CRUD operations on the {@code students} Firestore collection.
 * <p>
 * Provides methods to create, read, update, and delete student documents,
 * plus a specialised method for updating device fingerprints used by the
 * proxy-prevention layer.
 * </p>
 *
 * @author QR-Attend Team — Member 3 (Backend & Integration Lead)
 * @version 1.0
 * @since 2026-03-27
 */
public class StudentRepository {

    /** Reference to the {@code students} Firestore collection. */
    private final CollectionReference studentsRef;

    /**
     * Constructs a new StudentRepository using the default Firestore instance.
     */
    public StudentRepository() {
        this.studentsRef = FirebaseFirestore.getInstance()
                .collection(Constants.STUDENTS);
    }

    // ── Create ──────────────────────────────────────────────────────────

    /**
     * Adds a new student document with the given document ID.
     * <p>
     * Typically the document ID is the Firebase Auth UID so that the student
     * can be looked up directly from the authenticated user's identity.
     * </p>
     *
     * @param docId    the Firestore document ID (usually the Auth UID)
     * @param student  the {@link Student} object to persist
     * @param callback completion listener indicating success or failure
     */
    public void addStudent(@NonNull String docId, @NonNull Student student,
                           @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(docId)
                .set(student)
                .addOnCompleteListener(callback);
    }

    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Retrieves a single student document by ID.
     * <p>
     * The callback receives a {@link Student} if the document exists,
     * or {@code null} otherwise.
     * </p>
     *
     * @param studentId the document ID to look up
     * @param callback  success listener receiving the deserialized Student
     */
    public void getStudent(@NonNull String studentId,
                           @NonNull OnSuccessListener<Student> callback) {
        studentsRef.document(studentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Optimized: snapshot.toObject returns null if the doc doesn't exist
                    callback.onSuccess(snapshot.toObject(Student.class));
                });
    }

    /**
     * Retrieves all student documents in the collection.
     *
     * @param callback success listener receiving a list of {@link Student} objects
     */
    public void getAllStudents(@NonNull OnSuccessListener<List<Student>> callback) {
        studentsRef.get()
                .addOnSuccessListener(querySnapshot -> {
                    // Optimized: toObjects handles the loop and list creation internally
                    callback.onSuccess(querySnapshot.toObjects(Student.class));
                });
    }

    // ── Update ──────────────────────────────────────────────────────────

    /**
     * Applies partial updates to a student document.
     * <p>
     * Only the fields present in the {@code updates} map are overwritten;
     * all other fields remain untouched.
     * </p>
     *
     * @param studentId the document ID to update
     * @param updates   map of field names to new values
     * @param callback  completion listener
     */
    public void updateStudent(@NonNull String studentId,
                              @NonNull Map<String, Object> updates,
                              @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .update(updates)
                .addOnCompleteListener(callback);
    }

    /**
     * Updates the primary device fingerprint for a student.
     * <p>
     * Called when a student registers or changes their device.
     * The proxy-prevention layer uses this fingerprint to verify
     * that attendance is being marked from a trusted device.
     * </p>
     *
     * @param studentId   the student document ID
     * @param newDeviceId the new device fingerprint hash
     * @param callback    completion listener
     */
    public void updateDeviceId(@NonNull String studentId,
                               @NonNull String newDeviceId,
                               @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .update("deviceId", newDeviceId)
                .addOnCompleteListener(callback);
    }

    // ── Delete ──────────────────────────────────────────────────────────

    /**
     * Deletes a student document from Firestore.
     *
     * @param studentId the document ID to delete
     * @param callback  completion listener
     */
    public void deleteStudent(@NonNull String studentId,
                              @NonNull OnCompleteListener<Void> callback) {
        studentsRef.document(studentId)
                .delete()
                .addOnCompleteListener(callback);
    }
}