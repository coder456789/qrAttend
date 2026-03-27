package com.qrattend.app.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.qrattend.app.data.model.ClassInfo;
import com.qrattend.app.utils.Constants;

import java.util.List;
import java.util.Map;

/**
 * Repository for CRUD operations on the {@code classes} Firestore collection.
 * <p>
 * In addition to standard CRUD, provides teacher-specific queries and
 * methods to enroll/un-enroll students in a class.
 * </p>
 *
 * @author QR-Attend Team — Member 3 (Backend & Integration Lead)
 * @version 1.0
 * @since 2026-03-27
 */
public class ClassRepository {

    /** Reference to the {@code classes} Firestore collection. */
    private final CollectionReference classesRef;

    /**
     * Constructs a new ClassRepository using the default Firestore instance.
     */
    public ClassRepository() {
        this.classesRef = FirebaseFirestore.getInstance()
                .collection(Constants.CLASSES);
    }

    // ── Create ──────────────────────────────────────────────────────────

    /**
     * Creates a new class document with the given ID.
     *
     * @param docId     the Firestore document ID (e.g. {@code "class_001"})
     * @param classInfo the {@link ClassInfo} object to persist
     * @param callback  completion listener
     */
    public void addClass(@NonNull String docId, @NonNull ClassInfo classInfo,
                         @NonNull OnCompleteListener<Void> callback) {
        classesRef.document(docId)
                .set(classInfo)
                .addOnCompleteListener(callback);
    }

    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Retrieves a single class document by ID.
     *
     * @param classId  the document ID
     * @param callback success listener receiving the deserialized ClassInfo
     */
    public void getClass(@NonNull String classId,
                         @NonNull OnSuccessListener<ClassInfo> callback) {
        classesRef.document(classId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    callback.onSuccess(snapshot.toObject(ClassInfo.class));
                });
    }

    /**
     * Retrieves all class documents.
     *
     * @param callback success listener receiving a list of {@link ClassInfo} objects
     */
    public void getAllClasses(@NonNull OnSuccessListener<List<ClassInfo>> callback) {
        classesRef.get()
                .addOnSuccessListener(querySnapshot -> {
                    callback.onSuccess(querySnapshot.toObjects(ClassInfo.class));
                });
    }

    /**
     * Retrieves all classes assigned to a specific teacher.
     * <p>
     * Uses a Firestore {@code whereEqualTo} query on the {@code teacherId} field.
     * </p>
     *
     * @param teacherId the teacher's document ID
     * @param callback  success listener receiving matching classes
     */
    public void getClassesByTeacher(@NonNull String teacherId,
                                    @NonNull OnSuccessListener<List<ClassInfo>> callback) {
        classesRef.whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    callback.onSuccess(querySnapshot.toObjects(ClassInfo.class));
                });
    }

    // ── Update ──────────────────────────────────────────────────────────

    /**
     * Applies partial updates to a class document.
     *
     * @param classId  the document ID to update
     * @param updates  map of field names to new values
     * @param callback completion listener
     */
    public void updateClass(@NonNull String classId,
                            @NonNull Map<String, Object> updates,
                            @NonNull OnCompleteListener<Void> callback) {
        classesRef.document(classId)
                .update(updates)
                .addOnCompleteListener(callback);
    }

    /**
     * Adds a student to the {@code enrolledStudents} array using an atomic
     * {@link FieldValue#arrayUnion(Object...)} operation.
     * <p>
     * Idempotent — adding the same student ID twice has no effect.
     * </p>
     *
     * @param classId   the class document ID
     * @param studentId the student document ID to enroll
     * @param callback  completion listener
     */
    public void enrollStudent(@NonNull String classId, @NonNull String studentId,
                              @NonNull OnCompleteListener<Void> callback) {
        classesRef.document(classId)
                .update("enrolledStudents", FieldValue.arrayUnion(studentId))
                .addOnCompleteListener(callback);
    }

    /**
     * Removes a student from the {@code enrolledStudents} array using an atomic
     * {@link FieldValue#arrayRemove(Object...)} operation.
     *
     * @param classId   the class document ID
     * @param studentId the student document ID to un-enroll
     * @param callback  completion listener
     */
    public void unenrollStudent(@NonNull String classId, @NonNull String studentId,
                                @NonNull OnCompleteListener<Void> callback) {
        classesRef.document(classId)
                .update("enrolledStudents", FieldValue.arrayRemove(studentId))
                .addOnCompleteListener(callback);
    }

    // ── Delete ──────────────────────────────────────────────────────────

    /**
     * Deletes a class document from Firestore.
     *
     * @param classId  the document ID to delete
     * @param callback completion listener
     */
    public void deleteClass(@NonNull String classId,
                            @NonNull OnCompleteListener<Void> callback) {
        classesRef.document(classId)
                .delete()
                .addOnCompleteListener(callback);
    }
}