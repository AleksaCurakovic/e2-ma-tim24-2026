package com.example.myapplication.presentation.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.example.myapplication.presentation.viewModel.RankingViewModel;
import com.example.myapplication.util.CycleUtil;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.List;

/**
 * Rang lista igrača (nedeljna / mesečna). Prikazuje korisnička imena, ikonu lige i
 * zvezde osvojene u tekućem ciklusu. Automatski se osvežava na svaka 2 minuta.
 */
public class RankListFragment extends Fragment {

    private static final long REFRESH_INTERVAL_MS = 2 * 60 * 1000L; // 2 minuta (tačka d)

    private RankingViewModel rankingVm;
    private HomeViewModel homeVm;

    private LinearLayout rankContainer;
    private TextView tvEmpty;
    private TextView tvDateRange;
    private ProgressBar progress;

    private boolean showMonthly = false;
    private String currentUid = null;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            rankingVm.loadRanking(showMonthly);
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public RankListFragment() {
        super(R.layout.fragment_rank_list);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        rankingVm = new ViewModelProvider(requireActivity()).get(RankingViewModel.class);
        homeVm    = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        rankContainer = view.findViewById(R.id.rankContainer);
        tvEmpty       = view.findViewById(R.id.tvEmpty);
        tvDateRange   = view.findViewById(R.id.tvDateRange);
        progress      = view.findViewById(R.id.rankProgress);

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleCycle);
        toggle.check(R.id.btnWeekly);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            showMonthly = (checkedId == R.id.btnMonthly);
            progress.setVisibility(View.VISIBLE);
            rankingVm.loadRanking(showMonthly);
        });

        // Uid tekućeg korisnika — radi isticanja njegovog reda na listi.
        homeVm.currentUser.observe(getViewLifecycleOwner(), user -> {
            if (user != null) currentUid = user.getUid();
        });

        rankingVm.currentCycleId.observe(getViewLifecycleOwner(), cycleId ->
                tvDateRange.setText("Ciklus: " + CycleUtil.getDateRangeLabel(cycleId)));

        rankingVm.ranking.observe(getViewLifecycleOwner(), entries -> {
            progress.setVisibility(View.GONE);
            renderRanking(entries);
        });

        rankingVm.errorMessage.observe(getViewLifecycleOwner(), msg ->
                progress.setVisibility(View.GONE));

        progress.setVisibility(View.VISIBLE);
        rankingVm.loadRanking(showMonthly);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Pokreni periodično osvežavanje (prvo odmah pri ulasku, pa na svaka 2 minuta).
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void renderRanking(List<RankingEntry> entries) {
        rankContainer.removeAllViews();

        if (entries == null || entries.isEmpty()) {
            rankContainer.addView(tvEmpty);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }

        for (RankingEntry entry : entries) {
            rankContainer.addView(buildRow(entry));
        }
    }

    private View buildRow(RankingEntry entry) {
        boolean isMe = currentUid != null && currentUid.equals(entry.getUid());

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundColor(isMe ? Color.parseColor("#E3F2FD") : Color.WHITE);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        row.setLayoutParams(rowLp);
        row.setElevation(dp(2));

        // Plasman
        TextView tvRank = new TextView(requireContext());
        tvRank.setText(medalFor(entry.getRank()));
        tvRank.setTextSize(16);
        tvRank.setTypeface(null, Typeface.BOLD);
        tvRank.setTextColor(Color.parseColor("#333333"));
        tvRank.setWidth(dp(40));
        tvRank.setGravity(Gravity.CENTER);
        row.addView(tvRank);

        // Ikona lige
        ImageView ivLeague = new ImageView(requireContext());
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        ivLp.setMarginStart(dp(8));
        ivLp.setMarginEnd(dp(12));
        ivLeague.setLayoutParams(ivLp);
        int resId = leagueDrawable(entry.getLeagueIcon());
        if (resId != 0) ivLeague.setImageResource(resId);
        row.addView(ivLeague);

        // Korisničko ime
        TextView tvName = new TextView(requireContext());
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvName.setLayoutParams(nameLp);
        tvName.setText(entry.getUsername() != null ? entry.getUsername() : "—");
        tvName.setTextSize(15);
        tvName.setTextColor(Color.parseColor("#333333"));
        if (isMe) tvName.setTypeface(null, Typeface.BOLD);
        row.addView(tvName);

        // Zvezde osvojene u ciklusu
        TextView tvStars = new TextView(requireContext());
        tvStars.setText(String.valueOf(entry.getStarsEarned()));
        tvStars.setTextSize(15);
        tvStars.setTypeface(null, Typeface.BOLD);
        tvStars.setTextColor(Color.parseColor("#333333"));
        row.addView(tvStars);

        ImageView ivStar = new ImageView(requireContext());
        LinearLayout.LayoutParams starLp = new LinearLayout.LayoutParams(dp(20), dp(20));
        starLp.setMarginStart(dp(4));
        ivStar.setLayoutParams(starLp);
        ivStar.setImageResource(R.drawable.star);
        row.addView(ivStar);

        return row;
    }

    private String medalFor(int rank) {
        switch (rank) {
            case 1: return "🥇";
            case 2: return "🥈";
            case 3: return "🥉";
            default: return String.valueOf(rank);
        }
    }

    private int leagueDrawable(String leagueIcon) {
        if (leagueIcon == null) return 0;
        return getResources().getIdentifier(leagueIcon, "drawable", requireContext().getPackageName());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
