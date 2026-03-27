package com.qrattend.app;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // This line links to your UI

        // Initialize Firebase
        FirebaseApp.initializeApp(this);

        // If the app reaches this line without crashing, you win!
        Toast.makeText(this, "Firebase Connected Successfully!", Toast.LENGTH_LONG).show();
    }
}