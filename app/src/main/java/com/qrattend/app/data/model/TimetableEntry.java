package com.qrattend.app.data.model;

import com.google.firebase.firestore.Exclude;

/**
 * Represents a single weekly timetable entry for a teacher.
 * Stored as a sub-collection: teachers/{teacherId}/timetable/{entryId}
 */
public class TimetableEntry {

    private String teacherId;
    private int dayOfWeek;      // 1=Monday … 5=Friday
    private String subject;
    private String className;
    private String roomNo;      // Alphanumeric room/classroom identifier e.g. "N2", "Lab-3"
    private int startHour;
    private int startMinute;
    private int endHour;
    private int endMinute;
    private boolean notificationsEnabled;

    // Excluded from Firestore — managed locally
    @Exclude private String id;

    /** Required no-arg constructor for Firestore deserialization. */
    public TimetableEntry() {}

    public TimetableEntry(String teacherId, int dayOfWeek, String subject,
                          String className, int startHour, int startMinute,
                          int endHour, int endMinute) {
        this.teacherId           = teacherId;
        this.dayOfWeek           = dayOfWeek;
        this.subject             = subject;
        this.className           = className;
        this.startHour           = startHour;
        this.startMinute         = startMinute;
        this.endHour             = endHour;
        this.endMinute           = endMinute;
        this.notificationsEnabled = true;
    }

    // ── Firestore-excluded local ID ──────────────────────────────────────
    @Exclude public String getId()             { return id; }
    public void setId(String id)               { this.id = id; }

    // ── Firestore fields ─────────────────────────────────────────────────
    public String getTeacherId()               { return teacherId; }
    public void setTeacherId(String t)         { this.teacherId = t; }

    public int getDayOfWeek()                  { return dayOfWeek; }
    public void setDayOfWeek(int d)            { this.dayOfWeek = d; }

    public String getSubject()                 { return subject; }
    public void setSubject(String s)           { this.subject = s; }

    public String getClassName()               { return className; }
    public void setClassName(String c)         { this.className = c; }

    public String getRoomNo()                  { return roomNo; }
    public void setRoomNo(String r)            { this.roomNo = r; }

    public int getStartHour()                  { return startHour; }
    public void setStartHour(int h)            { this.startHour = h; }

    public int getStartMinute()                { return startMinute; }
    public void setStartMinute(int m)          { this.startMinute = m; }

    public int getEndHour()                    { return endHour; }
    public void setEndHour(int h)              { this.endHour = h; }

    public int getEndMinute()                  { return endMinute; }
    public void setEndMinute(int m)            { this.endMinute = m; }

    public boolean isNotificationsEnabled()    { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean b) { this.notificationsEnabled = b; }

    // ── Computed helpers (excluded from Firestore) ────────────────────────
    @Exclude
    public String getStartTimeFormatted() {
        return String.format("%02d:%02d", startHour, startMinute);
    }

    @Exclude
    public String getEndTimeFormatted() {
        return String.format("%02d:%02d", endHour, endMinute);
    }

    @Exclude
    public String getDayName() {
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday"};
        if (dayOfWeek >= 1 && dayOfWeek <= 5) return days[dayOfWeek - 1];
        return "Unknown";
    }

    /** Unique, stable int request-code used to identify this entry's alarm. */
    @Exclude
    public int getAlarmRequestCode() {
        return id != null ? id.hashCode() : (dayOfWeek * 10000 + startHour * 100 + startMinute);
    }
}
