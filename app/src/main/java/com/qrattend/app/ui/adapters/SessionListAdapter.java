package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceSession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying a list of {@link AttendanceSession} objects on the
 * Teacher Dashboard. Shows subject, class, start time, and active/ended status.
 * Tapping a row opens the session's attendance records.
 */
public class SessionListAdapter extends RecyclerView.Adapter<SessionListAdapter.ViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(AttendanceSession session);
    }

    private List<AttendanceSession> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());

    public SessionListAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<AttendanceSession> newSessions) {
        this.sessions = newSessions != null ? newSessions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AttendanceSession session = sessions.get(position);

        // Subject — Class
        String subject   = session.getSubject()   != null ? session.getSubject()   : "—";
        String className = session.getClassName()  != null ? session.getClassName() : "—";
        holder.tvSubjectClass.setText(subject + "  ·  " + className);

        // Date / time
        if (session.getStartTime() != null) {
            holder.tvStartTime.setText(dateFormat.format(session.getStartTime().toDate()));
        } else {
            holder.tvStartTime.setText("—");
        }

        // Active badge
        if (session.isActive()) {
            holder.tvStatus.setText("🟢 Active");
            holder.tvStatus.setTextColor(
                    holder.itemView.getContext().getResources().getColor(R.color.statusPresent));
        } else {
            holder.tvStatus.setText("🔴 Ended");
            holder.tvStatus.setTextColor(
                    holder.itemView.getContext().getResources().getColor(R.color.statusRejected));
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionClick(session);
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSubjectClass;
        final TextView tvStartTime;
        final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubjectClass = itemView.findViewById(R.id.tvSessionSubjectClass);
            tvStartTime    = itemView.findViewById(R.id.tvSessionStartTime);
            tvStatus       = itemView.findViewById(R.id.tvSessionStatus);
        }
    }
}
