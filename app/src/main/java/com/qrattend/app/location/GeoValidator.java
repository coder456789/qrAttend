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
 *   ✅ Default radius uses Constants.DEFAULT_GEOFENCE_RADIUS (50.0 m)
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

        // 3. Staleness check — location fix must be < 30 seconds old per PRD
        if (!LocationHelper.isFresh(studentLocation)) {
            return ValidationResult.fail(Constants.REASON_LOCATION_MISMATCH);
        }

        // 4. Accuracy sanity check
        if (studentLocation.hasAccuracy() && studentLocation.getAccuracy() > 100f) {
            return ValidationResult.fail(Constants.REASON_LOCATION_MISMATCH);
        }

        // 5. Geofence distance check against Constants.DEFAULT_GEOFENCE_RADIUS (50m)
        float distance = LocationHelper.distanceBetweenMeters(
                studentLocation.getLatitude(),
                studentLocation.getLongitude(),
                classroomLat,
                classroomLng);

        if (distance > geofenceRadiusM) {
            return ValidationResult.fail(Constants.REASON_LOCATION_MISMATCH);
        }

        return ValidationResult.pass();
    }

    /**
     * Overload using Constants.DEFAULT_GEOFENCE_RADIUS (50.0 m).
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