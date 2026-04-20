package com.qrattend.app.security;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AESCryptoUtil — Member 2 (Core Logic & QR Lead)
 *
 * Provides AES-256-GCM encryption and decryption for QR payloads.
 *
 * Integration with Member 3:
 *   - Teacher side: generateSessionKey() is called once when StartSessionActivity
 *     creates an AttendanceSession. The key is stored in AttendanceSession.sessionKey
 *     field and written to Firestore by SessionRepository.createSession().
 *   - Student side: ScanQRActivity fetches the session via
 *     SessionRepository.getSession() and passes session.getSessionKey()
 *     to QRScannerUtil for decryption.
 *
 * Output format (Base64 of): [12-byte IV | ciphertext + 16-byte Auth Tag]
 */
public class AESCryptoUtil {

    private static final String ALGORITHM   = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH  = 12;   // 96-bit IV recommended for GCM
    private static final int GCM_TAG_BITS   = 128;  // 128-bit authentication tag
    private static final int KEY_SIZE_BITS  = 256;

    // -----------------------------------------------------------------------
    // Key generation
    // -----------------------------------------------------------------------

    /**
     * Generates a fresh AES-256 session key.
     * Called once per session in StartSessionActivity (Member 1).
     * Result stored in AttendanceSession and written to Firestore by
     * SessionRepository.createSession() (Member 3).
     *
     * @return Base64-encoded key string safe to store in Firestore
     */
    public static String generateSessionKey() throws Exception {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(KEY_SIZE_BITS, new SecureRandom());
        SecretKey key = kg.generateKey();
        return Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP);
    }

    /**
     * Reconstructs a SecretKey from its Base64 string form.
     */
    public static SecretKey decodeKey(String base64Key) {
        byte[] keyBytes = Base64.decode(base64Key, Base64.NO_WRAP);
        return new SecretKeySpec(keyBytes, "AES");
    }

    // -----------------------------------------------------------------------
    // Encryption
    // -----------------------------------------------------------------------

    /**
     * Encrypts plaintext using AES-256-GCM.
     *
     * @param plaintext  JSON payload string from QRGeneratorUtil.QRPayload.toJson()
     * @param base64Key  Key from AttendanceSession.getSessionKey()
     * @return           Base64-encoded: [IV (12B) | ciphertext + auth tag]
     */
    public static String encrypt(String plaintext, String base64Key) throws Exception {
        SecretKey key = decodeKey(base64Key);

        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Prepend IV so receiver can extract it
        byte[] combined = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

        // Use URL-safe Base64 WITHOUT padding:
        // Standard Base64 uses +, /, = which QR scanners can misread.
        return Base64.encodeToString(combined, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    // -----------------------------------------------------------------------
    // Decryption
    // -----------------------------------------------------------------------

    /**
     * Decrypts a QR payload.
     *
     * @param encryptedBase64  Base64 string produced by encrypt()
     * @param base64Key        Same key used during encryption
     * @return                 Original plaintext JSON string
     * @throws Exception       If decryption fails (wrong key / tampered data)
     */
    public static String decrypt(String encryptedBase64, String base64Key) throws Exception {
        // Accept both URL-safe (new) and standard (legacy) Base64
        byte[] combined;
        try {
            // First try URL-safe decoder, tolerant of missing padding
            combined = Base64.decode(encryptedBase64, Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            // Fallback to standard Base64 if needed
            combined = Base64.decode(encryptedBase64, Base64.DEFAULT);
        }

        byte[] iv         = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        SecretKey key = decodeKey(base64Key);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------------
    // Convenience
    // -----------------------------------------------------------------------

    /**
     * Quick check: returns false if the payload cannot be decrypted with the given key.
     * Used by ProxyDetectionEngine to catch tampered QR codes fast.
     */
    public static boolean isDecryptable(String encryptedBase64, String base64Key) {
        try {
            decrypt(encryptedBase64, base64Key);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}