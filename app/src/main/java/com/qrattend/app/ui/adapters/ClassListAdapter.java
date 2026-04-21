package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.ClassInfo;

import java.util.ArrayList;
import java.util.List;

public class ClassListAdapter extends RecyclerView.Adapter<ClassListAdapter.ViewHolder> {

    public interface OnClassClickListener {
        void onClassClick(ClassInfo classInfo, int position);
        void onClassLongClick(ClassInfo classInfo, int position);
    }

    private List<ClassInfo> classes = new ArrayList<>();
    private OnClassClickListener listener;

    public ClassListAdapter(OnClassClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<ClassInfo> newClasses) {
        this.classes = newClasses != null ? newClasses : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_class, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ClassInfo classInfo = classes.get(position);

        holder.tvSubject.setText(classInfo.getSubject() != null ? classInfo.getSubject() : "");
        holder.tvClassName.setText(classInfo.getClassName() != null ? classInfo.getClassName() : "");

        int enrolled = classInfo.getEnrolledStudents() != null ? classInfo.getEnrolledStudents().size() : 0;
        holder.tvClassInfo.setText(holder.itemView.getContext()
                .getString(R.string.enrolled_count, enrolled));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClassClick(classInfo, position);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onClassLongClick(classInfo, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return classes.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSubject;
        final TextView tvClassName;
        final TextView tvClassInfo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubject = itemView.findViewById(R.id.tvSubject);
            tvClassName = itemView.findViewById(R.id.tvClassName);
            tvClassInfo = itemView.findViewById(R.id.tvStudentCount);
        }
    }
}
