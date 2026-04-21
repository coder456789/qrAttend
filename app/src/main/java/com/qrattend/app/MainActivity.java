package com.qrattend.app;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.R;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }
}