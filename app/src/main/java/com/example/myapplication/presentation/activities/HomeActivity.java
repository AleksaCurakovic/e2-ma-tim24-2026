package com.example.myapplication.presentation.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameInvite;
import com.example.myapplication.data.model.RankReward;
import com.example.myapplication.data.repository.AuthRepository;
import com.example.myapplication.data.repository.GameInviteRepository;
import com.example.myapplication.data.repository.GameRepository;
import com.example.myapplication.presentation.fragments.IncomingInviteDialogFragment;
import com.example.myapplication.presentation.fragments.RewardDialogFragment;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.example.myapplication.presentation.viewModel.RankingViewModel;
import com.example.myapplication.service.InviteForegroundService;
import com.example.myapplication.util.InviteNotificationHelper;
import com.example.myapplication.util.RankingNotificationHelper;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.navigation.NavOptions;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {
    private boolean isRegistered;
    private LinearLayout dropdownMenu;
    private boolean isMenuOpen = false;

    private RankingViewModel rankingViewModel;
    private final Deque<RankReward> rewardQueue = new ArrayDeque<>();
    private boolean rewardDialogShowing = false;

    // Dolazne pozivnice za partiju (strana primaoca).
    private final GameInviteRepository inviteRepository = new GameInviteRepository();
    private final GameRepository gameRepository = new GameRepository();
    private final AuthRepository authRepository = new AuthRepository();
    private final Set<String> handledInviteIds = new HashSet<>();
    private boolean inviteDialogShowing = false;
    private boolean inviteServiceStarted = false;
    private String myUid;

    // Heartbeat prisustva: dok je app u prvom planu osvežavamo lastSeen, da bi prijatelji
    // videli korisnika offline i kad je app naglo ugašena (heartbeat stane → status zastari).
    private static final long PRESENCE_HEARTBEAT_MS = 30_000L;
    private final android.os.Handler presenceHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable presenceHeartbeat = new Runnable() {
        @Override public void run() {
            authRepository.heartbeat();
            presenceHandler.postDelayed(this, PRESENCE_HEARTBEAT_MS);
        }
    };

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        isRegistered = getIntent().getBooleanExtra("isRegistered", false);
        new ViewModelProvider(this).get(
                com.example.myapplication.presentation.viewModel.HomeViewModel.class)
                .setRegistered(isRegistered);
        rankingViewModel = new ViewModelProvider(this).get(RankingViewModel.class);
        setupBottomNav();
        setupHeader();
        setupDropdownMenu();
        setupRankingRewards();

        // Dialog otvoren klikom na sistemsku notifikaciju o nagradi.
        handleRewardIntent(getIntent());
        handleInviteIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleRewardIntent(intent);
        handleInviteIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Dok je aktivnost u prvom planu, pozivnice prikazujemo kao dijalog (umesto notifikacije).
        if (isRegistered) {
            InviteForegroundService.setForegroundListener(this::showIncomingInviteDialog);
            // "Online" = korisnik je trenutno u aplikaciji: postavi loggedIn=true + lastSeen,
            // pa periodično osvežavaj lastSeen dok je app u prvom planu.
            authRepository.markOnline(unused -> {}, e -> {});
            presenceHandler.removeCallbacks(presenceHeartbeat);
            presenceHandler.postDelayed(presenceHeartbeat, PRESENCE_HEARTBEAT_MS);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        InviteForegroundService.setForegroundListener(null);
        // Rotacija/izmena konfiguracije nije napuštanje aplikacije — ne menjamo status.
        if (isRegistered && !isChangingConfigurations()) {
            // Korisnik je napustio aplikaciju (pozadina) → "offline".
            presenceHandler.removeCallbacks(presenceHeartbeat);
            authRepository.setLoggedIn(false, unused -> {}, e -> {});
        }
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

            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.homeNavHost);

            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();
                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, false)
                        .setLaunchSingleTop(true)
                        .build();
                navController.navigate(R.id.chatFragment, null, navOptions);
            }
        });


        findViewById(R.id.menuRankList).setOnClickListener(v -> {
            closeDropdown();

            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.homeNavHost);

            if (navHostFragment != null) {
                NavController navController = navHostFragment.getNavController();

                NavOptions navOptions = new NavOptions.Builder()
                        .setPopUpTo(R.id.homeFragment, false)
                        .setLaunchSingleTop(true)
                        .build();

                navController.navigate(R.id.rankListFragment, null, navOptions);
            }
        });
    }

    private void closeDropdown() {
        dropdownMenu.setVisibility(View.GONE);
        isMenuOpen = false;
    }

    // ------------------------------------------------------------- RANG LISTE

    private boolean rewardsFinalized = false;

    private void setupRankingRewards() {
        if (!isRegistered) return;

        RankingNotificationHelper.ensureChannel(this);
        requestNotificationPermissionIfNeeded();

        HomeViewModel homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        // Kad se korisnik učita, jednom po sesiji proveri i dodeli nagrade za protekle cikluse,
        // i pokreni slušanje dolaznih pozivnica za partiju.
        homeViewModel.currentUser.observe(this, user -> {
            if (user != null && !rewardsFinalized) {
                rewardsFinalized = true;
                rankingViewModel.finalizeRewards(user);
            }
            if (user != null && user.getUid() != null && !inviteServiceStarted) {
                inviteServiceStarted = true;
                myUid = user.getUid();
                // Foreground servis prima pozivnice i kad je aplikacija u pozadini.
                InviteForegroundService.start(this, myUid);
            }
        });

        // Primalac prihvatio poziv i soba je spremna → pokreni partiju.
        getSupportFragmentManager().setFragmentResultListener(
                IncomingInviteDialogFragment.RESULT_ACCEPTED, this, (key, bundle) -> {
                    inviteDialogShowing = false;
                    String gameId = bundle.getString(IncomingInviteDialogFragment.ARG_GAME_ID);
                    String myUsername = bundle.getString(IncomingInviteDialogFragment.ARG_TO_NAME);
                    homeViewModel.setInGame(true);
                    navigateToGameFragment(gameId, myUsername);
                });

        // Dialog zatvoren (odbijeno/isteklo/otkazano) → dozvoli buduće pozivnice.
        getSupportFragmentManager().setFragmentResultListener(
                IncomingInviteDialogFragment.RESULT_DISMISSED, this, (key, bundle) ->
                        inviteDialogShowing = false);

        // Osvojene nagrade: sistemska notifikacija + dialog sa animacijom/zvukom.
        rankingViewModel.rewards.observe(this, rewards -> {
            if (rewards == null || rewards.isEmpty()) return;
            for (RankReward reward : rewards) {
                RankingNotificationHelper.showRewardNotification(this, reward);
                rewardQueue.add(reward);
            }
            // Osveži zaglavlje (broj tokena se promenio nakon dodele).
            homeViewModel.loadUser();
            showNextReward();
        });

        getSupportFragmentManager().setFragmentResultListener(
                RewardDialogFragment.RESULT_DISMISSED, this, (key, bundle) -> {
                    rewardDialogShowing = false;
                    showNextReward();
                });
    }

    private void showNextReward() {
        if (rewardDialogShowing) return;
        RankReward reward = rewardQueue.poll();
        if (reward == null) return;
        rewardDialogShowing = true;
        RewardDialogFragment.newInstance(reward.tokens, reward.rank, reward.monthly, reward.dateRange)
                .show(getSupportFragmentManager(), "reward");
    }

    private void handleRewardIntent(Intent intent) {
        if (intent == null) return;
        if (!intent.getBooleanExtra(RankingNotificationHelper.EXTRA_SHOW_REWARD, false)) return;
        // Spreči ponovno prikazivanje pri rotaciji / ponovnom ulasku.
        intent.removeExtra(RankingNotificationHelper.EXTRA_SHOW_REWARD);

        RankReward reward = new RankReward(
                intent.getBooleanExtra(RankingNotificationHelper.EXTRA_REWARD_MONTHLY, false),
                null,
                intent.getIntExtra(RankingNotificationHelper.EXTRA_REWARD_RANK, 0),
                intent.getIntExtra(RankingNotificationHelper.EXTRA_REWARD_TOKENS, 0),
                intent.getStringExtra(RankingNotificationHelper.EXTRA_REWARD_RANGE));
        rewardQueue.add(reward);
        showNextReward();
    }

    /** Prikazuje dijalog dolazne pozivnice (kad je aplikacija u prvom planu). */
    private void showIncomingInviteDialog(GameInvite invite) {
        if (invite == null || invite.getId() == null) return;
        if (handledInviteIds.contains(invite.getId())) return;
        if (inviteDialogShowing) return;
        handledInviteIds.add(invite.getId());
        inviteDialogShowing = true;
        IncomingInviteDialogFragment.newInstance(invite)
                .show(getSupportFragmentManager(), "incomingInvite");
    }

    /** Obrada otvaranja preko notifikacije pozivnice (telo = prikaži dijalog, akcija = prihvati). */
    private void handleInviteIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra(InviteNotificationHelper.EXTRA_ACTION);
        if (action == null) return;
        intent.removeExtra(InviteNotificationHelper.EXTRA_ACTION);

        String inviteId   = intent.getStringExtra(InviteNotificationHelper.EXTRA_INVITE_ID);
        String gameId     = intent.getStringExtra(InviteNotificationHelper.EXTRA_GAME_ID);
        String fromName   = intent.getStringExtra(InviteNotificationHelper.EXTRA_FROM);
        String toUsername = intent.getStringExtra(InviteNotificationHelper.EXTRA_TO);
        if (inviteId == null || gameId == null) return;

        InviteNotificationHelper.cancelInviteNotification(this, inviteId);

        if (InviteNotificationHelper.ACTION_ACCEPT.equals(action)) {
            acceptInviteFromNotification(inviteId, gameId, toUsername);
        } else {
            GameInvite invite = new GameInvite();
            invite.setId(inviteId);
            invite.setGameId(gameId);
            invite.setFromUsername(fromName);
            invite.setToUsername(toUsername);
            showIncomingInviteDialog(invite);
        }
    }

    /** Prihvatanje pozivnice direktno iz notifikacije: čeka sobu i ulazi u partiju. */
    private void acceptInviteFromNotification(String inviteId, String gameId, String toUsername) {
        handledInviteIds.add(inviteId);
        HomeViewModel homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        inviteRepository.updateStatus(inviteId, GameInvite.STATUS_ACCEPTED, unused -> {
            homeViewModel.setInGame(true);
            gameRepository.listenToGameRoom(gameId, room -> {
                gameRepository.detachListeners();
                navigateToGameFragment(gameId, toUsername);
            }, e -> {});
        }, e -> {});
    }

    private void navigateToGameFragment(String gameId, String myUsername) {
        if (gameId == null) return;
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.homeNavHost);
        if (navHostFragment == null) return;
        NavController navController = navHostFragment.getNavController();
        Bundle args = new Bundle();
        args.putString("gameId", gameId);
        args.putString("myUsername", myUsername != null ? myUsername : "");
        navController.navigate(R.id.gameFragment, args);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}