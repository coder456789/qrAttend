package com.qrattend.app.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.qrattend.app.MainActivity;
import com.qrattend.app.utils.Constants;

import java.util.Map;

/**
 * Firebase Cloud Messaging service for the QR-Attend application.
 * <p>
 * Handles two key responsibilities:
 * <ol>
 *   <li><strong>Token refresh:</strong> When a new FCM registration token is
 *       generated, it is persisted to the student's or teacher's Firestore
 *       document so the backend can target push notifications.</li>
 *   <li><strong>Message receipt:</strong> Incoming data/notification messages
 *       are displayed as system notifications. Typical triggers include
 *       session-started alerts, low-attendance warnings, and proxy-detection
 *       flags.</li>
 * </ol>
 * </p>
 *
 * <h3>Notification triggers (from Cloud Functions or admin):</h3>
 * <ul>
 *   <li>{@code session_started} — teacher started an attendance session</li>
 *   <li>{@code low_attendance} — student's attendance below threshold</li>
 *   <li>{@code proxy_detected} — proxy attempt flagged (teacher + admin)</li>
 *   <li>{@code device_change} — device-change request for admin approval</li>
 * </ul>
 *
 * @author QR-Attend Team — Member 3 (Backend & Integration Lead)
 * @version 1.0
 * @since 2026-03-27
 */
public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    /** Notification channel ID (required for Android 8.0+). */
    private static final String CHANNEL_ID = "qr_attend_notifications";

    /** Human-readable channel name shown in device settings. */
    private static final String CHANNEL_NAME = "QR-Attend Notifications";

    /** Auto-incrementing notification ID. */
    private static int notificationId = 0;

    // ── Token Refresh ───────────────────────────────────────────────────

    /**
     * Called when a new FCM registration token is generated.
     * <p>
     * This occurs on first app launch, when the token is invalidated, and
     * when the user clears app data. The token must be persisted to the
     * user's Firestore document so push notifications can be delivered.
     * </p>
     *
     * @param token the new FCM registration token
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        saveTokenToFirestore(token);
    }

    /**
     * Persists the FCM token to the current user's Firestore document.
     * <p>
     * Reads the locally cached user ID and role from SharedPreferences,
     * then updates the correct collection ({@code students} or {@code teachers}).
     * </p>
     *
     * @param token the FCM registration token
     */
    private void saveTokenToFirestore(@NonNull String token) {
        android.content.SharedPreferences prefs =
                getSharedPreferences("qr_attend_prefs", Context.MODE_PRIVATE);

        String userId = prefs.getString(Constants.PREF_USER_ID, null);
        String role = prefs.getString(Constants.PREF_USER_ROLE, null);

        if (userId == null || role == null) {
            Log.w(TAG, "User ID or role not found in SharedPreferences — "
                    + "token will be saved on next login.");
            return;
        }

        String collection = Constants.ROLE_TEACHER.equals(role)
                ? Constants.TEACHERS
                : Constants.STUDENTS;

        FirebaseFirestore.getInstance()
                .collection(collection)
                .document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener(aVoid ->
                        Log.d(TAG, "FCM token saved to " + collection + "/" + userId))
                .addOnFailureListener(e ->
                        Log.e(TAG, "Failed to save FCM token", e));
    }

    // ── Message Receipt ─────────────────────────────────────────────────

    /**
     * Called when an incoming FCM message is received while the app is in
     * the foreground, or when a data-only message arrives.
     * <p>
     * Extracts the title and body from either the notification payload or
     * the data payload (for data-only messages sent from Cloud Functions),
     * then displays a system notification.
     * </p>
     *
     * @param message the incoming {@link RemoteMessage}
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        super.onMessageReceived(message);
        Log.d(TAG, "Message received from: " + message.getFrom());

        String title;
        String body;

        // Prefer the notification payload if present
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        } else {
            // Fall back to data payload keys
            Map<String, String> data = message.getData();
            title = data.getOrDefault("title", "QR-Attend");
            body = data.getOrDefault("body", "You have a new notification.");
        }

        showNotification(title, body);
    }

    // ── Notification Display ────────────────────────────────────────────

    /**
     * Builds and displays a system notification.
     * <p>
     * Creates the notification channel on Android 8.0+ if it doesn't
     * already exist. Tapping the notification opens {@link MainActivity}.
     * </p>
     *
     * @param title notification title
     * @param body  notification body text
     */
    private void showNotification(String title, String body) {
        createNotificationChannel();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with app icon
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager != null) {
            manager.notify(notificationId++, builder.build());
        }
    }

    /**
     * Creates the notification channel required on Android 8.0 (API 26)
     * and above. Safe to call multiple times — the channel is only created
     * once.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for attendance sessions, "
                    + "proxy alerts, and system events.");

            NotificationManager manager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
