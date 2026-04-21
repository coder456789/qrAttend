package com.qrattend.app.data.repository;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Repository for managing attendance records stored as a Firestore subcollection.
 * <p>
 * Firestore path: {@code attendanceSessions/{sessionId}/records/{studentId}}
 * </p>
 */
public class AttendanceRepository {

    /** Root Firestore instance. */
    private final FirebaseFirestore db;

    /**
     * Constructs a new AttendanceRepository using the default Firestore instance.
     */
    public AttendanceRepository() {
        this.db = FirebaseFirestore.getInstance();
    }

    // ── Write ───────────────────────────────────────────────────────────

    /**
     * Marks attendance for a student in a specific session.
     */
    public void markAttendance(@NonNull String sessionId,
                               @NonNull String studentId,
                               @NonNull AttendanceRecord record,
                               @NonNull OnCompleteListener<Void> callback) {
        // Ensure the record's internal IDs are set correctly for collectionGroup queries
        record.setSessionId(sessionId);
        record.setStudentId(studentId);
        
        getRecordsRef(sessionId)
                .document(studentId)
                .set(record)
                .addOnCompleteListener(callback);
    }
    /**
     * Allows a teacher to manually override a student's attendance status.
     * This is used for GPS glitches or physical verification.
     */
    public void manuallyMarkPresent(@NonNull String sessionId,
                                    @NonNull String studentId,
                                    @NonNull OnCompleteListener<Void> listener) {
        // Use your helper method getRecordsRef to reach the subcollection
        getRecordsRef(sessionId)
                .document(studentId)
                .set(new HashMap<String, Object>() {{
                    put("status", "Present");
                    put("markedManually", true);
                    put("timestamp", com.google.firebase.Timestamp.now());
                }}, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(listener);
    }
    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Checks whether a student has already submitted an attendance record.
     */
    public void hasAlreadyMarked(@NonNull String sessionId,
                                 @NonNull String studentId,
                                 @NonNull OnSuccessListener<Boolean> callback) {
        getRecordsRef(sessionId)
                .document(studentId)
                .get()
                .addOnSuccessListener(snapshot -> callback.onSuccess(snapshot.exists()));
    }

    /**
     * Retrieves all attendance records for a given session (Teacher View).
     */
    public void getSessionRecords(@NonNull String sessionId,
                                  @NonNull OnSuccessListener<List<AttendanceRecord>> callback) {
        getRecordsRef(sessionId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AttendanceRecord> records = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        AttendanceRecord r = doc.toObject(AttendanceRecord.class);
                        if (r != null) {
                            records.add(r);
                        }
                    }
                    callback.onSuccess(records);
                });
    }

    /**
     * Retrieves a student's attendance history across all sessions (Student History).
     * <p>
     * Optimized to filter on the server side to prevent overwhelming device RAM.
     * Note: Requires a Collection Group index on 'studentId' in Firestore.
     * </p>
     */
    public void getStudentHistory(@NonNull String studentId,
                                  @NonNull OnSuccessListener<List<AttendanceRecord>> callback) {
        db.collectionGroup(Constants.RECORDS)
                .whereEqualTo("studentId", studentId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AttendanceRecord> history = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        AttendanceRecord r = doc.toObject(AttendanceRecord.class);
                        if (r != null) {
                            history.add(r);
                        }
                    }
                    callback.onSuccess(history);
                });
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /**
     * Returns a reference to the {@code records} subcollection.
     */
    private CollectionReference getRecordsRef(@NonNull String sessionId) {
        return db.collection(Constants.SESSIONS)
                .document(sessionId)
                .collection(Constants.RECORDS);
    }
}