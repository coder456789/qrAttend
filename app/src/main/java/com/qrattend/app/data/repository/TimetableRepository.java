package com.qrattend.app.data.repository;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.qrattend.app.data.model.TimetableEntry;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Firestore CRUD for timetable entries stored under:
 *   teachers/{teacherId}/timetable/{entryId}
 */
public class TimetableRepository {

    private final FirebaseFirestore db;

    public TimetableRepository() {
        db = FirebaseFirestore.getInstance();
    }

    // ── Callback interfaces ───────────────────────────────────────────────

    public interface TimetableCallback {
        void onResult(List<TimetableEntry> entries);
    }

    public interface SaveCallback {
        void onComplete(boolean success, String entryId);
    }

    public interface DeleteCallback {
        void onComplete(boolean success);
    }

    // ── Firestore operations ──────────────────────────────────────────────

    /** Fetch all timetable entries for a teacher (all days at once). */
    public void getEntriesForTeacher(String teacherId, TimetableCallback callback) {
        db.collection(Constants.TEACHERS)
                .document(teacherId)
                .collection(Constants.TIMETABLE)
                .get()
                .addOnSuccessListener(snap -> {
                    List<TimetableEntry> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snap) {
                        TimetableEntry e = doc.toObject(TimetableEntry.class);
                        e.setId(doc.getId());
                        list.add(e);
                    }
                    callback.onResult(list);
                })
                .addOnFailureListener(ex -> callback.onResult(new ArrayList<>()));
    }

    /**
     * Add a new entry (id == null) or update an existing one (id != null).
     */
    public void saveEntry(String teacherId, TimetableEntry entry, SaveCallback callback) {
        if (entry.getId() == null || entry.getId().isEmpty()) {
            // Create
            db.collection(Constants.TEACHERS)
                    .document(teacherId)
                    .collection(Constants.TIMETABLE)
                    .add(entry)
                    .addOnSuccessListener(ref -> callback.onComplete(true, ref.getId()))
                    .addOnFailureListener(ex -> callback.onComplete(false, null));
        } else {
            // Update
            db.collection(Constants.TEACHERS)
                    .document(teacherId)
                    .collection(Constants.TIMETABLE)
                    .document(entry.getId())
                    .set(entry)
                    .addOnSuccessListener(v -> callback.onComplete(true, entry.getId()))
                    .addOnFailureListener(ex -> callback.onComplete(false, null));
        }
    }

    /** Delete a timetable entry by its Firestore document ID. */
    public void deleteEntry(String teacherId, String entryId, DeleteCallback callback) {
        db.collection(Constants.TEACHERS)
                .document(teacherId)
                .collection(Constants.TIMETABLE)
                .document(entryId)
                .delete()
                .addOnSuccessListener(v -> callback.onComplete(true))
                .addOnFailureListener(ex -> callback.onComplete(false));
    }
}
