package com.qrattend.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.model.AttendanceSession;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Utility for exporting attendance data as CSV and sharing via the system share sheet.
 */
public class CsvExporter {

    private static final String TAG = "CsvExporter";
    private static final String AUTHORITY = "com.qrattend.app.fileprovider";

    // ── Per-Session Export (unchanged original) ──────────────────────────

    /**
     * Exports a single session's records as CSV (simple row-per-student format).
     */
    public static void exportAndShare(Context context,
                                      List<AttendanceRecord> records,
                                      String sessionLabel) {
        if (records == null || records.isEmpty()) {
            Log.w(TAG, "exportAndShare: no records to export");
            return;
        }

        SimpleDateFormat tsFormat =
                new SimpleDateFormat("dd-MMM-yyyy HH:mm", Locale.getDefault());
        SimpleDateFormat fileDate =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

        StringBuilder csv = new StringBuilder(
                "Student Name,PRN / Roll No,Status,Date & Time,Session ID,Subject\n");

        for (AttendanceRecord r : records) {
            csv.append(esc(r.getStudentName()))
               .append(',').append(esc(r.getStudentRollNo()))
               .append(',').append(esc(r.getStatus()))
               .append(',').append(r.getTime() != null
                       ? esc(tsFormat.format(r.getTime().toDate())) : "")
               .append(',').append(esc(r.getSessionId()))
               .append(',').append(esc(r.getSubject()))
               .append('\n');
        }

        shareFile(context, csv.toString(), "attendance_" + safe(sessionLabel), fileDate, sessionLabel);
    }

    // ── Whole-Class Export ───────────────────────────────────────────────

    /**
     * Exports a whole-class attendance report as CSV.
     * <p>
     * <b>Format:</b>
     * <pre>
     * Student Name | PRN | 01-Apr-2026 | 05-Apr-2026 | ... | Attendance %
     * John Doe     | 123 | Present     | Absent      | ... | 75.00%
     * </pre>
     *
     * @param sessions      all sessions for this class (oldest-first)
     * @param recordBuckets parallel list — recordBuckets[i] contains the records for sessions[i]
     * @param classLabel    e.g. "OOP_SY_IT"
     */
    public static void exportWholeClassCsv(Context context,
                                           List<AttendanceSession> sessions,
                                           List<List<AttendanceRecord>> recordBuckets,
                                           String classLabel) {
        if (sessions == null || sessions.isEmpty()) {
            Log.w(TAG, "exportWholeClassCsv: no sessions");
            return;
        }

        SimpleDateFormat dateCol =
                new SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault());
        SimpleDateFormat fileDate =
                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

        // 1. Build ordered list of session date-column headers
        List<String> dateHeaders = new ArrayList<>();
        for (AttendanceSession s : sessions) {
            String header = s.getStartTime() != null
                    ? dateCol.format(s.getStartTime().toDate())
                    : s.getSessionId();
            dateHeaders.add(header);
        }

        // 2. Collect all unique students (PRN → name) across every session
        //    Use LinkedHashMap to maintain insertion order
        //    key = studentId (UID), value = [name, prn]
        Map<String, String[]> studentMap = new LinkedHashMap<>();
        // Also build per-session lookup: sessionIndex → set of present student IDs
        List<Set<String>> presentSets = new ArrayList<>();
        List<Set<String>> allStudentSets = new ArrayList<>();

        for (int i = 0; i < sessions.size(); i++) {
            Set<String> present  = new LinkedHashSet<>();
            Set<String> allInSession = new LinkedHashSet<>();
            List<AttendanceRecord> records = recordBuckets.get(i);
            if (records != null) {
                for (AttendanceRecord r : records) {
                    String sid = r.getStudentId();
                    if (sid == null) continue;

                    allInSession.add(sid);

                    // Register this student globally
                    if (!studentMap.containsKey(sid)) {
                        String name = r.getStudentName() != null ? r.getStudentName() : sid;
                        String prn  = r.getStudentRollNo() != null ? r.getStudentRollNo() : "";
                        studentMap.put(sid, new String[]{name, prn});
                    }

                    // Check if present
                    String status = r.getStatus();
                    if (Constants.STATUS_PRESENT.equalsIgnoreCase(status)
                            || "Present".equalsIgnoreCase(status)) {
                        present.add(sid);
                    }
                }
            }
            presentSets.add(present);
            allStudentSets.add(allInSession);
        }

        if (studentMap.isEmpty()) {
            Log.w(TAG, "exportWholeClassCsv: no student records found");
            return;
        }

        // 3. Build CSV
        StringBuilder csv = new StringBuilder();

        // Header row: Student Name, PRN, date1, date2, ..., Attendance %
        csv.append("Student Name,PRN");
        for (String dh : dateHeaders) {
            csv.append(',').append(esc(dh));
        }
        csv.append(",Attendance %\n");

        // One row per student
        int totalSessions = sessions.size();
        for (Map.Entry<String, String[]> entry : studentMap.entrySet()) {
            String uid   = entry.getKey();
            String name  = entry.getValue()[0];
            String prn   = entry.getValue()[1];

            csv.append(esc(name)).append(',').append(esc(prn));

            int presentCount = 0;
            for (int i = 0; i < totalSessions; i++) {
                if (presentSets.get(i).contains(uid)) {
                    csv.append(",Present");
                    presentCount++;
                } else {
                    csv.append(",Absent");
                }
            }

            // Attendance %
            double pct = totalSessions > 0
                    ? (presentCount * 100.0 / totalSessions) : 0;
            csv.append(',').append(String.format(Locale.US, "%.2f%%", pct));
            csv.append('\n');
        }

        shareFile(context, csv.toString(), "class_attendance_" + safe(classLabel), fileDate, classLabel);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Write CSV string to cache and launch share sheet. */
    private static void shareFile(Context context, String csvContent,
                                  String prefix,
                                  SimpleDateFormat fileDate,
                                  String shareSubject) {
        String fileName = prefix + "_" + fileDate.format(new Date()) + ".csv";

        File cacheDir = context.getExternalCacheDir() != null
                ? context.getExternalCacheDir()
                : context.getCacheDir();
        File csvFile = new File(cacheDir, fileName);

        try (FileWriter writer = new FileWriter(csvFile)) {
            writer.write(csvContent);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write CSV: " + e.getMessage(), e);
            return;
        }

        Uri contentUri = FileProvider.getUriForFile(context, AUTHORITY, csvFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/csv");
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Attendance — " + shareSubject);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(shareIntent, "Export CSV via…"));

        Log.d(TAG, "CSV exported: " + csvFile.getAbsolutePath());
    }

    /** Make a string filesystem-safe. */
    private static String safe(String s) {
        if (s == null) return "export";
        return s.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase(Locale.ROOT);
    }

    /** CSV-escape a value: wrap in quotes, double any embedded quotes. */
    private static String esc(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
