package com.qrattend.app.qr;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.qrattend.app.security.AESCryptoUtil;
import com.qrattend.app.security.NonceManager;
import com.qrattend.app.utils.Constants;



import java.util.EnumMap;
import java.util.Map;

/**
 * QRGeneratorUtil — Member 2 (Core Logic & QR Lead)
 *
 * PRD §5.1 — Dynamic QR Code Generation (Teacher Side):
 *   ✅ Payload: encrypted JSON { session_id, timestamp, nonce, teacher_id, course_id, geo_hash }
 *   ✅ Encryption: AES-256-GCM via AESCryptoUtil
 *   ✅ QR bitmap size: Constants.QR_SIZE (800 px)
 *   ✅ Refresh interval: Constants.QR_REFRESH_INTERVAL_MS (10 000 ms)
 *   ✅ Called every 10 seconds by QRRefreshManager
 *
 * Integration with Member 3:
 *   - sessionId   → AttendanceSession.getSessionId()
 *   - teacherId   → AuthManager.getCurrentUserId()
 *   - courseId    → AttendanceSession.getClassId()
 *   - sessionKey  → AttendanceSession.getSessionKey() via SessionRepository.getSession()
 *   - geoHash     → buildGeoHash(session.getLatitude(), session.getLongitude())
 */
public class QRGeneratorUtil {

    private static final int QR_MARGIN = 1;

    // -----------------------------------------------------------------------
    // QR Payload model
    // -----------------------------------------------------------------------

    /** Typed wrapper around the QR JSON payload. */
    public static class QRPayload {
        public final String sessionId;
        public final long   timestamp;
        public final String nonce;
        public final String teacherId;
        public final String courseId;
        public final String geoHash;

        public QRPayload(String sessionId, long timestamp, String nonce,
                         String teacherId, String courseId, String geoHash) {
            this.sessionId = sessionId;
            this.timestamp = timestamp;
            this.nonce     = nonce;
            this.teacherId = teacherId;
            this.courseId  = courseId;
            this.geoHash   = geoHash;
        }

        /** Serialise to JSON for AES encryption. */
        public String toJson() {
            return "{\"session_id\":\"" + sessionId + "\"" +
                    ",\"timestamp\":"    + timestamp +
                    ",\"nonce\":\""      + nonce + "\"" +
                    ",\"teacher_id\":\"" + teacherId + "\"" +
                    ",\"course_id\":\""  + courseId + "\"" +
                    ",\"geo_hash\":\""   + geoHash + "\"}";
        }

        /** Deserialise from JSON after AES decryption. */
        public static QRPayload fromJson(String json) {
            return new QRPayload(
                    extract(json, "session_id"),
                    Long.parseLong(extract(json, "timestamp")),
                    extract(json, "nonce"),
                    extract(json, "teacher_id"),
                    extract(json, "course_id"),
                    extract(json, "geo_hash")
            );
        }
        private static String extract(String json, String key) {
            String search = "\"" + key + "\":";
            int start = json.indexOf(search) + search.length();
            if (json.charAt(start) == '"') {
                start++;
                return json.substring(start, json.indexOf('"', start));
            } else {
                int end = json.indexOf(',', start);
                if (end == -1) end = json.indexOf('}', start);
                return json.substring(start, end).trim();
            }
        }

    }

    // -----------------------------------------------------------------------
    // QR generation
    // -----------------------------------------------------------------------

    /**
     * Builds an encrypted QR code Bitmap for display on the teacher's screen.
     * Bitmap size uses Constants.QR_SIZE (800 px).
     *
     * @param sessionId   AttendanceSession.getSessionId()
     * @param teacherId   AuthManager.getCurrentUserId() (Member 3)
     * @param courseId    AttendanceSession.getClassId()
     * @param nonce       NonceManager.generateNonce() — fresh every 10s
     * @param geoHash     buildGeoHash(session.getLatitude(), session.getLongitude())
     * @param base64Key   AttendanceSession.getSessionKey() via SessionRepository
     * @return            Bitmap for ImageView in DisplayQRActivity (Member 1)
     */
    public static Bitmap generateQRBitmap(String sessionId,
                                          String teacherId,
                                          String courseId,
                                          String nonce,
                                          String geoHash,
                                          String base64Key) throws Exception {
        long timestamp = NonceManager.currentTimestamp();
        QRPayload payload = new QRPayload(sessionId, timestamp, nonce,
                teacherId, courseId, geoHash);
        String encrypted = AESCryptoUtil.encrypt(payload.toJson(), base64Key);
        return encodeToQRBitmap(encrypted);
    }

    /**
     * Encodes a string into a QR code Bitmap using ZXing.
     * Size determined by Constants.QR_SIZE.
     */
    public static Bitmap encodeToQRBitmap(String content) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, QR_MARGIN);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        QRCodeWriter writer = new QRCodeWriter();
        // Use Constants.QR_SIZE (800) for both dimensions
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE,
                Constants.QR_SIZE, Constants.QR_SIZE, hints);

        int width  = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    // -----------------------------------------------------------------------
    // Geo hash helper
    // -----------------------------------------------------------------------

    /**
     * Produces a coarse geo-hash from AttendanceSession.getLatitude/getLongitude().
     * Region tag (~1.1 km precision) prevents QR replay from another campus.
     *
     * @param latitude  AttendanceSession.getLatitude()
     * @param longitude AttendanceSession.getLongitude()
     */
    public static String buildGeoHash(double latitude, double longitude) {
        long latRound = Math.round(latitude  * 100);
        long lngRound = Math.round(longitude * 100);
        return latRound + "_" + lngRound;
    }
}