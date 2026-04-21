package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.utils.Constants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AttendanceRecordAdapter extends RecyclerView.Adapter<AttendanceRecordAdapter.ViewHolder> {

    private List<AttendanceRecord> records = new ArrayList<>();
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public void updateList(List<AttendanceRecord> newRecords) {
        this.records = newRecords != null ? newRecords : new ArrayList<>();
        notifyDataSetChanged();
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

        // Date
        if (record.getTime() != null) {
            holder.tvDate.setText(dateFormat.format(record.getTime().toDate()));
        } else {
            holder.tvDate.setText("—");
        }

        // Subject (using session ID for now as fallback)
        String subject = record.getSessionId();
        holder.tvSubject.setText(subject != null ? subject : "Unknown Class");

        // Status
        String status = record.getStatus();
        if (Constants.STATUS_PRESENT.equals(status)) {
            holder.tvStatus.setText(R.string.filter_present);
            holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.statusPresent));
            holder.viewStatusDot.setBackgroundResource(R.color.statusPresent);
        } else {
            holder.tvStatus.setText(R.string.filter_rejected);
            holder.tvStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.statusRejected));
            holder.viewStatusDot.setBackgroundResource(R.color.statusRejected);
        }
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvSubject;
        final TextView tvStatus;
        final View viewStatusDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            viewStatusDot = itemView.findViewById(R.id.viewStatusDot);
        }
    }
}
