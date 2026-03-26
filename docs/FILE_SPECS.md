# QR-Attend — File Specification Guide
### What Each File Must Contain & Do

> This document describes every file in the repo (original 80-file structure).
> Use it as a reference when coding — each file has its purpose, inputs, outputs, and key methods listed.

---

## 🔵 MEMBER 1 — UI / Frontend (20 Java + 20 XML)

---

### `SplashActivity.java` + `activity_splash.xml`
**Purpose:** App launch screen shown for 2 seconds, then routes to Login or Dashboard.

| What it does | Details |
|---|---|
| Check if user is logged in | `FirebaseAuth.getInstance().getCurrentUser()` |
| If logged in | Read user role from Firestore → route to Student/Teacher/Admin dashboard |
| If not logged in | Go to `LoginActivity` |
| XML | App logo centered, loading spinner, gradient background |

---

### `LoginActivity.java` + `activity_login.xml`
**Purpose:** Email/password login + Google Sign-In button.

| What it does | Details |
|---|---|
| Email/password login | Calls `AuthManager.loginWithEmail(email, password, callback)` |
| Google Sign-In | Calls `AuthManager.loginWithGoogle(activity, callback)` |
| On success | Read role from Firestore → route to correct dashboard |
| On failure | Show error Toast |
| XML | Email EditText, Password EditText, Login Button, Google Sign-In Button, "Sign Up" link |

---

### `SignupActivity.java` + `activity_signup.xml`
**Purpose:** New user registration with role selection.

| What it does | Details |
|---|---|
| Collect fields | Name, email, password, phone, roll number (students), role selector |
| Create account | Calls `AuthManager.signupWithEmail()` → then writes to `students` or `teachers` collection |
| XML | EditTexts for all fields, role RadioGroup (Student/Teacher), Signup Button |

---

### `StudentDashboardActivity.java` + `activity_student_dashboard.xml`
**Purpose:** Main screen for students after login.

| What it shows | Details |
|---|---|
| Overall attendance % | Read all sessions → count present/total → show percentage |
| Subject-wise breakdown | List subjects with individual attendance % |
| "Scan QR" button | Opens `ScanQRActivity` |
| Nav to history | Opens `AttendanceHistoryActivity` |
| XML | Welcome text, percentage card, subject list (RecyclerView), big Scan FAB button |

---

### `AttendanceHistoryActivity.java` + `activity_attendance_history.xml`
**Purpose:** Shows student's past attendance records.

| What it shows | Details |
|---|---|
| List of records | Date, subject, status (present/rejected), time |
| Filter options | By subject, by date range |
| Data source | Query `attendanceSessions` → read `records/{studentId}` subcollection |
| Adapter | `AttendanceRecordAdapter` |
| XML | RecyclerView, filter chips/dropdown at top |

---

### `ScanQRActivity.java` + `activity_scan_qr.xml`
**Purpose:** Camera screen where student scans teacher's QR.

| What it does | Details |
|---|---|
| Open camera | ML Kit `BarcodeScanner` via `QRScannerUtil` |
| On scan success | Decrypt QR payload → extract sessionId + nonce |
| Validate | Call `ProxyDetectionEngine.validate(sessionId, nonce, deviceFP, location)` |
| On pass | Write to `attendanceSessions/{sessionId}/records/{studentId}` via `AttendanceRepository` |
| On fail | Show rejection reason (location mismatch / device mismatch / QR expired) |
| XML | Full-screen camera preview, overlay rectangle, status text at bottom |

---

### `TeacherDashboardActivity.java` + `activity_teacher_dashboard.xml`
**Purpose:** Main screen for teachers after login.

| What it shows | Details |
|---|---|
| List of assigned classes | From `classes` collection where `teacherId == currentUser` |
| "Start Session" button | Opens `StartSessionActivity` with selected class |
| Past sessions list | Recent sessions with date and attendance count |
| XML | Class list (RecyclerView), Start Session FAB, past sessions section |

---

### `StartSessionActivity.java` + `activity_start_session.xml`
**Purpose:** Teacher selects class and starts an attendance session.

| What it does | Details |
|---|---|
| Select class | Dropdown/spinner of teacher's classes |
| Get location | `LocationHelper.getCurrentLocation()` for geofence center |
| Create session | Write new doc to `attendanceSessions` via `SessionRepository` |
| Start QR display | Navigate to `DisplayQRActivity` with sessionId |
| XML | Class selector, current location display, "Start" button |

---

### `DisplayQRActivity.java` + `activity_display_qr.xml`
**Purpose:** Full-screen dynamic QR code that refreshes every 10 seconds.

| What it does | Details |
|---|---|
| Generate QR | `QRGeneratorUtil.generate(encryptedPayload)` → Bitmap |
| Auto-refresh | `QRRefreshManager` triggers new nonce every 10s → new QR |
| Update Firestore | Each refresh updates `attendanceSessions/{sessionId}.qrCode` field |
| Show live count | Real-time listener on `records` subcollection → count documents |
| "End Session" button | Sets `active: false` on session doc |
| XML | Full-screen ImageView (QR), timer countdown, live attendance count, End button |

---

### `SessionAttendanceActivity.java` + `activity_session_attendance.xml`
**Purpose:** Teacher views who attended a specific session.

| What it shows | Details |
|---|---|
| Student list | All docs from `attendanceSessions/{sessionId}/records/` |
| Each row shows | Student name, roll no, time marked, status (present/rejected) |
| Flagged entries | Highlight rejected ones with reason |
| Adapter | `StudentListAdapter` (or `AttendanceRecordAdapter`) |
| XML | RecyclerView, session info header (date, subject, total count) |

---

### `AdminDashboardActivity.java` + `activity_admin_dashboard.xml`
**Purpose:** Admin's main screen with management options.

| What it shows | Details |
|---|---|
| Cards/buttons | "Manage Students", "Manage Teachers", "Manage Classes" |
| Stats overview | Total students, total teachers, total classes |
| XML | Grid of cards with icons and counts |

---

### `ManageStudentsActivity.java` + `activity_manage_students.xml`
**Purpose:** Admin views/adds/edits/deletes students.

| What it does | Details |
|---|---|
| List all students | Read `students` collection |
| Add student | Dialog/form → `StudentRepository.addStudent()` |
| Edit student | Tap row → edit dialog → `StudentRepository.updateStudent()` |
| Delete student | Swipe/long-press → `StudentRepository.deleteStudent()` |
| Adapter | `StudentListAdapter` |
| XML | RecyclerView, FAB "Add Student", search bar |

---

### `ManageTeachersActivity.java` + `activity_manage_teachers.xml`
**Purpose:** Same as ManageStudents but for teachers. Identical pattern.

---

### `ManageClassesActivity.java` + `activity_manage_classes.xml`
**Purpose:** Admin creates/edits class-subject mappings.

| What it does | Details |
|---|---|
| List classes | Read `classes` collection |
| Add class | Form: className, subject, assign teacher (dropdown from `teachers`) |
| Enroll students | Multi-select students to add to `enrolledStudents` array |
| Adapter | `CourseListAdapter` |
| XML | RecyclerView, FAB "Add Class" |

---

### `ProfileActivity.java` + `activity_profile.xml`
**Purpose:** View and edit own profile (name, phone, email).

| What it does | Details |
|---|---|
| Show current info | Read from `students/{uid}` or `teachers/{uid}` |
| Edit fields | Name, phone (email is read-only — Firebase Auth) |
| Save | Update Firestore doc |
| Device info | Show registered deviceId(s) |
| XML | Avatar placeholder, EditTexts, Save button, device info section |

---

### `SettingsActivity.java` + `activity_settings.xml`
**Purpose:** App settings and logout.

| What it shows | Details |
|---|---|
| Notification toggle | Enable/disable push notifications |
| Dark mode toggle | Switch theme |
| About section | App version, developers |
| Logout button | `AuthManager.logout()` → go to LoginActivity |
| XML | List of setting items with toggles, Logout button at bottom |

---

### Adapters

#### `AttendanceRecordAdapter.java` + `item_attendance_record.xml`
| What it renders | One row of attendance history |
|---|---|
| Data | Date, subject name, status (colored badge: green=present, red=rejected), time |
| XML | Horizontal row: date text, subject text, status chip, time text |

#### `StudentListAdapter.java` + `item_student.xml`
| What it renders | One row of student in a list |
|---|---|
| Data | Name, roll number, class, email |
| Click action | Opens edit dialog or detail view |
| XML | Horizontal row: avatar circle, name, roll no, class badge |

#### `CourseListAdapter.java` + `item_course.xml`
| What it renders | One row of class/course |
|---|---|
| Data | Class name, subject, teacher name |
| XML | Card with subject name, class name, teacher chip |

#### `SessionListAdapter.java` + `item_session.xml`
| What it renders | One row of past session |
|---|---|
| Data | Date, subject, attendance count (e.g., "42/60"), status |
| Click action | Opens `SessionAttendanceActivity` |
| XML | Card: date, subject, attendance bar/count |

---

## 🟢 MEMBER 2 — Core Logic (10 Java Files)

---

### `QRGeneratorUtil.java`
**Purpose:** Generate QR code bitmap from encrypted payload using ZXing.

```java
// Key methods:
public class QRGeneratorUtil {

    // Generate QR bitmap from encrypted string
    // Input: encrypted JSON string, width, height
    // Output: Bitmap ready to display in ImageView
    public static Bitmap generateQR(String encryptedPayload, int width, int height);
    
    // Build the payload JSON before encryption
    // Input: sessionId, nonce, teacherId, timestamp
    // Output: JSON string like {"sid":"...","nonce":"...","tid":"...","ts":123456}
    public static String buildPayload(String sessionId, String nonce, String teacherId, long timestamp);
}
```

**Dependencies:** ZXing library (`com.journeyapps:zxing-android-embedded`)

---

### `QRScannerUtil.java`
**Purpose:** Scan and decode QR codes using Google ML Kit.

```java
public class QRScannerUtil {

    // Initialize ML Kit barcode scanner
    public void initialize(Activity activity);
    
    // Start scanning — returns decoded string via callback
    // Input: camera preview SurfaceView
    // Output: raw scanned string (still encrypted)
    public void startScanning(SurfaceView preview, OnQRScannedListener callback);
    
    // Stop camera and release resources
    public void stopScanning();
    
    // Callback interface
    public interface OnQRScannedListener {
        void onScanned(String rawPayload);  // encrypted string from QR
        void onError(String errorMessage);
    }
}
```

**Dependencies:** `com.google.mlkit:barcode-scanning`

---

### `QRRefreshManager.java`
**Purpose:** Timer that generates a new nonce and triggers QR refresh every 10 seconds.

```java
public class QRRefreshManager {

    // Start the 10-second refresh loop
    // Input: sessionId, encryptionKey
    // Action: Every 10s → generate new nonce → encrypt payload → callback with new QR bitmap
    public void startRefreshing(String sessionId, String encryptionKey, OnRefreshListener callback);
    
    // Stop the loop (when teacher ends session)
    public void stopRefreshing();
    
    // Callback
    public interface OnRefreshListener {
        void onNewQR(Bitmap qrBitmap, String currentNonce);  // UI updates the ImageView
    }
}
```

**Implementation:** Uses `Handler` + `Runnable` with `postDelayed(runnable, 10000)`

---

### `AESCryptoUtil.java`
**Purpose:** Encrypt and decrypt QR payload using AES-256-GCM.

```java
public class AESCryptoUtil {

    // Generate a random AES-256 key for this session
    // Output: Base64-encoded key string (store in session doc)
    public static String generateKey();
    
    // Encrypt plaintext payload
    // Input: JSON payload string, key
    // Output: Base64-encoded encrypted string (goes into QR)
    public static String encrypt(String plaintext, String key);
    
    // Decrypt scanned QR data
    // Input: encrypted string from QR, key
    // Output: JSON payload string
    public static String decrypt(String ciphertext, String key);
}
```

**Implementation:** Uses `javax.crypto.Cipher` with `AES/GCM/NoPadding`, random 12-byte IV prepended to ciphertext.

---

### `DeviceFingerprint.java`
**Purpose:** Collect unique device identifiers to prevent proxy attendance.

```java
public class DeviceFingerprint {

    // Collect all device signals and create a hash
    // Output: SHA-256 hash string combining all signals below
    public static String getFingerprint(Context context);
    
    // Individual signals collected:
    // - Settings.Secure.ANDROID_ID
    // - Build.MODEL, Build.MANUFACTURER, Build.BRAND
    // - Build.FINGERPRINT (hardware fingerprint)
    // - Screen resolution (DisplayMetrics)
    // - App signature hash (PackageManager)
    
    // Check if device matches registered fingerprint
    // Input: expected fingerprint from Firestore student doc
    // Output: true if match, false if different device
    public static boolean matches(String expectedFP, Context context);
}
```

---

### `NonceManager.java`
**Purpose:** Generate random one-time nonces and validate scanned nonces.

```java
public class NonceManager {

    // Generate a new random nonce (called every 10s by QRRefreshManager)
    // Output: 16-character random alphanumeric string
    public static String generateNonce();
    
    // Validate if the nonce from scanned QR matches the session's current nonce
    // Input: scanned nonce, session's current qrCode field
    // Output: true if they match (QR is fresh), false if expired/different
    public static boolean isValid(String scannedNonce, String currentSessionNonce);
}
```

---

### `IntegrityChecker.java`
**Purpose:** Detect rooted devices, emulators, and tampered APKs.

```java
public class IntegrityChecker {

    // Check if device is rooted
    // Looks for: su binary, Magisk, SuperSU, system/xbin/su
    public static boolean isRooted();
    
    // Check if running on emulator
    // Checks: Build.FINGERPRINT contains "generic", Build.MODEL contains "Emulator"
    public static boolean isEmulator();
    
    // Check if APK signature is valid (not repackaged)
    // Compares against your known signing certificate hash
    public static boolean isAPKTampered(Context context);
    
    // All-in-one check
    // Returns: true if device is safe, false if any check fails
    public static boolean isDeviceTrusted(Context context);
}
```

---

### `LocationHelper.java`
**Purpose:** Get current GPS location using Fused Location Provider.

```java
public class LocationHelper {

    // Request single location update (high accuracy)
    // Input: activity (for permission check), callback
    // Output: Location object with lat/lng via callback
    public void getCurrentLocation(Activity activity, OnLocationResultListener callback);
    
    // Check if location permissions are granted
    public static boolean hasLocationPermission(Context context);
    
    // Request location permissions
    public static void requestPermission(Activity activity, int requestCode);
    
    public interface OnLocationResultListener {
        void onLocation(double latitude, double longitude);
        void onError(String error);
    }
}
```

**Dependencies:** `com.google.android.gms:play-services-location`

---

### `GeoValidator.java`
**Purpose:** Check if student is within classroom geofence.

```java
public class GeoValidator {

    // Calculate distance between two GPS points (Haversine formula)
    // Input: student lat/lng, classroom lat/lng
    // Output: distance in meters
    public static double distanceInMeters(double lat1, double lon1, double lat2, double lon2);
    
    // Check if student is within geofence
    // Input: student location, session's lat/lng, geofenceRadius
    // Output: true if within radius
    public static boolean isWithinGeofence(double studentLat, double studentLng,
                                            double classLat, double classLng,
                                            double radiusMeters);
}
```

---

### `MockLocationDetector.java`
**Purpose:** Detect if the student is using a fake GPS app.

```java
public class MockLocationDetector {

    // Check if location is from a mock provider
    // Input: Location object
    // Output: true if MOCK (reject attendance)
    public static boolean isMockLocation(Location location);
    
    // Check if any mock location apps are installed
    // Scans installed apps for known fake GPS packages
    public static boolean hasMockAppsInstalled(Context context);
    
    // Combined check
    public static boolean isSpoofed(Location location, Context context);
}
```

---

### `ProxyDetectionEngine.java`
**Purpose:** Orchestrator that combines ALL validation checks and returns accept/reject.

```java
public class ProxyDetectionEngine {

    // Run all proxy checks in sequence
    // Input: scanned QR data, student's device context, student's location
    // Output: ValidationResult (pass/fail + reason)
    public void validate(String scannedNonce, String sessionId,
                         Context context, double studentLat, double studentLng,
                         ValidationCallback callback);
    
    // Validation order:
    // 1. IntegrityChecker.isDeviceTrusted()     → reject if rooted/emulator
    // 2. NonceManager.isValid(scannedNonce, sessionNonce) → reject if QR expired
    // 3. DeviceFingerprint.matches()             → reject if wrong device
    // 4. MockLocationDetector.isSpoofed()        → reject if fake GPS
    // 5. GeoValidator.isWithinGeofence()          → reject if outside classroom
    // 6. ALL PASS → return ValidationResult.SUCCESS
    
    public class ValidationResult {
        public boolean passed;
        public String rejectionReason;  // "", "device_mismatch", "location_mismatch", "nonce_expired", "root_detected"
    }
    
    public interface ValidationCallback {
        void onResult(ValidationResult result);
    }
}
```

---

## 🟠 MEMBER 3 — Backend / Firebase (16 Java + 3 config)

---

### Data Models (POJOs)

All models are simple Java classes with fields matching Firestore document structure. Each has: constructor, getters/setters, default empty constructor (required by Firestore).

#### `Student.java`
```java
public class Student {
    private String name;        // "Rahul Sharma"
    private String rollNo;      // "CS21"
    private String className;   // "TY BSc CS"  (field name: "class" in Firestore)
    private String email;       // "rahul@gmail.com"
    private String phone;       // "9876543210"
    private String deviceId;    // primary device fingerprint hash
    private String deviceId2;   // optional second device
    private String fcmToken;    // for push notifications
    // + empty constructor, getters, setters
}
```

#### `Teacher.java`
```java
public class Teacher {
    private String name;        // "Mr. Patil"
    private String email;       // "patil@college.edu"
    private String subject;     // "Data Structures"
    private String classroom;   // "Room 301"
    private String fcmToken;
}
```

#### `ClassInfo.java`
```java
public class ClassInfo {
    private String className;           // "TY BSc CS"
    private String subject;             // "DSA"
    private String teacherId;           // ref → teachers doc
    private List<String> enrolledStudents;  // array of student doc IDs
}
```

#### `AttendanceSession.java`
```java
public class AttendanceSession {
    private String classId;         // ref → classes doc
    private String teacherId;       // ref → teachers doc
    private String qrCode;          // current nonce (rotated every 10s)
    private double latitude;        // classroom GPS
    private double longitude;
    private double geofenceRadius;  // meters (default 50)
    private Timestamp startTime;
    private Timestamp endTime;
    private boolean active;
}
```

#### `AttendanceRecord.java`
```java
public class AttendanceRecord {
    private String status;              // "present" | "rejected"
    private Timestamp time;
    private String deviceId;            // student's device fingerprint at scan time
    private double deviceLocationLat;
    private double deviceLocationLong;
    private String rejectionReason;     // "" | "location_mismatch" | "device_mismatch" | "nonce_expired"
}
```

#### `NonceLog.java`
```java
// ⚠️ THIS FILE IS NO LONGER NEEDED — nonce validation uses session's qrCode field directly.
// Kept here for documentation only. DO NOT CREATE THIS FILE.
```

---

### Repositories (Firestore CRUD)

Each repository gets a `FirebaseFirestore.getInstance()` reference and performs read/write operations on its collection.

#### `StudentRepository.java`
```java
public class StudentRepository {
    private CollectionReference studentsRef = FirebaseFirestore.getInstance().collection("students");
    
    // Create
    void addStudent(String docId, Student student, OnCompleteListener callback);
    
    // Read one
    void getStudent(String studentId, OnSuccessListener<Student> callback);
    
    // Read all
    void getAllStudents(OnSuccessListener<List<Student>> callback);
    
    // Update
    void updateStudent(String studentId, Map<String, Object> updates, OnCompleteListener callback);
    
    // Update device fingerprint
    void updateDeviceId(String studentId, String newDeviceId, OnCompleteListener callback);
    
    // Delete
    void deleteStudent(String studentId, OnCompleteListener callback);
}
```

#### `TeacherRepository.java`
Same pattern as `StudentRepository` but for `teachers` collection.

#### `ClassRepository.java`
```java
public class ClassRepository {
    // Standard CRUD (same pattern as StudentRepo)
    // + Extra methods:
    
    // Get classes by teacher
    void getClassesByTeacher(String teacherId, OnSuccessListener<List<ClassInfo>> callback);
    
    // Add student to enrolledStudents array
    void enrollStudent(String classId, String studentId, OnCompleteListener callback);
    
    // Remove student from enrolledStudents array
    void unenrollStudent(String classId, String studentId, OnCompleteListener callback);
}
```

#### `SessionRepository.java`
```java
public class SessionRepository {
    private CollectionReference sessionsRef = 
        FirebaseFirestore.getInstance().collection("attendanceSessions");
    
    // Create new session (teacher starts class)
    void createSession(String sessionId, AttendanceSession session, OnCompleteListener callback);
    
    // Get active session for a class
    void getActiveSession(String classId, OnSuccessListener<AttendanceSession> callback);
    
    // Update current nonce (called every 10s by QRRefreshManager)
    void updateNonce(String sessionId, String newNonce, OnCompleteListener callback);
    
    // End session (set active=false, set endTime)
    void endSession(String sessionId, OnCompleteListener callback);
    
    // Get session details
    void getSession(String sessionId, OnSuccessListener<AttendanceSession> callback);
    
    // Real-time listener for live attendance count
    ListenerRegistration listenToAttendanceCount(String sessionId, OnCountChangedListener callback);
}
```

#### `AttendanceRepository.java`
```java
public class AttendanceRepository {
    // Note: records is a SUBCOLLECTION under attendanceSessions
    // Path: attendanceSessions/{sessionId}/records/{studentId}
    
    // Mark attendance (write record with studentId as doc ID)
    void markAttendance(String sessionId, String studentId, AttendanceRecord record, 
                        OnCompleteListener callback);
    
    // Check if student already marked (doc exists?)
    void hasAlreadyMarked(String sessionId, String studentId, OnSuccessListener<Boolean> callback);
    
    // Get all records for a session (teacher view)
    void getSessionRecords(String sessionId, OnSuccessListener<List<AttendanceRecord>> callback);
    
    // Get student's attendance across all sessions (student history)
    // This requires querying multiple sessions — use collectionGroup query
    void getStudentHistory(String studentId, OnSuccessListener<List<AttendanceRecord>> callback);
}
```

#### `NonceRepository.java`
```java
// ⚠️ THIS FILE IS NO LONGER NEEDED — nonce validation moved to SessionRepository.
// The session's qrCode field IS the current nonce. Just compare scanned vs stored.
// DO NOT CREATE THIS FILE.
```

---

### Firebase Services

#### `AuthManager.java`
```java
public class AuthManager {
    private FirebaseAuth auth = FirebaseAuth.getInstance();
    
    // Email/password login
    void loginWithEmail(String email, String password, OnCompleteListener callback);
    
    // Google Sign-In
    void loginWithGoogle(Activity activity, OnCompleteListener callback);
    
    // Email/password signup
    void signupWithEmail(String email, String password, OnCompleteListener callback);
    
    // Logout
    void logout();
    
    // Get current user ID
    String getCurrentUserId();
    
    // Check if logged in
    boolean isLoggedIn();
    
    // Get current user's role (reads from Firestore)
    void getUserRole(OnSuccessListener<String> callback);  // "student" | "teacher" | "admin"
}
```

#### `FCMService.java`
```java
public class FCMService extends FirebaseMessagingService {
    
    // Called when new FCM token is generated
    @Override
    public void onNewToken(String token) {
        // Save token to student/teacher Firestore document
    }
    
    // Called when push notification received
    @Override
    public void onMessageReceived(RemoteMessage message) {
        // Show notification: session started, low attendance alert, proxy detected
    }
    
    // Send notification to specific user (via their fcmToken)
    // Note: this would typically be done via Cloud Function, not client-side
    static void sendNotification(String title, String body, String fcmToken);
}
```

#### `FirestoreHelper.java`
```java
public class FirestoreHelper {
    // Singleton Firestore instance
    public static FirebaseFirestore db = FirebaseFirestore.getInstance();
    
    // Common query: get document by ID from any collection
    public static void getDocument(String collection, String docId, OnSuccessListener callback);
    
    // Common: check if document exists
    public static void exists(String collection, String docId, OnSuccessListener<Boolean> callback);
    
    // Timestamp helper
    public static Timestamp now();
}
```

---

### Utils (Shared)

#### `Constants.java`
```java
public class Constants {
    // Firestore collection names
    public static final String STUDENTS = "students";
    public static final String TEACHERS = "teachers";
    public static final String CLASSES = "classes";
    public static final String SESSIONS = "attendanceSessions";
    public static final String RECORDS = "records";
    
    // QR settings
    public static final int QR_REFRESH_INTERVAL_MS = 10000;  // 10 seconds
    public static final int QR_SIZE = 800;  // pixels
    
    // Geofence
    public static final double DEFAULT_GEOFENCE_RADIUS = 50.0;  // meters
    
    // Device
    public static final int MAX_DEVICES = 2;
    
    // Shared Prefs keys
    public static final String PREF_USER_ROLE = "user_role";
    public static final String PREF_USER_ID = "user_id";
}
```

#### `TimeUtils.java`
```java
public class TimeUtils {
    // Format Firestore Timestamp to "23 Mar 2026, 10:30 AM"
    public static String formatDateTime(Timestamp ts);
    
    // Format to date only: "23 Mar 2026"
    public static String formatDate(Timestamp ts);
    
    // Format to time only: "10:30 AM"
    public static String formatTime(Timestamp ts);
    
    // Generate readable session ID: "session_2026_03_23_10AM"
    public static String generateSessionId();
}
```

#### `ExportUtil.java`
```java
public class ExportUtil {
    // Export attendance data to CSV file
    // Input: list of AttendanceRecords, file name
    // Output: CSV file saved to device Downloads folder
    public static File exportToCSV(List<AttendanceRecord> records, String fileName);
    
    // Export to PDF (using Android's print framework or iText library)
    public static File exportToPDF(List<AttendanceRecord> records, String fileName);
}
```

---

### Config Files

#### `firestore.rules`
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Students can read their own doc, write their own attendance
    // Teachers can read their classes, create sessions, read attendance
    // Admins can read/write everything
  }
}
```

#### `firestore.indexes.json`
Composite indexes for common queries (e.g., sessions by classId + active).

#### `SETUP.md`
Step-by-step Firebase setup guide for team members.

#### `CONTRIBUTING.md`
Git branching strategy, commit conventions, PR process.

---

## ⚪ Shared Files

| File | Purpose |
|---|---|
| `QRAttendApp.java` | Custom Application class (initialize Firebase, etc.) |
| `AndroidManifest.xml` | Declares all Activities, permissions (CAMERA, LOCATION, INTERNET) |
| `.gitignore` | Ignores build files, local.properties, google-services.json |
| `README.md` | Project overview, tech stack, setup instructions link |
| `build.gradle` (project) | Plugin versions (AGP, Google Services) |
| `build.gradle` (app) | Dependencies (Firebase, ZXing, ML Kit, Play Services Location) |
| `settings.gradle` | Module includes |
| `gradle.properties` | JVM args, AndroidX settings |
