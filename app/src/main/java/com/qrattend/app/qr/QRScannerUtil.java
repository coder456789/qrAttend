package com.qrattend.app.qr;

import android.content.Context;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.qrattend.app.security.AESCryptoUtil;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * QRScannerUtil — Member 2 (Core Logic & QR Lead)
 *
 * PRD §5.2 — QR Code Scanning (Student Side):
 *   ✅ Camera preview via CameraX
 *   ✅ Barcode scanning via Google ML Kit
 *   ✅ AES-256-GCM decryption via AESCryptoUtil
 *   ✅ Returns typed QRPayload for ProxyDetectionEngine
 *
 * Integration with Member 3:
 *   - sessionKey comes from AttendanceSession.getSessionKey()
 *     fetched by SessionRepository.getSession() in ScanQRActivity
 *   - On successful decode, ScanQRActivity calls ProxyDetectionEngine.validate()
 *     passing the payload + AttendanceSession + Student objects
 *   - On pass, AttendanceRepository.markAttendance() is called with an
 *     AttendanceRecord built using Constants.STATUS_PRESENT / STATUS_REJECTED
 *
 * Usage (in ScanQRActivity — Member 1):
 *   QRScannerUtil scanner = new QRScannerUtil(context, previewView,
 *       lifecycleOwner, session.getSessionKey());
 *   scanner.startScanning(new QRScannerUtil.ScanCallback() {
 *       public void onPayloadDecoded(QRGeneratorUtil.QRPayload payload) { ... }
 *       public void onError(String reason) { ... }
 *   });
 *   // In onDestroy():
 *   scanner.stopScanning();
 */
public class QRScannerUtil {

    // -----------------------------------------------------------------------
    // Callback interface
    // -----------------------------------------------------------------------

    public interface ScanCallback {
        void onPayloadDecoded(QRGeneratorUtil.QRPayload payload);
        void onError(String reason); // "qr_decrypt_fail" | "parse_fail" | "camera_unavailable"
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final Context        context;
    private final PreviewView    previewView;
    private final LifecycleOwner lifecycleOwner;
    private final Handler        mainHandler = new Handler(Looper.getMainLooper());

    /**
     * AES session key from AttendanceSession.getSessionKey()
     * fetched by Member 3's SessionRepository.getSession().
     */
    private final String         sessionKey;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private       BarcodeScanner  barcodeScanner;
    private       boolean         scanHandled    = false;
    private       androidx.camera.core.Camera camera;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    public QRScannerUtil(Context context, PreviewView previewView,
                         LifecycleOwner lifecycleOwner, String sessionKey) {
        this.context        = context;
        this.previewView    = previewView;
        this.lifecycleOwner = lifecycleOwner;
        this.sessionKey     = sessionKey;
    }

    /** Returns the bound Camera instance (available after startScanning). */
    public androidx.camera.core.Camera getCamera() {
        return camera;
    }

    // -----------------------------------------------------------------------
    // Camera setup
    // -----------------------------------------------------------------------

    public void startScanning(ScanCallback callback) {
        scanHandled = false;

        barcodeScanner = BarcodeScanning.getClient(
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build());

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(context);

        future.addListener(() -> {
            try {
                bindCamera(future.get(), callback);
            } catch (Exception e) {
                callback.onError("camera_unavailable");
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCamera(ProcessCameraProvider cameraProvider, ScanCallback callback) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, ip -> analyzeFrame(ip, callback));

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
    }

    // -----------------------------------------------------------------------
    // Frame analysis
    // -----------------------------------------------------------------------

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(ImageProxy imageProxy, ScanCallback callback) {
        if (scanHandled) { imageProxy.close(); return; }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) { imageProxy.close(); return; }

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(cameraExecutor, barcodes -> {
                    for (Barcode barcode : barcodes) {
                        String raw = barcode.getRawValue();
                        if (raw != null && !scanHandled) handleRawQR(raw, callback);
                    }
                })
                .addOnFailureListener(e -> { /* keep scanning */ })
                .addOnCompleteListener(t -> imageProxy.close());
    }

    // -----------------------------------------------------------------------
    // Decrypt and parse
    // -----------------------------------------------------------------------

    private void handleRawQR(String encryptedPayload, ScanCallback callback) {
        String json;
        try {
            json = AESCryptoUtil.decrypt(encryptedPayload, sessionKey);
            Log.d("QRScan", "Decryption SUCCESS. JSON length=" + json.length());
        } catch (Exception e) {
            Log.e("QRScan", "Decryption FAILED. reason=" + e.getClass().getSimpleName()
                    + " msg=" + e.getMessage()
                    + " keyLen=" + (sessionKey != null ? sessionKey.length() : "null")
                    + " payloadLen=" + encryptedPayload.length());
            if (!scanHandled) { 
                scanHandled = true; 
                final String errorMsg = "qr_decrypt_fail: " + e.getMessage() + " keyLen=" + (sessionKey != null ? sessionKey.length() : "null");
                mainHandler.post(() -> callback.onError(errorMsg));
            }
            return;
        }

        try {
            QRGeneratorUtil.QRPayload payload = QRGeneratorUtil.QRPayload.fromJson(json);
            Log.d("QRScan", "Parse SUCCESS. sessionId=" + payload.sessionId);
            scanHandled = true;
            mainHandler.post(() -> callback.onPayloadDecoded(payload));
        } catch (Exception e) {
            Log.e("QRScan", "Parse FAILED. json=" + json + " err=" + e.getMessage());
            if (!scanHandled) { 
                scanHandled = true; 
                mainHandler.post(() -> callback.onError("parse_fail")); 
            }
        }
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    /** Call from onDestroy() in ScanQRActivity. */
    public void stopScanning() {
        if (barcodeScanner != null) barcodeScanner.close();
        cameraExecutor.shutdown();
    }
}
