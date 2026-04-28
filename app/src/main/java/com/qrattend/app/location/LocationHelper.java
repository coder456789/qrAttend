package com.qrattend.app.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.List;

/**
 * LocationHelper — Member 2 (Core Logic & QR Lead)
 *
 * Provides TWO fetch strategies:
 *
 * 1. fetchQuickLocation() — TEACHER side.
 *    Takes 1–2 quick samples and returns the more precise one.
 *    Indoor accuracy will be low, that's fine — the teacher is the anchor.
 *
 * 2. fetchAllLocations() — STUDENT side.
 *    Collects up to 5 samples over 12 seconds and returns ALL of them.
 *    ScanQRActivity then picks the one NEAREST to the teacher,
 *    because the closest reading is most likely the true position.
 *
 * Mock location detection is handled by GeoValidator.
 */
public class LocationHelper {

    private static final String TAG = "LocationHelper";

    /** Max age (ms) for a location fix (30 seconds). */
    public static final long MAX_LOCATION_AGE_MS = 30_000L;

    // -----------------------------------------------------------------------
    // Callback interfaces
    // -----------------------------------------------------------------------

    public interface LocationCallback {
        void onSuccess(Location location);
        void onFailure(String reason); // "permission_denied" | "timeout" | "unavailable"
    }

    /**
     * Callback for student multi-sample: returns ALL collected locations
     * so the caller can pick the best one based on distance to teacher.
     */
    public interface MultiLocationCallback {
        void onLocationsCollected(List<Location> locations);
        void onFailure(String reason);
    }

    // =======================================================================
    // 1. TEACHER — Quick fetch (1-2 samples, pick more precise)
    // =======================================================================

    /**
     * Fetches location quickly with at most 2 samples.
     * Returns the more precise one. Does NOT do a long multi-sample collection.
     * Used by StartSessionActivity for the teacher's anchor point.
     *
     * @param context  Activity context
     * @param callback Result callback
     */
    public static void fetchQuickLocation(@NonNull Context context,
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
                            Log.d(TAG, "[Teacher] Cached location: accuracy="
                                    + location.getAccuracy() + "m");
                            // Take one more sample and pick the better one
                            requestQuickFix(client, context, callback, location);
                        } else {
                            Log.d(TAG, "[Teacher] No fresh cache, requesting quick fix...");
                            requestQuickFix(client, context, callback, null);
                        }
                    })
                    .addOnFailureListener(e ->
                            requestQuickFix(client, context, callback, null));
        } catch (SecurityException e) {
            callback.onFailure("permission_denied");
        }
    }

    /**
     * Requests at most 2 location samples over 4 seconds, returns the more precise.
     */
    private static void requestQuickFix(FusedLocationProviderClient client,
                                        Context context,
                                        LocationCallback callback,
                                        Location cachedLocation) {
        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2_000)
                .setMinUpdateIntervalMillis(1_000)
                .setMaxUpdates(2)
                .setDurationMillis(4_000L)
                .build();

        final Location[] bestFix = {cachedLocation};
        final boolean[] callbackFired = {false};

        com.google.android.gms.location.LocationCallback gmsCallback =
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult result) {
                        for (Location loc : result.getLocations()) {
                            Log.d(TAG, "[Teacher] Sample: accuracy=" + loc.getAccuracy() + "m");
                            if (bestFix[0] == null || !bestFix[0].hasAccuracy()
                                    || (loc.hasAccuracy()
                                    && loc.getAccuracy() < bestFix[0].getAccuracy())) {
                                bestFix[0] = loc;
                            }
                        }
                    }
                };

        // After 5 seconds (4s + 1s grace), return whatever we have
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            client.removeLocationUpdates(gmsCallback);
            if (!callbackFired[0]) {
                callbackFired[0] = true;
                if (bestFix[0] != null) {
                    Log.d(TAG, "[Teacher] Quick fix done: accuracy="
                            + bestFix[0].getAccuracy() + "m");
                    callback.onSuccess(bestFix[0]);
                } else {
                    callback.onFailure("timeout");
                }
            }
        }, 5_000L);

        try {
            client.requestLocationUpdates(request, gmsCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onFailure("permission_denied");
        }
    }

    // =======================================================================
    // 2. STUDENT — Multi-sample, returns ALL locations
    // =======================================================================

    /**
     * Collects up to 2 GPS samples over 4 seconds and returns ALL of them.
     * ScanQRActivity uses these to find the one nearest to the teacher.
     * Reduced from 5 samples/12s to 2 samples/4s to speed up QR scan validation.
     *
     * Also provides a single-location convenience via fetchCurrentLocation().
     *
     * @param context  Activity context
     * @param callback Receives the full list of collected locations
     */
    public static void fetchAllLocations(@NonNull Context context,
                                         @NonNull MultiLocationCallback callback) {
        if (!hasLocationPermission(context)) {
            callback.onFailure("permission_denied");
            return;
        }

        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(context);

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 1_500)
                .setMinUpdateIntervalMillis(1_000)
                .setMaxUpdates(2)           // Only 2 samples — fast!
                .setDurationMillis(4_000L)  // Max 4 seconds
                .build();

        final List<Location> allLocations = new ArrayList<>();
        final boolean[] callbackFired = {false};

        com.google.android.gms.location.LocationCallback gmsCallback =
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(@NonNull LocationResult result) {
                        for (Location loc : result.getLocations()) {
                            allLocations.add(loc);
                            Log.d(TAG, "[Student] Sample #" + allLocations.size()
                                    + ": accuracy=" + loc.getAccuracy() + "m"
                                    + ", lat=" + loc.getLatitude()
                                    + ", lng=" + loc.getLongitude());
                        }
                    }
                };

        // After 5 seconds (4s + 1s grace), return all collected locations
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            client.removeLocationUpdates(gmsCallback);
            if (!callbackFired[0]) {
                callbackFired[0] = true;
                if (!allLocations.isEmpty()) {
                    Log.d(TAG, "[Student] Collection done: " + allLocations.size() + " samples");
                    callback.onLocationsCollected(allLocations);
                } else {
                    Log.w(TAG, "[Student] No locations collected.");
                    callback.onFailure("timeout");
                }
            }
        }, 5_000L);

        try {
            // Also try to include the cached location for instant response
            client.getLastLocation().addOnSuccessListener(location -> {
                if (location != null && isFresh(location)) {
                    allLocations.add(location);
                    Log.d(TAG, "[Student] Added cached location: accuracy="
                            + location.getAccuracy() + "m");
                }
            });

            client.requestLocationUpdates(request, gmsCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onFailure("permission_denied");
        }
    }

    // =======================================================================
    // 3. Legacy single-location fetch (used by pre-fetch in ScanQRActivity)
    // =======================================================================

    /**
     * Single-location convenience method. Returns the best of multiple samples.
     * Used by the pre-fetch mechanism in ScanQRActivity.
     */
    public static void fetchCurrentLocation(@NonNull Context context,
                                            @NonNull LocationCallback callback) {
        fetchAllLocations(context, new MultiLocationCallback() {
            @Override
            public void onLocationsCollected(List<Location> locations) {
                // Pick the most accurate one
                Location best = null;
                for (Location loc : locations) {
                    if (best == null || !best.hasAccuracy()
                            || (loc.hasAccuracy() && loc.getAccuracy() < best.getAccuracy())) {
                        best = loc;
                    }
                }
                if (best != null) {
                    callback.onSuccess(best);
                } else {
                    callback.onFailure("unavailable");
                }
            }

            @Override
            public void onFailure(String reason) {
                callback.onFailure(reason);
            }
        });
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
     */
    public static float distanceBetweenMeters(double lat1, double lon1,
                                              double lat2, double lon2) {
        float[] results = new float[1];
        Location.distanceBetween(lat1, lon1, lat2, lon2, results);
        return results[0];
    }
}