package com.qrattend.app.data.repository;

import android.content.ContentResolver;
import android.net.Uri;
import android.util.Base64;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.qrattend.app.data.model.LeaveApplication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for leave applications.
 *
 * <p>Attachment strategy (in priority order):
 * <ol>
 *   <li>Try Firebase Storage → get download URL → save to {@code attachmentUrl}.</li>
 *   <li>If Storage upload fails (rules / network) → encode file as Base64 and store
 *       directly inside the Firestore document in {@code attachmentBase64}.
 *       The teacher can then decode and view the file in-app.</li>
 * </ol>
 *
 * Firestore collection: {@code leaveApplications}
 * Firebase Storage path: {@code leaveProofs/{studentId}/{fileName}}
 */
public class LeaveApplicationRepository {

    private static final String COLLECTION = "leaveApplications";
    /** Max file size for Base64 fallback: 2 MB (Firestore doc limit is 1 MB, so 1.5 MB is safe) */
    private static final int MAX_BASE64_BYTES = 1_400_000;

    private final CollectionReference colRef;
    private final StorageReference storageRef;

    /** Android ContentResolver held temporarily during async encoding — must be set by caller. */
    private ContentResolver contentResolver;

    public LeaveApplicationRepository() {
        colRef     = FirebaseFirestore.getInstance().collection(COLLECTION);
        storageRef = FirebaseStorage.getInstance().getReference("leaveProofs");
    }

    /** Must be called before submitWithAttachment when ContentResolver is available. */
    public void setContentResolver(@NonNull ContentResolver cr) {
        this.contentResolver = cr;
    }

    // ── Submit ────────────────────────────────────────────────────────────

    /** Submits a leave application with no attachment. */
    public void submitApplication(@NonNull LeaveApplication app,
                                  @NonNull OnCompleteListener<Void> callback) {
        String docId = COLLECTION + "_" + System.currentTimeMillis();
        app.setApplicationId(docId);
        colRef.document(docId).set(app).addOnCompleteListener(callback);
    }

    /**
     * Tries Firebase Storage first; falls back to Base64 in Firestore if storage fails.
     * Always calls {@link #setContentResolver} before this method.
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
                    // ✅ Storage upload succeeded
                    app.setAttachmentUrl(downloadUri.toString());
                    colRef.document(docId).set(app).addOnCompleteListener(callback);
                })
                .addOnFailureListener(e -> {
                    // ⚠️ Storage failed → encode as Base64 fallback
                    encodeAndSubmit(app, fileUri, docId, callback);
                });
    }

    /**
     * Reads the file from the content URI, Base64-encodes it, and saves it
     * inside the Firestore document. Works without any Firebase Storage rules.
     */
    private void encodeAndSubmit(@NonNull LeaveApplication app,
                                 @NonNull Uri fileUri,
                                 @NonNull String docId,
                                 @NonNull OnCompleteListener<Void> callback) {
        new Thread(() -> {
            try {
                byte[] bytes = readBytes(fileUri);
                if (bytes != null && bytes.length <= MAX_BASE64_BYTES) {
                    String b64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    app.setAttachmentBase64(b64);
                    app.setAttachmentBase64MimeType(app.getAttachmentMimeType());
                }
                // Even if too large, still submit (without attachment)
                colRef.document(docId).set(app).addOnCompleteListener(callback);
            } catch (Exception ex) {
                colRef.document(docId).set(app).addOnCompleteListener(callback);
            }
        }).start();
    }

    private byte[] readBytes(Uri uri) {
        if (contentResolver == null) return null;
        try (InputStream is = contentResolver.openInputStream(uri);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            if (is == null) return null;
            byte[] buf = new byte[8192];
            int read;
            while ((read = is.read(buf)) != -1) bos.write(buf, 0, read);
            return bos.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────

    /** Fetches all leave applications submitted by a specific student. */
    public void getApplicationsByStudent(@NonNull String studentId,
                                         @NonNull OnSuccessListener<List<LeaveApplication>> callback) {
        colRef.whereEqualTo("studentId", studentId)
                .orderBy("submittedAt",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    List<LeaveApplication> list = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        LeaveApplication a = doc.toObject(LeaveApplication.class);
                        if (a != null) { a.setApplicationId(doc.getId()); list.add(a); }
                    }
                    callback.onSuccess(list);
                });
    }

    /** Fetches ALL leave applications (teacher view), ordered most-recent first. */
    public void getAllApplications(@NonNull OnSuccessListener<List<LeaveApplication>> callback) {
        colRef.orderBy("submittedAt",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    List<LeaveApplication> list = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        LeaveApplication a = doc.toObject(LeaveApplication.class);
                        if (a != null) { a.setApplicationId(doc.getId()); list.add(a); }
                    }
                    callback.onSuccess(list);
                });
    }

    /** Real-time listener on all applications (teacher live view). */
    public com.google.firebase.firestore.ListenerRegistration listenAllApplications(
            @NonNull OnSuccessListener<List<LeaveApplication>> callback) {
        return colRef.orderBy("submittedAt",
                        com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    List<LeaveApplication> list = new ArrayList<>();
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        LeaveApplication a = doc.toObject(LeaveApplication.class);
                        if (a != null) { a.setApplicationId(doc.getId()); list.add(a); }
                    }
                    callback.onSuccess(list);
                });
    }

    // ── Update ────────────────────────────────────────────────────────────

    /** Updates the status of a leave application ("Approved" / "Rejected"). */
    public void updateStatus(@NonNull String applicationId,
                             @NonNull String newStatus,
                             @NonNull OnCompleteListener<Void> callback) {
        colRef.document(applicationId)
                .update("status", newStatus)
                .addOnCompleteListener(callback);
    }
}
