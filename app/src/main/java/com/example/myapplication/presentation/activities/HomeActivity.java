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
import androidx.navigation.NavOptions;

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
        View wrapperStars = findViewById(R.id.wrapperStars);
        View wrapperTokens = findViewById(R.id.wrapperTokens);

        btnMenu.setOnClickListener(v -> toggleDropdown());

        HomeViewModel homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        homeViewModel.loadUser();

        if (!isRegistered) {
            ivLeagueIcon.setVisibility(View.GONE);
            btnMenu.setVisibility(View.GONE);
            if (wrapperStars != null) wrapperStars.setVisibility(View.GONE);
            if (wrapperTokens != null) wrapperTokens.setVisibility(View.GONE);
            return;
        }


        homeViewModel.currentUser.observe(this, user -> {
            if (user != null) {
                tvStars.setText(String.valueOf(user.getStars()));
                tvTokens.setText(String.valueOf(user.getTokens()));
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
        bottomNav.setItemIconTintList(null);


        if (!isRegistered) {
            bottomNav.getMenu().findItem(R.id.homeFragment).setVisible(false);
            bottomNav.getMenu().findItem(R.id.friendsFragment).setVisible(false);
            bottomNav.getMenu().findItem(R.id.notificationsFragment).setVisible(false);
        }


        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.homeFragment) {
                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, false)
                        .setLaunchSingleTop(true)
                        .build();
                navController.navigate(R.id.homeFragment, null, navOptions);
                return true;
            }

            return NavigationUI.onNavDestinationSelected(item, navController);
        });

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
        findViewById(R.id.menuProfile).setOnClickListener(v -> {
            closeDropdown();

            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.homeNavHost);

            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();

                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, false)
                        .setLaunchSingleTop(true)
                        .build();

                navController.navigate(R.id.profileFragment, null, navOptions);
            }
        });

        findViewById(R.id.menuRegions).setOnClickListener(v -> {
            closeDropdown();
        });

        findViewById(R.id.menuTournaments).setOnClickListener(v -> {
            closeDropdown();
        });
    }

    private void closeDropdown() {
        dropdownMenu.setVisibility(View.GONE);
        isMenuOpen = false;
    }

}