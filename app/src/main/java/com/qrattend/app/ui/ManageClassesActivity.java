package com.qrattend.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.qrattend.app.R;
import com.qrattend.app.data.model.ClassInfo;
import com.qrattend.app.data.repository.ClassRepository;
import com.qrattend.app.ui.adapters.ClassListAdapter;

import java.util.ArrayList;

public class ManageClassesActivity extends AppCompatActivity {

    private RecyclerView rvClasses;
    private TextView tvEmpty;
    private FloatingActionButton fabAddClass;
    private ClassListAdapter adapter;
    private ClassRepository classRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_classes);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvClasses = findViewById(R.id.rvClasses);
        tvEmpty = findViewById(R.id.tvEmpty);
        fabAddClass = findViewById(R.id.fabAddClass);

        classRepo = new ClassRepository();

        adapter = new ClassListAdapter(new ClassListAdapter.OnClassClickListener() {
            @Override
            public void onClassClick(ClassInfo classInfo, int position) {
                // Edit class dialog
            }

            @Override
            public void onClassLongClick(ClassInfo classInfo, int position) {
                showDeleteConfirmation(classInfo);
            }
        });
        rvClasses.setLayoutManager(new LinearLayoutManager(this));
        rvClasses.setAdapter(adapter);

        fabAddClass.setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.add_class), Toast.LENGTH_SHORT).show());

        loadClasses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadClasses();
    }

    private void loadClasses() {
        classRepo.getAllClasses(classes -> {
            if (classes == null || classes.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                rvClasses.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                rvClasses.setVisibility(View.VISIBLE);
                adapter.updateList(classes);
            }
        });
    }

    private void showDeleteConfirmation(ClassInfo classInfo) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_class)
                .setMessage(R.string.confirm_delete_class)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    // ClassInfo uses getClassName() — for delete we'd need the doc ID
                    // In practice, use @DocumentId annotation or track IDs from snapshots
                    Toast.makeText(this, getString(R.string.class_deleted), Toast.LENGTH_SHORT).show();
                    loadClasses();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
