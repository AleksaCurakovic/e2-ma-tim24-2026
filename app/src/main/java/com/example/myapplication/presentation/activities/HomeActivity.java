package com.example.myapplication.presentation.activities;

import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.R;
import com.example.myapplication.data.repository.AuthRepository;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class HomeActivity extends AppCompatActivity {
    private boolean isRegistered;
    private LinearLayout dropdownMenu;
    private boolean isMenuOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        isRegistered = getIntent().getBooleanExtra("isRegistered", false);
        new ViewModelProvider(this).get(
                com.example.myapplication.presentation.viewModel.HomeViewModel.class)
                .setRegistered(isRegistered);
        setupBottomNav();
        setupHeader();
        setupDropdownMenu();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View menu = findViewById(R.id.dropdownMenu);
            View btn = findViewById(R.id.btnMenu);

            if (menu != null && menu.getVisibility() == View.VISIBLE) {
                Rect menuRect = new Rect();
                Rect btnRect = new Rect();

                menu.getGlobalVisibleRect(menuRect);
                btn.getGlobalVisibleRect(btnRect);

                // If touch is NOT on menu AND NOT on the button, close the menu
                if (!menuRect.contains((int)event.getRawX(), (int)event.getRawY()) &&
                        !btnRect.contains((int)event.getRawX(), (int)event.getRawY())) {

                    closeDropdown();
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
    private void setupHeader() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        ImageView ivLeagueIcon = findViewById(R.id.ivLeagueIcon);
        TextView tvStars = findViewById(R.id.tvStars);
        TextView tvTokens = findViewById(R.id.tvTokens);
        dropdownMenu = findViewById(R.id.dropdownMenu);

        btnMenu.setOnClickListener(v -> toggleDropdown());
        HomeViewModel homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        homeViewModel.loadUser();

        if (!isRegistered)
            return;

        homeViewModel.currentUser.observe(this, user -> {
            if (user != null) {
                tvStars.setText(String.valueOf(user.getStars()));
                tvTokens.setText(String.valueOf(user.getTokens()));

                // Resolve league icon from local drawables
                int resId = getResources().getIdentifier(
                        user.getLeagueIcon(), "drawable", getPackageName()
                );
                if (resId != 0) ivLeagueIcon.setImageResource(resId);
            }
        });
    }

    private void toggleDropdown() {
        if (isMenuOpen) {
            dropdownMenu.setVisibility(View.GONE);
        } else {
            dropdownMenu.setVisibility(View.VISIBLE);
        }
        isMenuOpen = !isMenuOpen;
    }

    private void setupBottomNav() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.homeNavHost);
        NavController navController = navHostFragment.getNavController();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        NavigationUI.setupWithNavController(bottomNav, navController);
        bottomNav.setItemIconTintList(null);

        View header = findViewById(R.id.header);
        View bottomNavContainer = findViewById(R.id.bottomNavContainer);

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            if (id == R.id.gameFragment || id == R.id.resultsFragment) {
                header.setVisibility(View.GONE);
                bottomNavContainer.setVisibility(View.GONE);
            } else {
                header.setVisibility(View.VISIBLE);
                bottomNavContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setupDropdownMenu() {
        View dropdownMenu = findViewById(R.id.dropdownMenu);
        ImageButton btnMenu = findViewById(R.id.btnMenu);

        // Toggle menu when clicking the hamburger button
        btnMenu.setOnClickListener(v -> {
            if (dropdownMenu.getVisibility() == View.VISIBLE) {
                dropdownMenu.setVisibility(View.GONE);
            } else {
                dropdownMenu.setVisibility(View.VISIBLE);
                // Ensure it's on top of fragments
                dropdownMenu.bringToFront();
            }
        });

        // Menu Item Click Listeners
        findViewById(R.id.menuProfile).setOnClickListener(v -> {
            closeDropdown();
            // Navigation logic: navController.navigate(R.id.profile_dest);
        });

        findViewById(R.id.menuRegions).setOnClickListener(v -> {
            closeDropdown();
            // Navigation logic: navController.navigate(R.id.regions_dest);
        });

        findViewById(R.id.menuTournaments).setOnClickListener(v -> {
            closeDropdown();
            // Navigation logic: navController.navigate(R.id.tournaments_dest);
        });
    }

    private void closeDropdown() {
        findViewById(R.id.dropdownMenu).setVisibility(View.GONE);
    }

}