package com.qrattend.app.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.qrattend.app.data.model.Teacher;
import com.qrattend.app.utils.Constants;

import java.util.List;
import java.util.Map;

/**
 * Repository for CRUD operations on the {@code teachers} Firestore collection.
 * <p>
 * Mirrors the {@link StudentRepository} pattern — standardised create, read,
 * update, and delete methods with asynchronous Firestore callbacks.
 * </p>
 *
 * @author QR-Attend Team — Member 3 (Backend & Integration Lead)
 * @version 1.0
 * @since 2026-03-27
 */
public class TeacherRepository {

    /** Reference to the {@code teachers} Firestore collection. */
    private final CollectionReference teachersRef;

    /**
     * Constructs a new TeacherRepository using the default Firestore instance.
     */
    public TeacherRepository() {
        this.teachersRef = FirebaseFirestore.getInstance()
                .collection(Constants.TEACHERS);
    }

    // ── Create ──────────────────────────────────────────────────────────

    /**
     * Adds a new teacher document with the given document ID.
     *
     * @param docId    the Firestore document ID (usually the Auth UID)
     * @param teacher  the {@link Teacher} object to persist
     * @param callback completion listener
     */
    public void addTeacher(@NonNull String docId, @NonNull Teacher teacher,
                           @NonNull OnCompleteListener<Void> callback) {
        teachersRef.document(docId)
                .set(teacher)
                .addOnCompleteListener(callback);
    }

    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Retrieves a single teacher document by ID.
     *
     * @param teacherId the document ID to look up
     * @param callback  success listener receiving the deserialized Teacher
     */
    public void getTeacher(@NonNull String teacherId,
                           @NonNull OnSuccessListener<Teacher> callback) {
        teachersRef.document(teacherId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Returns null automatically if snapshot doesn't exist
                    callback.onSuccess(snapshot.toObject(Teacher.class));
                });
    }

    /**
     * Retrieves all teacher documents in the collection.
     *
     * @param callback success listener receiving a list of {@link Teacher} objects
     */
    public void getAllTeachers(@NonNull OnSuccessListener<List<Teacher>> callback) {
        teachersRef.get()
                .addOnSuccessListener(querySnapshot -> {
                    // Simplified: toObjects handles the loop and list creation internally
                    callback.onSuccess(querySnapshot.toObjects(Teacher.class));
                });
    }

    // ── Update ──────────────────────────────────────────────────────────

    /**
     * Applies partial updates to a teacher document.
     *
     * @param teacherId the document ID to update
     * @param updates   map of field names to new values
     * @param callback  completion listener
     */
    public void updateTeacher(@NonNull String teacherId,
                              @NonNull Map<String, Object> updates,
                              @NonNull OnCompleteListener<Void> callback) {
        teachersRef.document(teacherId)
                .update(updates)
                .addOnCompleteListener(callback);
    }

    // ── Delete ──────────────────────────────────────────────────────────

    /**
     * Deletes a teacher document from Firestore.
     *
     * @param teacherId the document ID to delete
     * @param callback  completion listener
     */
    public void deleteTeacher(@NonNull String teacherId,
                              @NonNull OnCompleteListener<Void> callback) {
        teachersRef.document(teacherId)
                .delete()
                .addOnCompleteListener(callback);
    }
}