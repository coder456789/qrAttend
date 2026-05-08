package com.qrattend.app;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.qrattend.app.location.LocationHelper;
import com.qrattend.app.qr.QRGeneratorUtil;
import com.qrattend.app.qr.QRScannerUtil;

public class ScanTestActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView resultText;
    private QRScannerUtil scanner;
    private String sessionKey;
    private ProgressDialog progressDialog;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    checkLocationPermissionAndStart();
                } else {
                    resultText.setText("Camera permission denied");
                }
            });

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    startScanner();
                } else {
                    resultText.setText("Location permission denied");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_test);

        previewView = findViewById(R.id.previewView);
        resultText = findViewById(R.id.resultText);

        resultText.setText("Opening scanner...");

        sessionKey = getIntent().getStringExtra("sessionKey");

        if (sessionKey == null || sessionKey.isEmpty()) {
            resultText.setText("Session key missing");
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            checkLocationPermissionAndStart();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void checkLocationPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void startScanner() {
        resultText.setText("Point camera at QR...");

        scanner = new QRScannerUtil(this, previewView, this, sessionKey);
        scanner.startScanning(new QRScannerUtil.ScanCallback() {
            @Override
            public void onPayloadDecoded(QRGeneratorUtil.QRPayload payload) {
                showPopupWithQrAndLocation(payload);
            }

            @Override
            public void onError(String reason) {
                if (isFinishing() || isDestroyed()) return;
                resultText.setText("Error: " + reason);
            }
        });
    }

    private void showPopupWithQrAndLocation(QRGeneratorUtil.QRPayload payload) {
        if (isFinishing() || isDestroyed()) return;

        // Visual feedback for the long location fetch (12-14 seconds)
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("QR Scanned! Fetching precise location...\nThis may take up to 15 seconds.");
        progressDialog.setCancelable(false);
        progressDialog.show();

        LocationHelper.fetchCurrentLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onSuccess(Location location) {
                if (isFinishing() || isDestroyed()) return;
                if (progressDialog != null) progressDialog.dismiss();

                String message =
                        "QR SCANNED\n\n" +
                                "session_id: " + payload.sessionId + "\n" +
                                "teacher_id: " + payload.teacherId + "\n" +
                                "course_id: " + payload.courseId + "\n" +
                                "nonce: " + payload.nonce + "\n" +
                                "geo_hash: " + payload.geoHash + "\n" +
                                "timestamp: " + payload.timestamp + "\n\n" +
                                "YOUR LOCATION\n\n" +
                                "Latitude: " + location.getLatitude() + "\n" +
                                "Longitude: " + location.getLongitude();

                new AlertDialog.Builder(ScanTestActivity.this)
                        .setTitle("Scan Result")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .show();
            }

            @Override
            public void onFailure(String reason) {
                if (isFinishing() || isDestroyed()) return;
                if (progressDialog != null) progressDialog.dismiss();

                String message =
                        "QR SCANNED\n\n" +
                                "session_id: " + payload.sessionId + "\n" +
                                "teacher_id: " + payload.teacherId + "\n" +
                                "course_id: " + payload.courseId + "\n" +
                                "nonce: " + payload.nonce + "\n" +
                                "geo_hash: " + payload.geoHash + "\n" +
                                "timestamp: " + payload.timestamp + "\n\n" +
                                "Location fetch failed: " + reason;

                new AlertDialog.Builder(ScanTestActivity.this)
                        .setTitle("Scan Result")
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton("OK", (dialog, which) -> finish())
                        .show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Release camera resources immediately when not in focus
        if (scanner != null) {
            scanner.stopScanning();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Optionally restart scanning if it was stopped
        if (scanner != null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanner();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (scanner != null) {
            scanner.stopScanning();
        }
    }
}
