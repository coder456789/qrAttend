package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the Subject-wise breakdown list on the Student Dashboard.
 * Each row shows: Subject Name | Attendance % | Progress bar | "X/Y sessions" count.
 * Tapping a row triggers {@link OnSubjectClickListener#onClick(SubjectGroup)}.
 */
public class SubjectGroupAdapter extends RecyclerView.Adapter<SubjectGroupAdapter.ViewHolder> {

    /** Callback when the student taps a subject row. */
    public interface OnSubjectClickListener {
        void onClick(SubjectGroup item);
    }

    /** In-memory grouping of attendance records for one subject. */
    public static class SubjectGroup {
        public final String subjectName;
        public final int    presentCount;
        public final int    totalCount;

        public SubjectGroup(String subjectName, int presentCount, int totalCount) {
            this.subjectName  = subjectName;
            this.presentCount = presentCount;
            this.totalCount   = totalCount;
        }

        public int getPercentage() {
            return totalCount > 0 ? (presentCount * 100) / totalCount : 0;
        }
    }

    private List<SubjectGroup>   items         = new ArrayList<>();
    private OnSubjectClickListener clickListener;

    public SubjectGroupAdapter(OnSubjectClickListener listener) {
        this.clickListener = listener;
    }

    public void updateList(List<SubjectGroup> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_subject, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SubjectGroup item = items.get(position);
        int pct = item.getPercentage();

        holder.tvSubjectName.setText(item.subjectName);
        holder.tvSubjectPercent.setText(pct + "%");
        holder.progressSubject.setProgress(pct);
        holder.tvSubjectCount.setText(
                item.presentCount + " / " + item.totalCount + " sessions present");

        // Colour the percentage by attendance threshold
        int colorRes;
        if (pct >= 75) {
            colorRes = R.color.attendanceHigh;
        } else if (pct >= 60) {
            colorRes = R.color.attendanceMid;
        } else {
            colorRes = R.color.attendanceLow;
        }
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
        holder.tvSubjectPercent.setTextColor(color);
        holder.progressSubject.setProgressTintList(
                android.content.res.ColorStateList.valueOf(color));

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView    tvSubjectName;
        final TextView    tvSubjectPercent;
        final TextView    tvSubjectCount;
        final ProgressBar progressSubject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectName    = itemView.findViewById(R.id.tvSubjectName);
            tvSubjectPercent = itemView.findViewById(R.id.tvSubjectPercent);
            tvSubjectCount   = itemView.findViewById(R.id.tvSubjectCount);
            progressSubject  = itemView.findViewById(R.id.progressSubject);
        }
    }
}
