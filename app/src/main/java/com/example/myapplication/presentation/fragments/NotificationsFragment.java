package com.example.myapplication.presentation.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.AppNotification;
import com.example.myapplication.data.model.GameInvite;
import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.GameInviteRepository;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.example.myapplication.presentation.viewModel.NotificationsViewModel;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.List;

/**
 * Istorija svih sistemskih obaveštenja: prikaz, filtriranje (sve/nepročitane/pročitane),
 * označavanje kao pročitano i naknadno reagovanje (poziv → dijalog, nagrada → dijalog).
 */
public class NotificationsFragment extends Fragment {

    private NotificationsViewModel vm;
    private HomeViewModel homeVm;
    private final GameInviteRepository inviteRepo = new GameInviteRepository();

    private LinearLayout container;
    private TextView tvEmpty;
    private String uid;

    public NotificationsFragment() {
        super(R.layout.fragment_notifications);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm     = new ViewModelProvider(requireActivity()).get(NotificationsViewModel.class);
        homeVm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        container = view.findViewById(R.id.notifContainer);
        tvEmpty   = view.findViewById(R.id.tvNotifEmpty);

        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleFilter);
        toggle.check(R.id.btnFilterAll);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnFilterUnread) vm.setFilter(NotificationsViewModel.Filter.UNREAD);
            else if (checkedId == R.id.btnFilterRead) vm.setFilter(NotificationsViewModel.Filter.READ);
            else vm.setFilter(NotificationsViewModel.Filter.ALL);
        });

        vm.visible.observe(getViewLifecycleOwner(), this::render);

        User user = homeVm.currentUser.getValue();
        if (user != null) {
            uid = user.getUid();
            vm.start(uid);
        } else {
            homeVm.currentUser.observe(getViewLifecycleOwner(), u -> {
                if (u != null && uid == null) { uid = u.getUid(); vm.start(uid); }
            });
        }
    }

    private void render(List<AppNotification> items) {
        container.removeAllViews();
        if (items == null || items.isEmpty()) {
            container.addView(tvEmpty);
            tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        for (AppNotification n : items) container.addView(buildRow(n));
    }

    private View buildRow(AppNotification n) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackgroundColor(n.isRead() ? Color.WHITE : Color.parseColor("#E3F2FD"));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView icon = new TextView(requireContext());
        icon.setText(iconFor(n.getType()));
        icon.setTextSize(24);
        icon.setWidth(dp(40));
        icon.setGravity(Gravity.CENTER);
        card.addView(icon);

        LinearLayout mid = new LinearLayout(requireContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(10), 0, dp(8), 0);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(requireContext());
        title.setText(n.getTitle() != null ? n.getTitle() : "");
        title.setTextSize(14);
        title.setTypeface(null, n.isRead() ? Typeface.NORMAL : Typeface.BOLD);
        title.setTextColor(Color.parseColor("#333333"));
        mid.addView(title);

        TextView msg = new TextView(requireContext());
        msg.setText(n.getMessage() != null ? n.getMessage() : "");
        msg.setTextSize(13);
        msg.setTextColor(Color.parseColor("#666666"));
        mid.addView(msg);

        TextView time = new TextView(requireContext());
        time.setText(relativeTime(n.getCreatedAt()));
        time.setTextSize(11);
        time.setTextColor(Color.parseColor("#AAAAAA"));
        mid.addView(time);

        card.addView(mid);

        if (!n.isRead()) {
            TextView dot = new TextView(requireContext());
            dot.setText("●");
            dot.setTextColor(Color.parseColor("#2196F3"));
            dot.setTextSize(12);
            card.addView(dot);
        }

        card.setOnClickListener(v -> onNotificationClicked(n));
        return card;
    }

    private void onNotificationClicked(AppNotification n) {
        if (uid != null && !n.isRead()) vm.markRead(uid, n.getId()); // označi kao pročitano

        // Naknadno reagovanje na obaveštenje
        if (AppNotification.TYPE_FRIEND_INVITE.equals(n.getType())) {
            reactToInvite(n);
        } else if (AppNotification.TYPE_RANK_REWARD.equals(n.getType())) {
            RewardDialogFragment.newInstance(n.getTokens(), n.getRank(), n.isMonthly(), n.getDateRange())
                    .show(getParentFragmentManager(), "reward");
        }
        // RANK_PLACEMENT / LEAGUE_PROMOTION: samo informativno.
    }

    private void reactToInvite(AppNotification n) {
        if (n.getInviteId() == null) return;
        inviteRepo.getInvite(n.getInviteId(), invite -> {
            if (invite != null && GameInvite.STATUS_PENDING.equals(invite.getStatus())) {
                IncomingInviteDialogFragment.newInstance(invite)
                        .show(getParentFragmentManager(), "incomingInvite");
            } else if (isAdded()) {
                Toast.makeText(requireContext(), "Ovaj poziv više nije aktivan.", Toast.LENGTH_SHORT).show();
            }
        }, e -> { /* tiho */ });
    }

    private String iconFor(String type) {
        if (type == null) return "🔔";
        switch (type) {
            case AppNotification.TYPE_FRIEND_INVITE:    return "🎮";
            case AppNotification.TYPE_RANK_REWARD:      return "🏆";
            case AppNotification.TYPE_RANK_PLACEMENT:   return "📊";
            case AppNotification.TYPE_LEAGUE_PROMOTION: return "⬆️";
            default:                                    return "🔔";
        }
    }

    private CharSequence relativeTime(long t) {
        if (t <= 0) return "";
        return DateUtils.getRelativeTimeSpanString(
                t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
