package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.ClassInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple adapter that shows which classes a student is already enrolled in,
 * used in {@link com.qrattend.app.ui.JoinClassActivity}.
 */
public class EnrolledClassAdapter extends RecyclerView.Adapter<EnrolledClassAdapter.ViewHolder> {

    private List<ClassInfo> items = new ArrayList<>();

    public void updateList(List<ClassInfo> list) {
        this.items = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassInfo ci = items.get(position);
        holder.tvClassName.setText(ci.getClassName());
        holder.tvSubject.setText(ci.getSubject());
        holder.tvClassInitial.setText(
                ci.getSubject() != null && !ci.getSubject().isEmpty()
                        ? String.valueOf(ci.getSubject().charAt(0)).toUpperCase()
                        : "?");
        if (holder.tvStudentCount != null) {
            int count = ci.getEnrolledStudents() != null ? ci.getEnrolledStudents().size() : 0;
            holder.tvStudentCount.setText(count + " students enrolled");
        }
        // Non-clickable in this context
        holder.itemView.setClickable(false);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvClassInitial, tvClassName, tvSubject, tvStudentCount;
        ViewHolder(@NonNull View v) {
            super(v);
            tvClassInitial = v.findViewById(R.id.tvClassInitial);
            tvClassName    = v.findViewById(R.id.tvClassName);
            tvSubject      = v.findViewById(R.id.tvSubject);
            tvStudentCount = v.findViewById(R.id.tvStudentCount);
        }
    }
}
