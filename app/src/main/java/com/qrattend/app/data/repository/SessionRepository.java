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
 * @version 1.1
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
    /**
     * Retrieves all sessions (active or ended) created by a specific teacher,
     * ordered by start time descending (newest first).
     * Used by the teacher dashboard to list historical sessions.
     */
    public void getAllSessionsByTeacher(@NonNull String teacherId,
                                       @NonNull OnSuccessListener<List<AttendanceSession>> callback) {
        sessionsRef
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AttendanceSession> sessions = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : querySnapshot.getDocuments()) {
                        AttendanceSession s = doc.toObject(AttendanceSession.class);
                        if (s != null) sessions.add(s);
                    }
                    // Sort newest-first without needing a Firestore composite index
                    sessions.sort((a, b) -> {
                        if (a.getStartTime() == null) return 1;
                        if (b.getStartTime() == null) return -1;
                        return b.getStartTime().compareTo(a.getStartTime());
                    });
                    callback.onSuccess(sessions);
                })
                .addOnFailureListener(e -> callback.onSuccess(new java.util.ArrayList<>()));
    }

    /**
     * Retrieves all sessions for a specific class (by classId + teacherId),
     * sorted oldest-first (for chronological CSV columns).
     */
    public void getSessionsByClass(@NonNull String classId,
                                   @NonNull String teacherId,
                                   @NonNull OnSuccessListener<List<AttendanceSession>> callback) {
        sessionsRef
                .whereEqualTo("classId", classId)
                .whereEqualTo("teacherId", teacherId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<AttendanceSession> sessions = new java.util.ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : querySnapshot.getDocuments()) {
                        AttendanceSession s = doc.toObject(AttendanceSession.class);
                        if (s != null) sessions.add(s);
                    }
                    // Sort oldest-first for chronological CSV columns
                    sessions.sort((a, b) -> {
                        if (a.getStartTime() == null) return 1;
                        if (b.getStartTime() == null) return -1;
                        return a.getStartTime().compareTo(b.getStartTime());
                    });
                    callback.onSuccess(sessions);
                })
                .addOnFailureListener(e -> callback.onSuccess(new java.util.ArrayList<>()));
    }

    // ── Nonce / QR Update ───────────────────────────────────────────────

    /**
     * FIX (nonce grace window): Before overwriting {@code qrCode} with the
     * new nonce, the CURRENT nonce is copied to {@code previousQrCode} with
     * an expiry of {@code now + Constants.PREVIOUS_NONCE_GRACE_MS} (20s).
     * This lets students who scanned the old QR but were delayed by GPS
     * multi-sample collection (up to 14 seconds) still pass nonce validation.
     *
     * The sessionKey is intentionally NOT rotated — it must remain stable
     * so that the student's scanner (initialized once at session discovery)
     * can always decrypt QR codes for the full session lifetime.
     */
    public void updateNonce(@NonNull String sessionId,
                            @NonNull String newNonce,
                            @NonNull String newSessionKey,
                            @NonNull OnCompleteListener<Void> callback) {
        // Step 1: Read the current nonce so we can preserve it as previousQrCode
        sessionsRef.document(sessionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("qrCode", newNonce);

                    // Preserve current nonce as previous with a grace window
                    String currentNonce = snapshot.getString("qrCode");
                    if (currentNonce != null && !currentNonce.isEmpty()) {
                        updates.put("previousQrCode", currentNonce);
                        updates.put("previousNonceExpiryMs",
                                System.currentTimeMillis() + Constants.PREVIOUS_NONCE_GRACE_MS);
                    }

                    // sessionKey deliberately excluded — rotating it would break
                    // any scanner that was initialized before the rotation.

                    sessionsRef.document(sessionId)
                            .update(updates)
                            .addOnCompleteListener(callback);
                })
                .addOnFailureListener(e -> {
                    // Fallback: write new nonce without preserving previous
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("qrCode", newNonce);
                    sessionsRef.document(sessionId)
                            .update(updates)
                            .addOnCompleteListener(callback);
                });
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