package com.qrattend.app.security;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.qrattend.app.utils.Constants;
import com.qrattend.app.utils.ValidationResult;

import java.io.File;
import java.security.MessageDigest;

/**
 * DeviceFingerprint — Member 2 (Core Logic & QR Lead)
 *
 * Collects hardware and software signals to build a stable device identifier.
 * Also detects emulators and rooted devices.
 *
 * PRD Layer 1 — Device Fingerprinting signals:
 *   ✅ ANDROID_ID
 *   ✅ Hardware serial / Build fingerprint
 *   ✅ Installed app signature hash
 *   ✅ Screen resolution + density
 *   ✅ Root / emulator detection (merged from IntegrityChecker per PRD §12)
 *
 * Integration with Member 3:
 *   - The fingerprint is compared against Student.getDeviceId() and
 *     Student.getDeviceId2() fetched by StudentRepository.getStudent()
 *   - PRD allows Constants.MAX_DEVICES (2) per student
 *   - On first login, StudentRepository.updateDeviceId() binds the device
 *   - Rejection uses Constants.REASON_ROOT_DETECTED and
 *     Constants.REASON_DEVICE_MISMATCH
 */
public class DeviceFingerprint {

    // -----------------------------------------------------------------------
    // Fingerprint generation
    // -----------------------------------------------------------------------

    /**
     * Builds a composite device fingerprint by hashing multiple hardware signals.
     * Stable across app launches, unique per physical device.
     *
     * @param context Application or Activity context
     * @return        SHA-256 hex string (64 chars)
     */
    public static String getFingerprint(Context context) {
        String combined = getAndroidId(context)
                + "|" + getBuildFingerprint()
                + "|" + getScreenSignature(context)
                + "|" + getAppSignatureHash(context);
        return sha256(combined);
    }

    // -----------------------------------------------------------------------
    // Individual signal collectors
    // -----------------------------------------------------------------------

    private static String getAndroidId(Context context) {
        String id = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id != null ? id : "unknown_android_id";
    }

    private static String getBuildFingerprint() {
        return Build.FINGERPRINT + "|" + Build.HARDWARE + "|" + Build.DEVICE;
    }

    private static String getScreenSignature(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) wm.getDefaultDisplay().getMetrics(dm);
        return dm.widthPixels + "x" + dm.heightPixels + "@" + dm.densityDpi;
    }

    @SuppressWarnings("deprecation")
    private static String getAppSignatureHash(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                android.content.pm.SigningInfo si = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(),
                                PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                android.content.pm.Signature[] sigs = si.getApkContentsSigners();
                if (sigs != null && sigs.length > 0)
                    return sha256(new String(sigs[0].toByteArray()));
            } else {
                android.content.pm.Signature[] sigs = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(),
                                PackageManager.GET_SIGNATURES).signatures;
                if (sigs != null && sigs.length > 0)
                    return sha256(new String(sigs[0].toByteArray()));
            }
        } catch (PackageManager.NameNotFoundException e) { /* no-op */ }
        return "unknown_sig";
    }

    // -----------------------------------------------------------------------
    // Integrity check — root / emulator (merged from IntegrityChecker — PRD §12)
    // -----------------------------------------------------------------------

    /**
     * Checks device integrity before running fingerprint comparison.
     * Uses Constants.REASON_ROOT_DETECTED for both emulator and rooted device
     * since Constants does not define a separate emulator reason.
     *
     * Called by ProxyDetectionEngine as the first step of Layer 1.
     */
    public static ValidationResult checkDeviceIntegrity(Context context) {
        if (isEmulator() || isRooted()) {
            return ValidationResult.fail(Constants.REASON_ROOT_DETECTED);
        }
        return ValidationResult.pass();
    }

    /** Heuristic emulator detection via Build properties. */
    public static boolean isEmulator() {
        String fp           = Build.FINGERPRINT.toLowerCase();
        String model        = Build.MODEL.toLowerCase();
        String brand        = Build.BRAND.toLowerCase();
        String device       = Build.DEVICE.toLowerCase();
        String product      = Build.PRODUCT.toLowerCase();
        String manufacturer = Build.MANUFACTURER.toLowerCase();

        return fp.contains("generic") || fp.contains("unknown") || fp.startsWith("robolectric")
                || model.contains("google_sdk") || model.contains("emulator")
                || model.contains("android sdk built for")
                || brand.startsWith("generic") || brand.equals("android")
                || device.contains("generic") || device.contains("vbox")
                || device.contains("goldfish")
                || product.contains("sdk") || product.contains("genymotion")
                || product.contains("vbox")
                || manufacturer.contains("genymotion") || manufacturer.equals("unknown");
    }

    /** Checks for su binaries, Magisk, SuperSU, test-keys build. */
    public static boolean isRooted() {
        String buildTags = Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) return true;

        String[] rootPaths = {
                "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
                "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
                "/su/bin/su", "/system/xbin/busybox", "/data/adb/magisk"
        };
        for (String path : rootPaths) {
            if (new File(path).exists()) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Hashing utility
    // -----------------------------------------------------------------------

    static String sha256(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}