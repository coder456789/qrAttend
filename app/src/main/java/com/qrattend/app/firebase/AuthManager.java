package com.qrattend.app.firebase;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.qrattend.app.utils.Constants;

/**
 * Centralized authentication manager for the QR-Attend application.
 * <p>
 * Wraps <strong>Firebase Authentication</strong> to provide a clean API for
 * email/password login, user registration, logout, and role-based routing.
 * All Activities interact with Firebase Auth exclusively through this class.
 * </p>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 *   <li>Email/password sign-up and login via {@link FirebaseAuth}</li>
 *   <li>Session state queries ({@link #isLoggedIn()}, {@link #getCurrentUserId()})</li>
 *   <li>Role retrieval from Firestore ({@link #getUserRole(Context, OnSuccessListener)})</li>
 *   <li>Logout with local preference cleanup</li>
 * </ul>
 *
 * <h3>Usage from Activities:</h3>
 * <pre>{@code
 *   AuthManager authManager = new AuthManager();
 *
 *   // Login
 *   authManager.loginWithEmail(email, password, task -> {
 *       if (task.isSuccessful()) { // navigate to dashboard }
 *   });
 *
 *   // Check role after login
 *   authManager.getUserRole(this, role -> {
 *       if ("student".equals(role))  startActivity(StudentDashboard);
 *       if ("teacher".equals(role))  startActivity(TeacherDashboard);
 *       if ("admin".equals(role))    startActivity(AdminDashboard);
 *   });
 * }</pre>
 *
 * @author QR-Attend Team - Member 3 (Backend and Integration Lead)
 * @version 1.0
 * @since 2026-03-27
 */
public class AuthManager {

    private static final String TAG = "AuthManager";

    /** SharedPreferences file name used across the app. */
    private static final String PREFS_NAME = "qr_attend_prefs";

    // ── Firebase instances ──────────────────────────────────────────────

    /** Firebase Authentication instance — handles all credential operations. */
    private final FirebaseAuth auth;

    /** Firestore instance — used to look up user roles. */
    private final FirebaseFirestore db;

    // ════════════════════════════════════════════════════════════════════
    //  CONSTRUCTOR
    // ════════════════════════════════════════════════════════════════════

    /**
     * Constructs an AuthManager using the default Firebase Auth and
     * Firestore instances.
     */
    public AuthManager() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
    }

    // ════════════════════════════════════════════════════════════════════
    //  EMAIL / PASSWORD — LOGIN
    // ════════════════════════════════════════════════════════════════════

    /**
     * Authenticates a user with email and password via Firebase Auth.
     * <p>
     * On success, {@link FirebaseAuth#getCurrentUser()} will return the
     * authenticated user. The caller should then call
     * {@link #getUserRole(Context, OnSuccessListener)} to determine
     * which dashboard to navigate to.
     * </p>
     *
     * @param email    the user's email address
     * @param password the user's password
     * @param callback completion listener — check {@code task.isSuccessful()}
     */
    public void loginWithEmail(@NonNull String email,
                               @NonNull String password,
                               @NonNull OnCompleteListener<AuthResult> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(callback);
    }

    // ════════════════════════════════════════════════════════════════════
    //  EMAIL / PASSWORD — SIGN UP
    // ════════════════════════════════════════════════════════════════════

    /**
     * Creates a new Firebase Auth account with email and password.
     * <p>
     * After this succeeds, the caller should write the user's profile
     * data (name, phone, role, etc.) to the appropriate Firestore
     * collection ({@code students} or {@code teachers}) using the
     * corresponding repository class.
     * </p>
     *
     * @param email    the desired email address
     * @param password the desired password (Firebase requires ≥ 6 chars)
     * @param callback completion listener
     */
    public void signupWithEmail(@NonNull String email,
                                @NonNull String password,
                                @NonNull OnCompleteListener<AuthResult> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(callback);
    }

    // ════════════════════════════════════════════════════════════════════
    //  LOGOUT
    // ════════════════════════════════════════════════════════════════════

    /**
     * Signs the current user out of Firebase Auth and clears locally
     * cached user preferences (role, user ID).
     *
     * @param context the application or Activity context (needed for
     *                SharedPreferences cleanup). Pass {@code null}
     *                if only Firebase sign-out is needed.
     */
    public void logout(@Nullable Context context) {
        // Firebase sign-out
        auth.signOut();
        Log.d(TAG, "User signed out of Firebase Auth.");

        if (context != null) {
            // Clear cached role and user ID
            clearLocalPreferences(context);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  SESSION STATE QUERIES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Returns the UID of the currently authenticated user.
     *
     * @return the Firebase Auth UID, or {@code null} if no user is signed in
     */
    @Nullable
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    /**
     * Returns the currently authenticated {@link FirebaseUser}.
     *
     * @return the current user, or {@code null} if not signed in
     */
    @Nullable
    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    /**
     * Checks whether a user is currently signed in.
     *
     * @return {@code true} if a user is authenticated
     */
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    // ════════════════════════════════════════════════════════════════════
    //  ROLE DETECTION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Determines the authenticated user's role by querying Firestore.
     * <p>
     * Checks the {@code students} collection first. If no document exists
     * for the user's UID, checks the {@code teachers} collection. If
     * neither collection contains the UID, returns {@code "admin"} as the
     * default fallback.
     * </p>
     * <p>
     * On success, the resolved role is cached in SharedPreferences
     * (key: {@link Constants#PREF_USER_ROLE}) so subsequent checks can
     * be done locally without a Firestore read.
     * </p>
     *
     * @param context  application or Activity context
     * @param callback success listener receiving the role string:
     *                 {@code "student"}, {@code "teacher"}, or {@code "admin"}
     */
    public void getUserRole(@NonNull Context context,
                            @NonNull OnSuccessListener<String> callback) {
        String uid = getCurrentUserId();

        if (uid == null) {
            Log.w(TAG, "getUserRole called but no user is signed in.");
            callback.onSuccess(null);
            return;
        }

        // Check students collection first
        db.collection(Constants.STUDENTS).document(uid)
                .get()
                .addOnSuccessListener(studentDoc -> {
                    if (studentDoc.exists()) {
                        cacheRole(context, uid, Constants.ROLE_STUDENT);
                        callback.onSuccess(Constants.ROLE_STUDENT);
                    } else {
                        // Not a student — check teachers collection
                        db.collection(Constants.TEACHERS).document(uid)
                                .get()
                                .addOnSuccessListener(teacherDoc -> {
                                    if (teacherDoc.exists()) {
                                        cacheRole(context, uid, Constants.ROLE_TEACHER);
                                        callback.onSuccess(Constants.ROLE_TEACHER);
                                    } else {
                                        // Check admins collection explicitly
                                        db.collection("admins").document(uid).get().addOnSuccessListener(adminDoc -> {
                                            if (adminDoc.exists()) {
                                                cacheRole(context, uid, Constants.ROLE_ADMIN);
                                                callback.onSuccess(Constants.ROLE_ADMIN);
                                            } else {
                                                //No role found? Sign them out!
                                                logout(context);
                                                callback.onSuccess("unauthorized");
                                            }
                                        });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to check teachers collection", e);
                                    callback.onSuccess(null);
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check students collection", e);
                    callback.onSuccess(null);
                });
    }

    /**
     * Returns the locally cached role without making a Firestore query.
     * <p>
     * Returns {@code null} if no role has been cached yet (i.e.,
     * {@link #getUserRole(Context, OnSuccessListener)} has not been
     * called since the last login).
     * </p>
     *
     * @param context application or Activity context
     * @return the cached role string, or {@code null}
     */
    @Nullable
    public String getCachedRole(@NonNull Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(Constants.PREF_USER_ROLE, null);
    }

    // ════════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Caches the user's ID and role in SharedPreferences for offline access
     * and for use by {@link FCMService} when persisting FCM tokens.
     *
     * @param context application or Activity context
     * @param uid     the user's Firebase Auth UID
     * @param role    the resolved role string
     */
    private void cacheRole(@NonNull Context context,
                           @NonNull String uid,
                           @NonNull String role) {
        SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit();
        editor.putString(Constants.PREF_USER_ID, uid);
        editor.putString(Constants.PREF_USER_ROLE, role);
        editor.apply();
        Log.d(TAG, "Cached role: uid=" + uid + ", role=" + role);
    }

    /**
     * Clears locally cached user preferences on logout.
     *
     * @param context application or Activity context
     */
    private void clearLocalPreferences(@NonNull Context context) {
        SharedPreferences.Editor editor = context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit();
        editor.remove(Constants.PREF_USER_ID);
        editor.remove(Constants.PREF_USER_ROLE);
        editor.apply();
        Log.d(TAG, "Local preferences cleared.");
    }
}
