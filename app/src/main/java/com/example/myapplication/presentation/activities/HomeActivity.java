package com.example.myapplication.presentation.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.myapplication.R;

public class HomeActivity extends AppCompatActivity {
    private boolean isRegistered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        isRegistered = getIntent().getBooleanExtra("isRegistered", false);

        // isRegistered is available here for later use
        // true = logged in user, false = guest
    }
}