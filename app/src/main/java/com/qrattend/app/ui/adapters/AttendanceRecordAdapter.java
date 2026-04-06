package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
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

        // Session ID
        String sessionId = record.getSessionId();
        holder.tvSession.setText(sessionId != null ? sessionId : "");

        // Status chip
        String status = record.getStatus();
        if (Constants.STATUS_PRESENT.equals(status)) {
            holder.chipStatus.setText(R.string.filter_present);
            holder.chipStatus.setChipBackgroundColorResource(R.color.statusPresent);
            holder.chipStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.white));
            holder.tvRejectionReason.setVisibility(View.GONE);
        } else {
            holder.chipStatus.setText(R.string.filter_rejected);
            holder.chipStatus.setChipBackgroundColorResource(R.color.statusRejected);
            holder.chipStatus.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.white));
            String reason = record.getRejectionReason();
            if (reason != null && !reason.isEmpty()) {
                holder.tvRejectionReason.setVisibility(View.VISIBLE);
                holder.tvRejectionReason.setText(reason);
            } else {
                holder.tvRejectionReason.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvDate;
        final TextView tvSession;
        final TextView tvRejectionReason;
        final Chip chipStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvRecordDate);
            tvSession = itemView.findViewById(R.id.tvRecordSession);
            tvRejectionReason = itemView.findViewById(R.id.tvRejectionReason);
            chipStatus = itemView.findViewById(R.id.chipStatus);
        }
    }
}
