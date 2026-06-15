package com.example.myapplication.presentation.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.FriendProfile;
import com.example.myapplication.data.model.User;
import com.example.myapplication.presentation.viewModel.FriendsViewModel;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.google.android.material.button.MaterialButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment {

    private final int[] avatarDrawables = {
            R.drawable.friends, R.drawable.business, R.drawable.star
    };

    private FriendsViewModel friendsVm;
    private HomeViewModel homeVm;
    private GameViewModel gameVm;

    private LinearLayout friendsContainer;
    private LinearLayout searchContainer;
    private TextView tvSearchHeader;
    private TextView tvFriendsEmpty;

    private AlertDialog waitingDialog;
    private final List<String> friendUids = new ArrayList<>();

    private final ActivityResultLauncher<ScanOptions> qrScanner =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() == null) return;
                String friendUid = parseFriendUid(result.getContents());
                User me = homeVm.currentUser.getValue();
                if (me == null) return;
                friendsVm.addFriendByUid(me.getUid(), friendUid, friendUids, this::loadFriends);
            });

    public FriendsFragment() {
        super(R.layout.fragment_friends);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        friendsVm = new ViewModelProvider(requireActivity()).get(FriendsViewModel.class);
        homeVm = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        gameVm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        friendsContainer = view.findViewById(R.id.friendsContainer);
        searchContainer = view.findViewById(R.id.searchContainer);
        tvSearchHeader = view.findViewById(R.id.tvSearchHeader);
        tvFriendsEmpty = view.findViewById(R.id.tvFriendsEmpty);

        EditText etSearch = view.findViewById(R.id.etSearch);
        MaterialButton btnSearch = view.findViewById(R.id.btnSearch);
        MaterialButton btnScanQr = view.findViewById(R.id.btnScanQr);

        btnSearch.setOnClickListener(v -> {
            String q = etSearch.getText().toString().trim();
            if (q.isEmpty()) {
                Toast.makeText(requireContext(), "Unesi korisnicko ime", Toast.LENGTH_SHORT).show();
                return;
            }
            User me = homeVm.currentUser.getValue();
            friendsVm.search(q, me != null ? me.getUid() : null, friendUids);
        });

        btnScanQr.setOnClickListener(v -> {
            ScanOptions options = new ScanOptions();
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
            options.setPrompt("Skeniraj QR kod prijatelja");
            options.setBeepEnabled(false);
            options.setOrientationLocked(false);
            qrScanner.launch(options);
        });

        friendsVm.friends.observe(getViewLifecycleOwner(), this::renderFriends);
        friendsVm.searchResults.observe(getViewLifecycleOwner(), this::renderSearchResults);
        friendsVm.message.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });
        friendsVm.inviteEvent.observe(getViewLifecycleOwner(), this::onInviteEvent);

        loadFriends();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFriends();
    }

    private void loadFriends() {
        User me = homeVm.currentUser.getValue();
        if (me != null) friendsVm.loadFriends(me.getUid());
    }

    private void renderFriends(List<FriendProfile> profiles) {
        friendsContainer.removeAllViews();
        friendUids.clear();

        if (profiles == null || profiles.isEmpty()) {
            tvFriendsEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvFriendsEmpty.setVisibility(View.GONE);
        for (FriendProfile p : profiles) {
            if (p.user.getUid() != null) friendUids.add(p.user.getUid());
            friendsContainer.addView(buildFriendCard(p));
        }
    }

    private void renderSearchResults(List<User> users) {
        searchContainer.removeAllViews();
        if (users == null || users.isEmpty()) {
            tvSearchHeader.setVisibility(View.VISIBLE);
            TextView none = new TextView(requireContext());
            none.setText("Nema rezultata.");
            none.setTextColor(Color.parseColor("#64748B"));
            none.setPadding(dp(4), dp(8), 0, 0);
            searchContainer.addView(none);
            return;
        }
        tvSearchHeader.setVisibility(View.VISIBLE);
        for (User u : users) searchContainer.addView(buildSearchRow(u));
    }

    private View buildSearchRow(User u) {
        LinearLayout row = card();
        LinearLayout inner = horizontalInner();

        ImageView avatar = new ImageView(requireContext());
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        avLp.setMarginEnd(dp(12));
        avatar.setLayoutParams(avLp);
        avatar.setPadding(dp(5), dp(5), dp(5), dp(5));
        avatar.setImageResource(avatarFor(u.getAvatarId()));
        applyAvatarFrame(avatar, u.getAvatarFrameColor());
        inner.addView(avatar);

        TextView name = new TextView(requireContext());
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        name.setText(u.getUsername());
        name.setTextSize(15);
        name.setTextColor(Color.parseColor("#111827"));
        inner.addView(name);

        MaterialButton add = new MaterialButton(requireContext());
        add.setText("Dodaj");
        add.setOnClickListener(v -> {
            User me = homeVm.currentUser.getValue();
            if (me == null) return;
            add.setEnabled(false);
            friendsVm.addFriend(me.getUid(), u, () -> {
                searchContainer.removeView(row);
                loadFriends();
            });
        });
        inner.addView(add);

        row.addView(inner);
        return row;
    }

    private View buildFriendCard(FriendProfile p) {
        User u = p.user;
        LinearLayout card = card();
        LinearLayout inner = horizontalInner();

        ImageView avatar = new ImageView(requireContext());
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        avLp.setMarginEnd(dp(12));
        avatar.setLayoutParams(avLp);
        avatar.setPadding(dp(6), dp(6), dp(6), dp(6));
        avatar.setImageResource(avatarFor(u.getAvatarId()));
        applyAvatarFrame(avatar, u.getAvatarFrameColor());
        inner.addView(avatar);

        LinearLayout mid = new LinearLayout(requireContext());
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(requireContext());
        name.setText(u.getUsername());
        name.setTextSize(16);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(Color.parseColor("#111827"));
        mid.addView(name);

        TextView details = new TextView(requireContext());
        String rankText = p.monthlyRank > 0 ? (p.monthlyRank + ". mesto") : "nije rangiran";
        details.setText("Mesecni rang: " + rankText + "  |  zvezde: " + u.getStars());
        details.setTextSize(13);
        details.setTextColor(Color.parseColor("#475569"));
        mid.addView(details);

        LinearLayout leagueRow = new LinearLayout(requireContext());
        leagueRow.setOrientation(LinearLayout.HORIZONTAL);
        leagueRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView leagueIcon = new ImageView(requireContext());
        LinearLayout.LayoutParams liLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        liLp.setMarginEnd(dp(4));
        leagueIcon.setLayoutParams(liLp);
        int resId = leagueDrawable(u.getLeagueIcon());
        if (resId != 0) leagueIcon.setImageResource(resId);
        leagueRow.addView(leagueIcon);
        TextView league = new TextView(requireContext());
        league.setText(u.getLeagueName() != null ? u.getLeagueName() : "");
        league.setTextSize(12);
        league.setTextColor(Color.parseColor("#64748B"));
        leagueRow.addView(league);
        mid.addView(leagueRow);
        inner.addView(mid);

        LinearLayout right = new LinearLayout(requireContext());
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView status = new TextView(requireContext());
<<<<<<< HEAD
        boolean online = isOnline(u);
        boolean canPlay = online && !u.isInGame();
        if (!online) {
            status.setText("● offline");
            status.setTextColor(Color.parseColor("#9E9E9E"));
=======
        boolean canPlay = u.isLoggedIn() && !u.isInGame();
        if (!u.isLoggedIn()) {
            status.setText("offline");
            status.setTextColor(Color.parseColor("#64748B"));
>>>>>>> d026c18b7da3ca82fb5670043f94c763166d16a3
        } else if (u.isInGame()) {
            status.setText("u partiji");
            status.setTextColor(Color.parseColor("#B45309"));
        } else {
            status.setText("online");
            status.setTextColor(Color.parseColor("#15803D"));
        }
        status.setTextSize(12);
        right.addView(status);

        MaterialButton play = new MaterialButton(requireContext());
        play.setText("Igraj");
        play.setEnabled(canPlay);
        play.setTextSize(12);
        play.setOnClickListener(v -> {
            User me = homeVm.currentUser.getValue();
            if (me == null) return;
            friendsVm.sendInvite(me, u);
        });
        right.addView(play);

        inner.addView(right);
        card.addView(inner);
        return card;
    }

    private void onInviteEvent(FriendsViewModel.InviteEvent event) {
        if (event == null) return;
        switch (event.type) {
            case WAITING:
                showWaitingDialog(event.friendName);
                break;
            case START:
                dismissWaitingDialog();
                homeVm.setInGame(true);
                navigateToGame(event.gameId, event.myUsername);
                break;
            case REJECTED:
                dismissWaitingDialog();
                Toast.makeText(requireContext(),
                        (event.friendName != null ? event.friendName : "Igrac") + " je odbio poziv.",
                        Toast.LENGTH_SHORT).show();
                break;
            case EXPIRED:
                dismissWaitingDialog();
                Toast.makeText(requireContext(), "Poziv je istekao.", Toast.LENGTH_SHORT).show();
                break;
            case CANCELLED:
            case ERROR:
                dismissWaitingDialog();
                break;
        }
    }

    private void showWaitingDialog(String friendName) {
        dismissWaitingDialog();
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_invite_waiting, null);
        TextView msg = content.findViewById(R.id.tvWaitingMessage);
        msg.setText("Ceka se da " + friendName + " prihvati poziv...");
        MaterialButton cancel = content.findViewById(R.id.btnCancelInvite);
        cancel.setOnClickListener(v -> friendsVm.cancelInvite());

        waitingDialog = new AlertDialog.Builder(requireContext())
                .setView(content)
                .setCancelable(false)
                .create();
        waitingDialog.show();
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) waitingDialog.dismiss();
        waitingDialog = null;
    }

    private void navigateToGame(String gameId, String myUsername) {
        gameVm.myUsername.setValue(myUsername);
        Bundle b = new Bundle();
        b.putString("gameId", gameId);
        b.putString("myUsername", myUsername != null ? myUsername : "");
        Navigation.findNavController(requireView()).navigate(R.id.gameFragment, b);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissWaitingDialog();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(Color.WHITE, Color.parseColor("#E2E8F0")));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout horizontalInner() {
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(dp(14), dp(12), dp(14), dp(12));
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return inner;
    }

    /**
     * Online je samo korisnik koji je prijavljen I čiji je heartbeat svež. Time se
     * korisnik prikazuje offline i kad je app naglo ugašena (heartbeat prestane, lastSeen
     * zastari) — ne samo kad se eksplicitno izloguje.
     */
    private static final long ONLINE_THRESHOLD_MS = 90_000L;

    private boolean isOnline(User u) {
        return u.isLoggedIn()
                && (System.currentTimeMillis() - u.getLastSeen() < ONLINE_THRESHOLD_MS);
    }

    private int avatarFor(int avatarId) {
        if (avatarId < 0 || avatarId >= avatarDrawables.length) return avatarDrawables[0];
        return avatarDrawables[avatarId];
    }

    private int leagueDrawable(String leagueIcon) {
        if (leagueIcon == null) return 0;
        return getResources().getIdentifier(leagueIcon, "drawable", requireContext().getPackageName());
    }

    private void applyAvatarFrame(ImageView avatar, String frame) {
        int strokeColor;
        if ("gold".equals(frame)) strokeColor = Color.parseColor("#F59E0B");
        else if ("silver".equals(frame)) strokeColor = Color.parseColor("#94A3B8");
        else if ("bronze".equals(frame)) strokeColor = Color.parseColor("#B45309");
        else strokeColor = Color.parseColor("#CBD5E1");
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.WHITE);
        bg.setStroke(4, strokeColor);
        avatar.setBackground(bg);
    }

    private GradientDrawable rounded(int fill, int stroke) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(12));
        bg.setColor(fill);
        bg.setStroke(1, stroke);
        return bg;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String parseFriendUid(String content) {
        if (content == null) return "";
        String value = content.trim();
        if (value.startsWith("FRIEND:")) return value.substring("FRIEND:".length()).trim();
        return value;
    }
}
