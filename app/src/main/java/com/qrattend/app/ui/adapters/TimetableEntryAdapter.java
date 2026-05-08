package com.qrattend.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qrattend.app.R;
import com.qrattend.app.data.model.TimetableEntry;

import java.util.ArrayList;
import java.util.List;

public class TimetableEntryAdapter
        extends RecyclerView.Adapter<TimetableEntryAdapter.VH> {

    public interface OnEntryActionListener {
        void onEdit(TimetableEntry entry);
        void onDelete(TimetableEntry entry);
    }

    private final List<TimetableEntry> items = new ArrayList<>();
    private final OnEntryActionListener listener;

    public TimetableEntryAdapter(OnEntryActionListener listener) {
        this.listener = listener;
    }

    public void setEntries(List<TimetableEntry> entries) {
        items.clear();
        if (entries != null) items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_timetable_entry, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        TimetableEntry e = items.get(pos);
        h.tvSubject.setText(e.getSubject());
        h.tvClass.setText(e.getClassName() != null ? e.getClassName() : "");
        // Room No. — hide if empty
        String room = e.getRoomNo();
        if (room != null && !room.isEmpty()) {
            h.tvRoom.setVisibility(android.view.View.VISIBLE);
            h.tvRoom.setText("Room: " + room);
        } else {
            h.tvRoom.setVisibility(android.view.View.GONE);
        }
        // 12-hour AM/PM time range
        h.tvTime.setText(
                fmt12h(e.getStartHour(), e.getStartMinute())
                + "  –  "
                + fmt12h(e.getEndHour(), e.getEndMinute()));
        h.btnEdit.setOnClickListener(v -> listener.onEdit(e));
        h.btnDelete.setOnClickListener(v -> listener.onDelete(e));
    }

    /** Converts 24h (0-23) hour + minute → "h:mm AM/PM" */
    private static String fmt12h(int hour24, int minute) {
        String amPm   = hour24 < 12 ? "AM" : "PM";
        int    hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return String.format(java.util.Locale.getDefault(),
                "%d:%02d %s", hour12, minute, amPm);
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView    tvSubject, tvClass, tvRoom, tvTime;
        ImageButton btnEdit, btnDelete;

        VH(@NonNull View v) {
            super(v);
            tvSubject  = v.findViewById(R.id.tvEntrySubject);
            tvClass    = v.findViewById(R.id.tvEntryClass);
            tvRoom     = v.findViewById(R.id.tvEntryRoom);
            tvTime     = v.findViewById(R.id.tvEntryTime);
            btnEdit    = v.findViewById(R.id.btnEditEntry);
            btnDelete  = v.findViewById(R.id.btnDeleteEntry);
        }
    }
}
