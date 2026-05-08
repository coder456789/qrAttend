package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceRecordAdapter extends RecyclerView.Adapter<AttendanceRecordAdapter.ViewHolder> {

    /** Controls what the secondary line of each card shows. */
    public enum DisplayMode {
        /** Student history view — shows the subject / lecture name. */
        STUDENT_HISTORY,
        /** Teacher session view — shows the student's name + PRN. */
        TEACHER_SESSION,
        /**
         * Student subject-detail view — primary shows "Session N", secondary shows date/time.
         * Used in SubjectAttendanceActivity.
         */
        STUDENT_SUBJECT_SESSION
    }

    /** Long-click callback for manual override in SessionAttendanceActivity. */
    public interface OnItemLongClickListener {
        void onLongClick(AttendanceRecord record, int position);
    }

    private List<AttendanceRecord> records = new ArrayList<>();
    private final DisplayMode displayMode;
    private OnItemLongClickListener longClickListener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    /** Default constructor — uses STUDENT_HISTORY mode. */
    public AttendanceRecordAdapter() {
        this.displayMode = DisplayMode.STUDENT_HISTORY;
    }

    /** Explicit mode constructor. */
    public AttendanceRecordAdapter(DisplayMode mode) {
        this.displayMode = mode;
    }

    public void updateList(List<AttendanceRecord> newRecords) {
        this.records = newRecords != null ? newRecords : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Set to enable long-press actions (teacher use). */
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_attendance_record, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceRecord record = records.get(position);

        if (displayMode == DisplayMode.STUDENT_SUBJECT_SESSION) {
            // ── Subject-session view ──────────────────────────────────────
            // Primary: "Session N"
            holder.tvSubject.setText("Session " + (position + 1));
            // Secondary: date/time
            if (record.getTime() != null) {
                holder.tvDate.setText(dateFormat.format(record.getTime().toDate()));
            } else {
                holder.tvDate.setText("—");
            }

        } else if (displayMode == DisplayMode.STUDENT_HISTORY) {
            // ── Student history view ──────────────────────────────────────
            if (record.getTime() != null) {
                holder.tvDate.setText(dateFormat.format(record.getTime().toDate()));
            } else {
                holder.tvDate.setText("—");
            }
            String subject = record.getSubject();
            if (subject != null && !subject.isEmpty()) {
                holder.tvSubject.setText(subject);
            } else {
                holder.tvSubject.setText("Session: " + record.getSessionId());
            }

        } else {
            // ── Teacher session view ──────────────────────────────────────
            if (record.getTime() != null) {
                holder.tvDate.setText(dateFormat.format(record.getTime().toDate()));
            } else {
                holder.tvDate.setText("—");
            }
            String name   = record.getStudentName();
            String rollNo = record.getStudentRollNo();
            if (name != null && !name.isEmpty()) {
                String display = name;
                if (rollNo != null && !rollNo.isEmpty()) display += "  |  PRN: " + rollNo;
                holder.tvSubject.setText(display);
            } else {
                String uid = record.getStudentId();
                holder.tvSubject.setText(uid != null ? "UID: " + uid : "Unknown Student");
            }
        }

        // ── Status chip ────────────────────────────────────────────────────
        bindStatusChip(holder, record.getStatus());

        // ── Long-press ────────────────────────────────────────────────────
        if (longClickListener != null) {
            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onLongClick(record, position);
                return true;
            });
        }
    }

    /** Colors the status chip based on the attendance status string. */
    private void bindStatusChip(ViewHolder holder, String status) {
        if (status == null) status = "";

        boolean isPresent = Constants.STATUS_PRESENT.equals(status)
                || "Present".equals(status);
        boolean isAbsent  = Constants.STATUS_ABSENT.equals(status);
        boolean isLeave   = Constants.STATUS_LEAVE.equals(status);

        int textRes;
        int colorRes;

        if (isPresent) {
            textRes  = R.string.filter_present;
            colorRes = R.color.statusPresent;
        } else if (isAbsent) {
            textRes  = R.string.filter_absent;
            colorRes = R.color.attendanceMid;   // amber / orange
        } else if (isLeave) {
            textRes  = R.string.filter_leave;
            colorRes = R.color.tertiary;         // blue
        } else {
            textRes  = R.string.filter_rejected;
            colorRes = R.color.statusRejected;
        }

        holder.tvStatus.setText(textRes);
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
        holder.tvStatus.setTextColor(color);
        holder.viewStatusDot.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(color));
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvSubject;
        final TextView tvStatus;
        final View     viewStatusDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate        = itemView.findViewById(R.id.tvDate);
            tvSubject     = itemView.findViewById(R.id.tvSubject);
            tvStatus      = itemView.findViewById(R.id.tvStatus);
            viewStatusDot = itemView.findViewById(R.id.viewStatusDot);
        }
    }
}
