package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the teacher dashboard's "My Classes" section.
 * Shows one row per unique class (subject + className), with session count and join code.
 */
public class ClassGroupAdapter extends RecyclerView.Adapter<ClassGroupAdapter.ViewHolder> {

    /** Data class representing one unique class on the dashboard. */
    public static class ClassGroup {
        public String classId;    // Firestore classId (= className for MVP)
        public String className;  // e.g. "SY IT"
        public String subject;    // e.g. "OOP"
        public String joinCode;   // 6-char code displayed on the card
        public int sessionCount;  // total sessions taken for this class
        public int activeCount;   // currently active sessions
    }

    public interface OnClassGroupClickListener {
        void onClassGroupClick(ClassGroup group);
    }

    private List<ClassGroup> groups = new ArrayList<>();
    private final OnClassGroupClickListener listener;

    public ClassGroupAdapter(OnClassGroupClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<ClassGroup> newGroups) {
        this.groups = newGroups != null ? newGroups : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassGroup g = groups.get(position);

        holder.tvSubjectClass.setText(g.subject + " — " + g.className);

        String countLabel = g.sessionCount + " session" + (g.sessionCount != 1 ? "s" : "");
        if (g.activeCount > 0) {
            countLabel += "  ·  🟢 " + g.activeCount + " active";
        }
        holder.tvSessionCount.setText(countLabel);

        // Show join code chip if available
        if (holder.tvJoinCode != null) {
            if (g.joinCode != null && !g.joinCode.isEmpty()) {
                holder.tvJoinCode.setText("Code: " + g.joinCode);
                holder.tvJoinCode.setVisibility(View.VISIBLE);
            } else {
                holder.tvJoinCode.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClassGroupClick(g);
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSubjectClass;
        final TextView tvSessionCount;
        final TextView tvJoinCode;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectClass = itemView.findViewById(R.id.tvClassGroupSubject);
            tvSessionCount = itemView.findViewById(R.id.tvClassGroupCount);
            tvJoinCode     = itemView.findViewById(R.id.tvClassGroupJoinCode);
        }
    }
}
