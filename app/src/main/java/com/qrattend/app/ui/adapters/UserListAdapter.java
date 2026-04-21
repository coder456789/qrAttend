package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.model.Teacher;

import java.util.ArrayList;
import java.util.List;

public class UserListAdapter extends RecyclerView.Adapter<UserListAdapter.ViewHolder> {

    public enum UserType { STUDENT, TEACHER }

    public interface OnUserClickListener {
        void onUserClick(Object user, int position);
        void onUserLongClick(Object user, int position);
    }

    private List<Object> users = new ArrayList<>();
    private UserType currentType = UserType.STUDENT;
    private OnUserClickListener listener;

    public UserListAdapter(OnUserClickListener listener) {
        this.listener = listener;
    }

    public void setUserType(UserType type) {
        this.currentType = type;
    }

    public void updateList(List<?> newUsers) {
        this.users = new ArrayList<>(newUsers != null ? newUsers : new ArrayList<>());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object user = users.get(position);

        if (currentType == UserType.STUDENT && user instanceof Student) {
            Student s = (Student) user;
            String name = s.getName() != null ? s.getName() : "";
            holder.tvName.setText(name);
            holder.tvInfo.setText(s.getEmail() != null ? s.getEmail() : "");
            holder.tvAvatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
            holder.tvRole.setText(R.string.role_student);
            holder.tvRole.setBackgroundResource(R.drawable.bg_card_rounded); // Assuming this is used as a background
            // Note: If you want to change background tint programmatically, you might need more logic
        } else if (currentType == UserType.TEACHER && user instanceof Teacher) {
            Teacher t = (Teacher) user;
            String name = t.getName() != null ? t.getName() : "";
            holder.tvName.setText(name);
            holder.tvInfo.setText(t.getEmail() != null ? t.getEmail() : "");
            holder.tvAvatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
            holder.tvRole.setText(R.string.role_teacher);
            holder.tvRole.setBackgroundResource(R.drawable.bg_card_rounded);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onUserClick(user, position);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onUserLongClick(user, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAvatar;
        final TextView tvName;
        final TextView tvInfo;
        final TextView tvRole;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAvatar = itemView.findViewById(R.id.tvUserInitial);
            tvName = itemView.findViewById(R.id.tvUserName);
            tvInfo = itemView.findViewById(R.id.tvUserEmail);
            tvRole = itemView.findViewById(R.id.tvUserRole);
        }
    }
}
