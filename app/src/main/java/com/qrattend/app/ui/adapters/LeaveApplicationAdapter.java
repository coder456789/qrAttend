package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.LeaveApplication;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the teacher's leave-application list.
 * Tap → shows full detail + attachment.
 * Long-press → Approve / Reject action dialog.
 */
public class LeaveApplicationAdapter
        extends RecyclerView.Adapter<LeaveApplicationAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(LeaveApplication app);
    }

    public interface OnItemLongClickListener {
        void onLongClick(LeaveApplication app);
    }

    private List<LeaveApplication> items = new ArrayList<>();
    private final OnItemClickListener     clickListener;
    private       OnItemLongClickListener longClickListener;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public LeaveApplicationAdapter(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        this.longClickListener = l;
    }

    public void updateList(List<LeaveApplication> list) {
        this.items = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leave_application, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaveApplication app = items.get(position);

        holder.tvStudentName.setText(app.getStudentName() != null ? app.getStudentName() : "Student");
        holder.tvRollNo.setText(app.getStudentRollNo() != null
                ? "PRN: " + app.getStudentRollNo() : "");
        holder.tvSubmittedAt.setText(app.getSubmittedAt() != null
                ? dateFormat.format(app.getSubmittedAt().toDate()) : "");

        // Show 📎 badge if the application has an attachment
        if (holder.tvAttachBadge != null) {
            holder.tvAttachBadge.setVisibility(
                    app.hasAttachment() ? View.VISIBLE : View.GONE);
        }

        String status = app.getStatus() != null ? app.getStatus() : "Pending";
        holder.tvStatus.setText(status);

        // Colour status dot and text
        int colorRes;
        switch (status) {
            case "Approved": colorRes = R.color.attendanceHigh;   break;
            case "Rejected": colorRes = R.color.attendanceLow;    break;
            default:         colorRes = R.color.attendanceMid;    break;  // Pending
        }
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
        holder.tvStatus.setTextColor(color);
        if (holder.viewStatusDot != null) {
            holder.viewStatusDot.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(color));
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(app);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onLongClick(app);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvStudentName, tvRollNo, tvSubmittedAt, tvStatus;
        final TextView tvAttachBadge;
        final View     viewStatusDot;

        ViewHolder(@NonNull View v) {
            super(v);
            tvStudentName  = v.findViewById(R.id.tvStudentName);
            tvRollNo       = v.findViewById(R.id.tvRollNo);
            tvSubmittedAt  = v.findViewById(R.id.tvSubmittedAt);
            tvStatus       = v.findViewById(R.id.tvStatus);
            tvAttachBadge  = v.findViewById(R.id.tvAttachBadge);
            viewStatusDot  = v.findViewById(R.id.viewStatusDot);
        }
    }
}
