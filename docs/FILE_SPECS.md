# QR-Attend — File Specification Guide
### What Each File Must Contain & Do (Simplified 58-File Structure)

> This document describes every file in the repo.
> Use it as a reference when coding — each file has its purpose, inputs, outputs, and key methods listed.

---

## 🔵 MEMBER 1 — UI / Frontend (14 Activities + 3 Adapters + 17 XML)

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
| Adapter | `AttendanceRecordAdapter` |
| XML | RecyclerView, session info header (date, subject, total count) |

---

### `AdminDashboardActivity.java` + `activity_admin_dashboard.xml`
**Purpose:** Admin's main screen with management options.

| What it shows | Details |
|---|---|
| Cards/buttons | "Manage Users", "Manage Classes" |
| Stats overview | Total students, total teachers, total classes |
| XML | Grid of cards with icons and counts |

---

### `ManageUsersActivity.java` + `activity_manage_users.xml`
**Purpose:** Admin views/adds/edits/deletes students AND teachers (tab/toggle to switch).

| What it does | Details |
|---|---|
| Tab: Students | List all students from `students` collection |
| Tab: Teachers | List all teachers from `teachers` collection |
| Add | Dialog/form → `StudentRepository.addStudent()` or `TeacherRepository.addTeacher()` |
| Edit | Tap row → edit dialog → update Firestore |
| Delete | Swipe/long-press → delete from Firestore |
| Adapter | `UserListAdapter` (reused for both) |
| XML | TabLayout (Students / Teachers), RecyclerView, FAB "Add", search bar |

> **Note:** This replaces the old `ManageStudentsActivity` + `ManageTeachersActivity`. Same screen, different data.

---

### `ManageClassesActivity.java` + `activity_manage_classes.xml`
**Purpose:** Admin creates/edits class-subject mappings.

| What it does | Details |
|---|---|
| List classes | Read `classes` collection |
| Add class | Form: className, subject, assign teacher (dropdown from `teachers`) |
| Enroll students | Multi-select students to add to `enrolledStudents` array |
| Adapter | `ClassListAdapter` |
| XML | RecyclerView, FAB "Add Class" |

---

### `SettingsActivity.java` + `activity_settings.xml`
**Purpose:** App settings, profile editing, and logout.

| What it shows | Details |
|---|---|
| Profile section | Name, phone, email (read-only), device info — edit + save |
| Notification toggle | Enable/disable push notifications |
| Dark mode toggle | Switch theme |
| About section | App version, developers |
| Logout button | `AuthManager.logout()` → go to LoginActivity |
| XML | Profile card at top, list of setting items with toggles, Logout button at bottom |

> **Note:** This replaces the old `ProfileActivity` + `SettingsActivity`. Profile editing is now a section inside Settings.

---

### Adapters (3 files)

#### `AttendanceRecordAdapter.java` + `item_attendance_record.xml`
| What it renders | One row of attendance history |
|---|---|
| Data | Date, subject name, status (colored badge: green=present, red=rejected), time |
| XML | Horizontal row: date text, subject text, status chip, time text |

#### `UserListAdapter.java` + `item_user.xml`
| What it renders | One row of student OR teacher in a list |
|---|---|
| Data | Name, role indicator, email, roll no (students) or subject (teachers) |
| Click action | Opens edit dialog or detail view |
| XML | Horizontal row: avatar circle, name, info text, role badge |

> **Note:** Replaces old `StudentListAdapter` + `CourseListAdapter`. Reused for both students and teachers.

#### `ClassListAdapter.java` + `item_class.xml`
| What it renders | One row of class/course OR past session |
|---|---|
| Data | Class name, subject, teacher name OR date + attendance count |
| Click action | Opens detail view or `SessionAttendanceActivity` |
| XML | Card with subject name, class name, info chip |

> **Note:** Replaces old `CourseListAdapter` + `SessionListAdapter`. Reused for both.

---

## 🟢 MEMBER 2 — Core Logic (7 Java Files)

---

### `QRGeneratorUtil.java`
**Purpose:** Generate QR code bitmap from encrypted payload using ZXing.

```java
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
**Purpose:** Collect unique device identifiers AND detect rooted/emulator devices.

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
    
    // === MERGED FROM IntegrityChecker.java ===
    
    // Check if device is rooted (su binary, Magisk, SuperSU)
    public static boolean isRooted();
    
    // Check if running on emulator (Build.FINGERPRINT "generic")
    public static boolean isEmulator();
    
    // Check if APK signature is valid (not repackaged)
    public static boolean isAPKTampered(Context context);
    
    // All-in-one: fingerprint match + root + emulator + APK check
    public static boolean isDeviceTrusted(Context context);
}
```

> **Note:** This now includes root/emulator detection (merged from old `IntegrityChecker.java`).

---

### `NonceManager.java`
**Purpose:** Generate random one-time nonces and validate scanned nonces. **No Firestore involved — pure local logic.**

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

### `GeoValidator.java`
**Purpose:** Check if student is within classroom geofence AND detect mock/fake GPS.

```java
public class GeoValidator {

    // Get current GPS location (Fused Location Provider)
    public void getCurrentLocation(Activity activity, OnLocationResultListener callback);
    
    // Check if location permissions are granted
    public static boolean hasLocationPermission(Context context);
    
    // Request location permissions
    public static void requestPermission(Activity activity, int requestCode);

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
    
    // === MERGED FROM LocationHelper.java + MockLocationDetector.java ===
    
    // Check if location is from a mock provider
    public static boolean isMockLocation(Location location);
    
    // Check if any mock location apps are installed
    public static boolean hasMockAppsInstalled(Context context);
    
    // Combined: mock check + geofence check
    public static boolean isLocationValid(Location studentLocation,
                                           double classLat, double classLng,
                                           double radiusMeters, Context context);
    
    public interface OnLocationResultListener {
        void onLocation(double latitude, double longitude);
        void onError(String error);
    }
}
```

> **Note:** This now includes location fetching (from `LocationHelper`) and mock detection (from `MockLocationDetector`).

**Dependencies:** `com.google.android.gms:play-services-location`

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
    // 1. DeviceFingerprint.isDeviceTrusted()     → reject if rooted/emulator
    // 2. NonceManager.isValid(scannedNonce, sessionNonce) → reject if QR expired
    // 3. DeviceFingerprint.matches()             → reject if wrong device
    // 4. GeoValidator.isLocationValid()           → reject if fake GPS or outside classroom
    // 5. ALL PASS → return ValidationResult.SUCCESS
    
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

## 🟠 MEMBER 3 — Backend / Firebase (5 Models + 5 Repos + 2 Firebase = 12 Java + 2 Config)

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

> **Note:** Nonce validation is handled here — no separate `NonceRepository` needed.

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

#### `SETUP.md`
Step-by-step Firebase setup guide for team members.

---

## ⚪ Shared Files

| File | Purpose |
|---|---|
| `AndroidManifest.xml` | Declares all Activities, permissions (CAMERA, LOCATION, INTERNET) |
| `.gitignore` | Ignores build files, local.properties, google-services.json |
| `README.md` | Project overview, tech stack, setup instructions link |
| `build.gradle` (project) | Plugin versions (AGP, Google Services) |
| `build.gradle` (app) | Dependencies (Firebase, ZXing, ML Kit, Play Services Location) |
| `settings.gradle` | Module includes |
| `gradle.properties` | JVM args, AndroidX settings |

---

## Summary: What Was Merged

| Old File | Now Part Of | Reason |
|---|---|---|
| `ProfileActivity` + XML | `SettingsActivity` | Profile editing is just a section in settings |
| `ManageStudentsActivity` + `ManageTeachersActivity` | `ManageUsersActivity` | Same screen, tab/toggle to switch |
| `IntegrityChecker.java` | `DeviceFingerprint.java` | Root/emulator check is part of fingerprinting |
| `LocationHelper.java` + `MockLocationDetector.java` | `GeoValidator.java` | All location logic in one file |
| `FirestoreHelper.java` | Individual repositories | Each repo handles its own Firestore instance |
| `NonceLog.java` + `NonceRepository.java` | `SessionRepository` | No separate nonce collection needed |
| `ExportUtil.java` + `TimeUtils.java` | Deferred to v1.1 | Not essential for v1.0 |
| `StudentListAdapter` + `CourseListAdapter` + `SessionListAdapter` | `UserListAdapter` + `ClassListAdapter` | Reusable with different data |
| `QRAttendApp.java` | Removed | No custom Application class for v1.0 |
| `CONTRIBUTING.md` + `firestore.indexes.json` + `dimens.xml` | Removed | Not needed for 3-person team |
