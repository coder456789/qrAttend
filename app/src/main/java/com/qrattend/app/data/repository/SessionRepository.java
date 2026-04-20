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
import java.util.List;
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
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        List<AttendanceSession> sessions = new java.util.ArrayList<>();
                        for (AttendanceSession s : querySnapshot.toObjects(AttendanceSession.class)) {
                            // Filter out manually created junk sessions from Firestore with invalid keys
                            if (s.getSessionKey() != null && s.getSessionKey().length() >= 32) {
                                sessions.add(s);
                            }
                        }
                        
                        if (sessions.isEmpty()) {
                            callback.onSuccess(null);
                            return;
                        }

                        // Manually sort descending by startTime to avoid Firestore composite index requirement
                        sessions.sort((s1, s2) -> {
                            if (s1.getStartTime() == null) return 1;
                            if (s2.getStartTime() == null) return -1;
                            return s2.getStartTime().compareTo(s1.getStartTime());
                        });
                        callback.onSuccess(sessions.get(0));
                    } else {
                        callback.onSuccess(null);
                    }
                });
    }

    // ── Nonce / QR Update ───────────────────────────────────────────────

    /**
     * Updates only the QR nonce in Firestore each refresh cycle.
     * The sessionKey is intentionally NOT rotated — it must remain stable
     * so that the student's scanner (initialized once at session discovery)
     * can always decrypt QR codes for the full session lifetime.
     */
    public void updateSecureNonce(@NonNull String sessionId,
                                  @NonNull String newNonce,
                                  @NonNull String newSessionKey,
                                  @NonNull OnCompleteListener<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("qrCode", newNonce);
        // sessionKey deliberately excluded — rotating it would break
        // any scanner that was initialized before the rotation.

        sessionsRef.document(sessionId)
                .update(updates)
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
     * Callback interface for real-time attendance count updates.
     */
    public interface OnCountChangedListener {
        void onCountChanged(int count);
        void onError(String errorMessage);
    }
}
