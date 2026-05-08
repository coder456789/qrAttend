package com.qrattend.app.data.repository;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.qrattend.app.data.model.LeaveApplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository for leave applications.
 * Firestore collection: {@code leaveApplications}
 * Firebase Storage path: {@code leaveProofs/{studentId}/{fileName}}
 */
public class LeaveApplicationRepository {

    private static final String COLLECTION = "leaveApplications";

    private final CollectionReference colRef;
    private final StorageReference storageRef;

    public LeaveApplicationRepository() {
        colRef     = FirebaseFirestore.getInstance().collection(COLLECTION);
        storageRef = FirebaseStorage.getInstance().getReference("leaveProofs");
    }

    // ── Submit ───────────────────────────────────────────────────────────

    /**
     * Submits a leave application (no attachment).
     */
    public void submitApplication(@NonNull LeaveApplication app,
                                  @NonNull OnCompleteListener<Void> callback) {
        String docId = COLLECTION + "_" + System.currentTimeMillis();
        app.setApplicationId(docId);
        colRef.document(docId).set(app).addOnCompleteListener(callback);
    }

    /**
     * Uploads attachment to Firebase Storage, then submits the leave application.
     *
     * @param app        the leave application (attachmentUrl will be filled in)
     * @param fileUri    the local content URI of the attachment
     * @param studentId  used for the Storage path
     * @param fileName   original file name
     * @param callback   called after Firestore write completes
     */
    public void submitWithAttachment(@NonNull LeaveApplication app,
                                     @NonNull Uri fileUri,
                                     @NonNull String studentId,
                                     @NonNull String fileName,
                                     @NonNull OnCompleteListener<Void> callback) {
        String docId = COLLECTION + "_" + System.currentTimeMillis();
        app.setApplicationId(docId);
        app.setAttachmentFileName(fileName);

        StorageReference fileRef = storageRef.child(studentId)
                .child(docId + "_" + fileName);

        fileRef.putFile(fileUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        throw task.getException();
                    }
                    return fileRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    app.setAttachmentUrl(downloadUri.toString());
                    colRef.document(docId).set(app).addOnCompleteListener(callback);
                })
                .addOnFailureListener(e -> {
                    // Still submit without attachment if upload fails
                    app.setAttachmentUrl(null);
                    colRef.document(docId).set(app).addOnCompleteListener(callback);
                });
    }

    // ── Read ────────────────────────────────────────────────────────────

    /**
     * Fetches all leave applications submitted by a specific student.
     */
    public void getApplicationsByStudent(@NonNull String studentId,
                                         @NonNull OnSuccessListener<List<LeaveApplication>> callback) {
        colRef.whereEqualTo("studentId", studentId)
                .orderBy("submittedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    List<LeaveApplication> list = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        LeaveApplication a = doc.toObject(LeaveApplication.class);
                        if (a != null) {
                            a.setApplicationId(doc.getId());
                            list.add(a);
                        }
                    }
                    callback.onSuccess(list);
                });
    }

    /**
     * Fetches ALL leave applications (teacher view), ordered most-recent first.
     */
    public void getAllApplications(@NonNull OnSuccessListener<List<LeaveApplication>> callback) {
        colRef.orderBy("submittedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    List<LeaveApplication> list = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        LeaveApplication a = doc.toObject(LeaveApplication.class);
                        if (a != null) {
                            a.setApplicationId(doc.getId());
                            list.add(a);
                        }
                    }
                    callback.onSuccess(list);
                });
    }

    /**
     * Real-time listener on all applications (teacher live view).
     */
    public com.google.firebase.firestore.ListenerRegistration listenAllApplications(
            @NonNull OnSuccessListener<List<LeaveApplication>> callback) {
        return colRef.orderBy("submittedAt",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    List<LeaveApplication> list = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        LeaveApplication a = doc.toObject(LeaveApplication.class);
                        if (a != null) {
                            a.setApplicationId(doc.getId());
                            list.add(a);
                        }
                    }
                    callback.onSuccess(list);
                });
    }
}
