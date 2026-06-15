package com.example.myapplication.presentation.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.presentation.activities.AuthActivity;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.example.myapplication.util.LeagueUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class ProfileFragment extends Fragment {

    private final int[] avatarDrawables = {
            R.drawable.friends,
            R.drawable.business,
            R.drawable.star
    };

    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        TextView tvUsername = view.findViewById(R.id.tvProfileUsername);
        TextView tvEmail = view.findViewById(R.id.tvProfileEmail);
        TextView tvLeague = view.findViewById(R.id.tvProfileLeague);
        TextView tvTokens = view.findViewById(R.id.tvProfileTokens);
        TextView tvStars = view.findViewById(R.id.tvProfileStars);
        TextView tvRegion = view.findViewById(R.id.tvProfileRegion);
        ImageView ivLeague = view.findViewById(R.id.ivProfileLeagueIcon);
        ImageView ivAvatar = view.findViewById(R.id.ivProfileAvatar);
        ImageView ivQr = view.findViewById(R.id.ivProfileQr);
        TextView tvStatsGames = view.findViewById(R.id.tvStatsGames);
        TextView tvStatsWinLoss = view.findViewById(R.id.tvStatsWinLoss);
        TextView tvStatsQuiz = view.findViewById(R.id.tvStatsQuiz);
        TextView tvStatsSpojnice = view.findViewById(R.id.tvStatsSpojnice);
        TextView tvStatsAsocijacije = view.findViewById(R.id.tvStatsAsocijacije);
        TextView tvLeagueProgress = view.findViewById(R.id.tvLeagueProgress);
        TextView tvFrameBadge = view.findViewById(R.id.tvFrameBadge);

        view.findViewById(R.id.btnAvatarFriends).setOnClickListener(v -> viewModel.updateAvatar(0));
        view.findViewById(R.id.btnAvatarBusiness).setOnClickListener(v -> viewModel.updateAvatar(1));
        view.findViewById(R.id.btnAvatarStar).setOnClickListener(v -> viewModel.updateAvatar(2));

        Button btnLogout = view.findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            // Zaustavi foreground servis za pozivnice pre odjave.
            com.example.myapplication.service.InviteForegroundService.stop(requireContext());
            viewModel.logout();
            Intent intent = new Intent(requireActivity(), AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            requireActivity().finish();
        });

        EditText etOld = view.findViewById(R.id.etOldPassword);
        EditText etNew = view.findViewById(R.id.etNewPassword);
        EditText etConfirm = view.findViewById(R.id.etConfirmPassword);
        Button btnChange = view.findViewById(R.id.btnChangePassword);

        btnChange.setOnClickListener(v -> {
            String oldPass = etOld.getText().toString().trim();
            String newPass = etNew.getText().toString().trim();
            String confirmPass = etConfirm.getText().toString().trim();

            if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Popunite sva polja.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPass.equals(confirmPass)) {
                etConfirm.setError("Sifre nisu iste.");
                return;
            }
            if (newPass.length() < 8) {
                etNew.setError("Sifra mora imati najmanje 8 karaktera.");
                return;
            }

            btnChange.setEnabled(false);
            viewModel.changePassword(oldPass, newPass);
        });

        viewModel.currentUser.observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;

            tvUsername.setText(user.getUsername());
            tvEmail.setText(user.getEmail() != null ? user.getEmail() : "Gost");
            tvLeague.setText(user.getLeagueName() != null ? user.getLeagueName() : user.getLeagueIcon());
            tvTokens.setText("Tokeni: " + user.getTokens());
            tvStars.setText("Zvezde: " + user.getStars());
            tvRegion.setText("Region: " + (user.getRegion() != null ? user.getRegion() : "-"));
            ivAvatar.setImageResource(avatarFor(user.getAvatarId()));
            applyAvatarFrame(ivAvatar, user.getAvatarFrameColor());
            String qrContent = user.getQrCode() != null && !user.getQrCode().isEmpty()
                    ? user.getQrCode()
                    : "FRIEND:" + user.getUid();
            ivQr.setImageBitmap(createFriendCodeBitmap(qrContent));

            int resId = requireContext().getResources().getIdentifier(
                    user.getLeagueIcon(), "drawable", requireContext().getPackageName());
            if (resId != 0) ivLeague.setImageResource(resId);

            int level = user.getLeagueLevel() > 0
                    ? user.getLeagueLevel()
                    : LeagueUtil.levelForStars(user.getStars());
            int next = LeagueUtil.nextThresholdForLevel(level);
            if (next < 0) {
                tvLeagueProgress.setText("Najvisa liga dostignuta");
            } else {
                int missing = Math.max(0, next - user.getStars());
                tvLeagueProgress.setText("Do sledece lige: " + missing + " zvezda");
            }
            String frame = user.getAvatarFrameColor();
            if (frame != null && !frame.isEmpty()) {
                tvFrameBadge.setVisibility(View.VISIBLE);
                tvFrameBadge.setText("Okvir avatara: " + frameLabel(frame));
            } else {
                tvFrameBadge.setVisibility(View.GONE);
            }

            int total = user.getTotalGames();
            int won = user.getWonGames();
            int lost = user.getLostGames();
            int winPercent = total == 0 ? 0 : Math.round((won * 100f) / total);
            int lossPercent = total == 0 ? 0 : Math.round((lost * 100f) / total);
            tvStatsGames.setText("Ukupno odigranih partija: " + total);
            tvStatsWinLoss.setText("Pobede/porazi: " + won + "/" + lost
                    + " (" + winPercent + "% / " + lossPercent + "%)");
            tvStatsQuiz.setText("Ko zna zna: rezultat ulazi u ukupan skor partije.");
            tvStatsSpojnice.setText("Spojnice: povezani pojmovi donose poene u partiji.");
            tvStatsAsocijacije.setText("Asocijacije: resenja kolona i konacno resenje donose poene.");
        });

        viewModel.passwordChangeResult.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            btnChange.setEnabled(true);
            if (result.equals("success")) {
                Toast.makeText(requireContext(), "Sifra promenjena uspesno.", Toast.LENGTH_SHORT).show();
                etOld.setText("");
                etNew.setText("");
                etConfirm.setText("");
            } else {
                String msg = result.startsWith("error:") ? result.substring(6) : result;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }

            viewModel.passwordChangeResult.setValue(null);
        });

        viewModel.errorMessage.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                viewModel.errorMessage.setValue(null);
            }
        });

        viewModel.loadUser();
    }

    private int avatarFor(int avatarId) {
        if (avatarId < 0 || avatarId >= avatarDrawables.length) return avatarDrawables[0];
        return avatarDrawables[avatarId];
    }

    private Bitmap createFriendCodeBitmap(String content) {
        try {
            return new BarcodeEncoder().encodeBitmap(content, BarcodeFormat.QR_CODE, 360, 360);
        } catch (WriterException e) {
            return createFallbackCodeBitmap(content);
        }
    }

    private Bitmap createFallbackCodeBitmap(String content) {
        int size = 240;
        int cells = 15;
        int cell = size / cells;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawColor(Color.WHITE);

        int seed = content != null ? content.hashCode() : 0;
        for (int y = 0; y < cells; y++) {
            for (int x = 0; x < cells; x++) {
                boolean finder = (x < 4 && y < 4) || (x > 10 && y < 4) || (x < 4 && y > 10);
                boolean filled = finder || ((((seed >> ((x + y) % 24)) ^ (x * 31 + y * 17)) & 1) == 1);
                paint.setColor(filled ? Color.BLACK : Color.WHITE);
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint);
            }
        }
        return bitmap;
    }

    private void applyAvatarFrame(ImageView avatar, String frame) {
        int strokeColor;
        if ("gold".equals(frame)) {
            strokeColor = Color.parseColor("#F59E0B");
        } else if ("silver".equals(frame)) {
            strokeColor = Color.parseColor("#94A3B8");
        } else if ("bronze".equals(frame)) {
            strokeColor = Color.parseColor("#B45309");
        } else {
            strokeColor = Color.parseColor("#CBD5E1");
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.WHITE);
        bg.setStroke(5, strokeColor);
        avatar.setBackground(bg);
    }

    private String frameLabel(String frame) {
        if ("gold".equals(frame)) return "zlatni";
        if ("silver".equals(frame)) return "srebrni";
        if ("bronze".equals(frame)) return "bronzani";
        return frame;
    }
}
