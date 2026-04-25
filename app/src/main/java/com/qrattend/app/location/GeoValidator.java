package com.qrattend.app.location;

import android.location.Location;

import com.qrattend.app.utils.Constants;
import com.qrattend.app.utils.ValidationResult;
import com.qrattend.app.location.LocationHelper;

/**
 * GeoValidator — Member 2 (Core Logic & QR Lead)
 *
 * PRD Layer 2 — Geolocation Validation:
 *   ✅ Circular geofence check against classroom GPS coordinates
 *   ✅ Mock location detection via Fused Location Provider flag (PRD tech stack)
 *   ✅ Location staleness check (< 30 seconds)
 *   ✅ geofenceRadiusM is double to match AttendanceSession.getGeofenceRadius()
 *   ✅ Default radius uses Constants.DEFAULT_GEOFENCE_RADIUS (100.0 m)
 *   ✅ Rejection uses Constants.REASON_LOCATION_MISMATCH
 *
 * Integration with Member 3:
 *   - classroomLat  → AttendanceSession.getLatitude()
 *   - classroomLng  → AttendanceSession.getLongitude()
 *   - geofenceRadius → AttendanceSession.getGeofenceRadius()
 *   All fetched by SessionRepository.getSession() (Member 3).
 */
public class GeoValidator {

    // -----------------------------------------------------------------------
    // Main validation entry point
    // -----------------------------------------------------------------------

    /**
     * Validates whether the student is within the classroom geofence.
     *
     * @param studentLocation  Fresh Location from LocationHelper (Fused Location Provider)
     * @param classroomLat     AttendanceSession.getLatitude()
     * @param classroomLng     AttendanceSession.getLongitude()
     * @param geofenceRadiusM  AttendanceSession.getGeofenceRadius() — double, admin configurable
     * @return                 ValidationResult with Constants rejection reason if failed
     */
    public static ValidationResult validate(Location studentLocation,
                                            double classroomLat,
                                            double classroomLng,
                                            double geofenceRadiusM) {
        // 1. Null guard
        if (studentLocation == null) {
            return ValidationResult.fail(Constants.REASON_LOCATION_MISMATCH);
        }

        // 2. Mock location check — must come first
        if (isMockLocation(studentLocation)) {
            return ValidationResult.fail("mock_location");
        }

        // 3. Staleness check — location fix must be reasonably fresh.
        //    Relaxed from 30s → 60s to accommodate pre-fetched locations
        //    (location starts collecting when camera opens, may be 30-40s old by scan time).
        long ageMs = System.currentTimeMillis() - studentLocation.getTime();
        if (ageMs < 0 || ageMs > 60_000L) {
            return ValidationResult.fail("location_stale");
        }

        // 4. Accuracy sanity check — reject truly unusable fixes.
        //    Threshold lowered from 500m → Constants.MAX_ACCEPTABLE_ACCURACY (200m).
        //    A ±200m+ fix is too noisy for meaningful geofence validation.
        if (studentLocation.hasAccuracy()
                && studentLocation.getAccuracy() > Constants.MAX_ACCEPTABLE_ACCURACY) {
            return ValidationResult.fail("location_inaccurate");
        }

        // 5. Geofence distance check.
        //    Add the student's GPS accuracy as a CAPPED buffer so that a student
        //    physically inside the classroom isn't rejected due to indoor GPS drift,
        //    but the effective geofence can never balloon beyond a reasonable limit.
        //    Buffer is capped at Constants.MAX_ACCURACY_BUFFER (50m).
        //    Example: accuracy=40m → buffer=40m, accuracy=150m → buffer=50m (capped).
        float distance = LocationHelper.distanceBetweenMeters(
                studentLocation.getLatitude(),
                studentLocation.getLongitude(),
                classroomLat,
                classroomLng);

        float accuracyBuffer = studentLocation.hasAccuracy()
                ? Math.min(studentLocation.getAccuracy(), Constants.MAX_ACCURACY_BUFFER)
                : 0f;

        if (distance > geofenceRadiusM + accuracyBuffer) {
            return ValidationResult.fail(Constants.REASON_LOCATION_MISMATCH);
        }

        return ValidationResult.pass();
    }

    /**
     * Overload using Constants.DEFAULT_GEOFENCE_RADIUS (20.0 m).
     * Used when AttendanceSession.geofenceRadius is not set.
     */
    public static ValidationResult validate(Location studentLocation,
                                            double classroomLat,
                                            double classroomLng) {
        return validate(studentLocation, classroomLat, classroomLng,
                Constants.DEFAULT_GEOFENCE_RADIUS);
    }

    // -----------------------------------------------------------------------
    // Mock location detection (merged from MockLocationDetector — PRD §12)
    // Uses Fused Location Provider's own flag — consistent with PRD tech stack
    // -----------------------------------------------------------------------

    public static boolean isMockLocation(Location location) {
        if (location == null) return true;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return location.isMock();
        } else {
            return location.isFromMockProvider();
        }
    }

    // -----------------------------------------------------------------------
    // Helper for UI (Member 1)
    // -----------------------------------------------------------------------

    /**
     * Returns distance in metres from student to classroom.
     * Useful for "You are X m away from class" error messages in ScanQRActivity.
     */
    public static float getDistanceToClassroom(Location studentLocation,
                                               double classroomLat,
                                               double classroomLng) {
        return LocationHelper.distanceBetweenMeters(
                studentLocation.getLatitude(),
                studentLocation.getLongitude(),
                classroomLat,
                classroomLng);
    }
}