package com.qrattend.app.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * LocationHelper — Member 2 (Core Logic & QR Lead)
 *
 * Wraps Google Play Services Fused Location Provider (consistent with PRD tech stack)
 * to obtain a fresh, high-accuracy GPS fix for GeoValidator.
 *
 * PRD requirement: location fix must be < 30 seconds old.
 * Mock location detection is handled by GeoValidator.
 *
 * Integration with Member 3:
 *   - ScanQRActivity (Member 1) calls fetchCurrentLocation()
 *   - The returned Location is passed to ProxyDetectionEngine.validate()
 *     alongside the AttendanceSession from SessionRepository.getSession()
 *
 * Usage:
 *   LocationHelper.fetchCurrentLocation(context, new LocationHelper.LocationCallback() {
 *       public void onSuccess(Location loc) { // pass to ProxyDetectionEngine }
 *       public void onFailure(String reason) { // show error to user }
 *   });
 */
public class LocationHelper {

    /** Max age (ms) for a location fix per PRD (30 seconds). */
    public static final long MAX_LOCATION_AGE_MS = 30_000L;

    private static final long FETCH_TIMEOUT_MS = 10_000L;

    // -----------------------------------------------------------------------
    // Callback interface
    // -----------------------------------------------------------------------

    public interface LocationCallback {
        void onSuccess(Location location);
        void onFailure(String reason); // "permission_denied" | "timeout" | "unavailable"
    }

    // -----------------------------------------------------------------------
    // Main fetch method
    // -----------------------------------------------------------------------

    /**
     * Fetches the most accurate recent location using Fused Location Provider.
     * Tries last known location first; if stale, requests a fresh fix.
     *
     * @param context  Activity or Application context
     * @param callback Result callback
     */
    public static void fetchCurrentLocation(@NonNull Context context,
                                            @NonNull LocationCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onFailure("permission_denied");
            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(context);

        try {
            client.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null && isFresh(location)) {
                            callback.onSuccess(location);
                        } else {
                            requestFreshLocation(client, context, callback);
                        }
                    })
                    .addOnFailureListener(e ->
                            requestFreshLocation(client, context, callback));
        } catch (SecurityException e) {
            callback.onFailure("permission_denied");
        }
    }

    // -----------------------------------------------------------------------
    // Fresh location request
    // -----------------------------------------------------------------------

    private static void requestFreshLocation(FusedLocationProviderClient client,
                                             Context context,
                                             LocationCallback callback) {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdates(1)
                .setDurationMillis(FETCH_TIMEOUT_MS)
                .build();

        com.google.android.gms.location.LocationCallback gmsCallback =
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult result) {
                        client.removeLocationUpdates(this);
                        Location loc = result.getLastLocation();
                        if (loc != null) callback.onSuccess(loc);
                        else callback.onFailure("unavailable");
                    }
                };

        // Timeout fallback
        new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
            client.removeLocationUpdates(gmsCallback);
            callback.onFailure("timeout");
        }, FETCH_TIMEOUT_MS);

        try {
            client.requestLocationUpdates(request, gmsCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onFailure("permission_denied");
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns true if the location fix is within the 30-second freshness window. */
    public static boolean isFresh(Location location) {
        long ageMs = System.currentTimeMillis() - location.getTime();
        return ageMs >= 0 && ageMs <= MAX_LOCATION_AGE_MS;
    }

    /** Returns true if ACCESS_FINE_LOCATION is granted. */
    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Computes distance in metres between two lat/lng pairs.
     * Used by GeoValidator to check against AttendanceSession.getGeofenceRadius().
     */
    public static float distanceBetweenMeters(double lat1, double lon1,
                                              double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}