package com.qrattend.app.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.qrattend.app.utils.Constants;

public class AuthManager {

    private static final String TAG = "AuthManager";
    private static final String PREFS_NAME = "qr_attend_prefs";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthManager() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    public void loginWithEmail(@NonNull String email,
                               @NonNull String password,
                               @NonNull OnCompleteListener<AuthResult> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(callback);
    }

    public void signupWithEmail(@NonNull String email,
                                @NonNull String password,
                                @NonNull OnCompleteListener<AuthResult> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(callback);
    }

    public void sendPasswordResetEmail(@NonNull String email,
                                       @NonNull OnCompleteListener<Void> callback) {
        auth.sendPasswordResetEmail(email)
                .addOnCompleteListener(callback);
    }

    public void changePassword(@NonNull String currentPassword,
                               @NonNull String newPassword,
                               @NonNull OnCompleteListener<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getEmail() != null) {
            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
            user.reauthenticate(credential).addOnCompleteListener(reauthTask -> {
                if (reauthTask.isSuccessful()) {
                    user.updatePassword(newPassword).addOnCompleteListener(callback);
                } else {
                    // Fail the task if re-auth fails
                    callback.onComplete(Tasks.forException(reauthTask.getException()));
                }
            });
        }
    }

    public void logout(@Nullable Context context) {
        auth.signOut();
        if (context != null) {
            clearLocalPreferences(context);
        }
    }

    @Nullable
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Optimised role detection: Checks cache first, then Firestore.
     */
    public void getUserRole(@NonNull Context context,
                            @NonNull OnSuccessListener<String> callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            callback.onSuccess(null);
            return;
        }

        // 1. Check Cache First (Instant)
        String cachedRole = getCachedRole(context);
        if (cachedRole != null) {
            Log.d(TAG, "Using cached role: " + cachedRole);
            callback.onSuccess(cachedRole);
            return; 
        }

        // 2. Fallback to Firestore (Network)
        db.collection(Constants.STUDENTS).document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        cacheRole(context, uid, Constants.ROLE_STUDENT);
                        callback.onSuccess(Constants.ROLE_STUDENT);
                    } else {
                        db.collection(Constants.TEACHERS).document(uid)
                                .get()
                                .addOnSuccessListener(teacherDoc -> {
                                    if (teacherDoc.exists()) {
                                        cacheRole(context, uid, Constants.ROLE_TEACHER);
                                        callback.onSuccess(Constants.ROLE_TEACHER);
                                    } else {
                                        logout(context);
                                        callback.onSuccess("unauthorized");
                                    }
                                })
                                .addOnFailureListener(e -> callback.onSuccess(null));
                    }
                })
                .addOnFailureListener(e -> callback.onSuccess(null));
    }

    @Nullable
    public String getCachedRole(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Constants.PREF_USER_ROLE, null);
    }

    private void cacheRole(@NonNull Context context, @NonNull String uid, @NonNull String role) {
        SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit();
        editor.putString(Constants.PREF_USER_ID, uid);
        editor.putString(Constants.PREF_USER_ROLE, role);
        editor.apply();
    }

    private void clearLocalPreferences(@NonNull Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}
