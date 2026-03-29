package com.qrattend.app.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.utils.Constants;

import java.util.HashMap;
import java.util.Map;

/**
 * Repository for managing {@code attendanceSessions} Firestore documents.
 * <p>
 * Handles session lifecycle (create → update nonce → end) and provides
 * a real-time listener for live attendance counts during an active session.
 * </p>
 *
 * @author QR-Attend Team — Member 3 (Backend & Integration Lead)
 * @version 1.0
 * @since 2026-03-27
 */
public class SessionRepository {

    /** Reference to the {@code attendanceSessions} Firestore collection. */
    private final CollectionReference sessionsRef;

    /**
     * Constructs a new SessionRepository using the default Firestore instance.
     */
    public SessionRepository() {
        this.sessionsRef = FirebaseFirestore.getInstance()
                .collection(Constants.SESSIONS);
    }

    // ── Create ──────────────────────────────────────────────────────────

    /**
     * Creates a new attendance session document.
     */
    public void createSession(@NonNull String sessionId,
                              @NonNull AttendanceSession session,
                              @NonNull OnCompleteListener<Void> callback) {
        sessionsRef.document(sessionId)
                .set(session)
                .addOnCompleteListener(callback);
    }

    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Retrieves a session document by its ID.
     */
    public void getSession(@NonNull String sessionId,
                           @NonNull OnSuccessListener<AttendanceSession> callback) {
        sessionsRef.document(sessionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    // Optimized: toObject handles null check internally
                    callback.onSuccess(snapshot.toObject(AttendanceSession.class));
                });
    }

    /**
     * Queries for the currently active session for a given class.
     */
    public void getActiveSession(@NonNull String classId,
                                 @NonNull OnSuccessListener<AttendanceSession> callback) {
        sessionsRef
                .whereEqualTo("classId", classId)
                .whereEqualTo("active", true)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        callback.onSuccess(querySnapshot.getDocuments().get(0)
                                .toObject(AttendanceSession.class));
                    } else {
                        callback.onSuccess(null);
                    }
                });
    }

    // ── Nonce / QR Update ───────────────────────────────────────────────

    /**
     * Updates the current QR nonce for a session.
     */
    public void updateNonce(@NonNull String sessionId, @NonNull String newNonce,
                            @NonNull OnCompleteListener<Void> callback) {
        sessionsRef.document(sessionId)
                .update("qrCode", newNonce)
                .addOnCompleteListener(callback);
    }

    // ── End Session ─────────────────────────────────────────────────────

    /**
     * Ends an active session by setting {@code active} to {@code false}.
     */
    public void endSession(@NonNull String sessionId,
                           @NonNull OnCompleteListener<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("active", false);
        updates.put("endTime", Timestamp.now());

        sessionsRef.document(sessionId)
                .update(updates)
                .addOnCompleteListener(callback);
    }

    // ── Real-time Listener ──────────────────────────────────────────────

    /**
     * Attaches a real-time snapshot listener to track live attendance count.
     */
    public ListenerRegistration listenToAttendanceCount(
            @NonNull String sessionId,
            @NonNull OnCountChangedListener callback) {

        return sessionsRef.document(sessionId)
                .collection(Constants.RECORDS)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        callback.onError(error.getMessage());
                        return;
                    }
                    int count = (value != null) ? value.size() : 0;
                    callback.onCountChanged(count);
                });
    }

    /**
     * Allows a teacher to manually override a student's attendance status.
     * This is used for GPS glitches or physical verification.
     */
    public void manuallyMarkPresent(@NonNull String sessionId,
                                    @NonNull String studentId,
                                    @NonNull OnCompleteListener<Void> listener) {
        // Use sessionsRef instead of db
        sessionsRef.document(sessionId)
                .collection(Constants.RECORDS)
                .document(studentId)
                .set(new HashMap<String, Object>() {{
                    put("status", "Present");
                    put("markedManually", true);
                    put("timestamp", com.google.firebase.Timestamp.now());
                }}, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(listener);
    }
    /**
     * Callback interface for real-time attendance count updates.
     */
    public interface OnCountChangedListener {
        void onCountChanged(int count);
        void onError(String errorMessage);
    }
}