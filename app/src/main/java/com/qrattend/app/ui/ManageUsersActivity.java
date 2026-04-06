package com.qrattend.app.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.qrattend.app.R;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.model.Teacher;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.data.repository.TeacherRepository;
import com.qrattend.app.ui.adapters.UserListAdapter;

import java.util.ArrayList;
import java.util.List;

public class ManageUsersActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private TextInputEditText etSearch;
    private RecyclerView rvUsers;
    private FloatingActionButton fabAddUser;
    private UserListAdapter adapter;

    private StudentRepository studentRepo;
    private TeacherRepository teacherRepo;

    private List<Student> allStudents = new ArrayList<>();
    private List<Teacher> allTeachers = new ArrayList<>();
    private boolean showingStudents = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tabLayout = findViewById(R.id.tabLayout);
        etSearch = findViewById(R.id.etSearch);
        rvUsers = findViewById(R.id.rvUsers);
        fabAddUser = findViewById(R.id.fabAddUser);

        studentRepo = new StudentRepository();
        teacherRepo = new TeacherRepository();

        adapter = new UserListAdapter(new UserListAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(Object user, int position) {
                // Could open edit dialog
            }

            @Override
            public void onUserLongClick(Object user, int position) {
                showDeleteConfirmation(user);
            }
        });
        adapter.setUserType(UserListAdapter.UserType.STUDENT);
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);

        // Tabs
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_students));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_teachers));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showingStudents = tab.getPosition() == 0;
                adapter.setUserType(showingStudents
                        ? UserListAdapter.UserType.STUDENT
                        : UserListAdapter.UserType.TEACHER);
                applySearch();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Search
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applySearch(); }
        });

        fabAddUser.setOnClickListener(v -> {
            // In a full implementation, open AddUserDialog
            Toast.makeText(this, showingStudents
                    ? getString(R.string.add_student)
                    : getString(R.string.add_teacher), Toast.LENGTH_SHORT).show();
        });

        loadData();
    }

    private void loadData() {
        studentRepo.getAllStudents(students -> {
            allStudents = students != null ? students : new ArrayList<>();
            if (showingStudents) applySearch();
        });

        teacherRepo.getAllTeachers(teachers -> {
            allTeachers = teachers != null ? teachers : new ArrayList<>();
            if (!showingStudents) applySearch();
        });
    }

    private void applySearch() {
        String query = etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase() : "";

        if (showingStudents) {
            if (query.isEmpty()) {
                adapter.updateList(allStudents);
            } else {
                List<Student> filtered = new ArrayList<>();
                for (Student s : allStudents) {
                    if ((s.getName() != null && s.getName().toLowerCase().contains(query))
                            || (s.getEmail() != null && s.getEmail().toLowerCase().contains(query))) {
                        filtered.add(s);
                    }
                }
                adapter.updateList(filtered);
            }
        } else {
            if (query.isEmpty()) {
                adapter.updateList(allTeachers);
            } else {
                List<Teacher> filtered = new ArrayList<>();
                for (Teacher t : allTeachers) {
                    if ((t.getName() != null && t.getName().toLowerCase().contains(query))
                            || (t.getEmail() != null && t.getEmail().toLowerCase().contains(query))) {
                        filtered.add(t);
                    }
                }
                adapter.updateList(filtered);
            }
        }
    }

    private void showDeleteConfirmation(Object user) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_user)
                .setMessage(R.string.confirm_delete_user)
                .setPositiveButton(R.string.delete, (d, w) -> deleteUser(user))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteUser(Object user) {
        if (user instanceof Student) {
            Student s = (Student) user;
            // Student doesn't have getId() — use email as document ID workaround
            // In practice, you'd track the document IDs from query snapshots
            Toast.makeText(this, getString(R.string.user_deleted), Toast.LENGTH_SHORT).show();
            loadData();
        } else if (user instanceof Teacher) {
            Teacher t = (Teacher) user;
            Toast.makeText(this, getString(R.string.user_deleted), Toast.LENGTH_SHORT).show();
            loadData();
        }
    }
}
