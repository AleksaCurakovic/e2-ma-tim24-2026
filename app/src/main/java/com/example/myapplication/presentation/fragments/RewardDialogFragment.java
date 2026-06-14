package com.example.myapplication.presentation.fragments;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.DialogInterface;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.myapplication.R;
import com.example.myapplication.util.RankingNotificationHelper;
import com.google.android.material.button.MaterialButton;

/**
 * Dialog koji prikazuje osvojenu nagradu na rang listi, sa vizuelnom animacijom
 * (poskakivanje pehara, odbrojavanje tokena) i zvučnim efektom (tačka g).
 */
public class RewardDialogFragment extends DialogFragment {

    /** Ključ rezultata koji se emituje kad se dialog zatvori (za prikaz sledeće nagrade). */
    public static final String RESULT_DISMISSED = "reward_dismissed";

    public static RewardDialogFragment newInstance(int tokens, int rank, boolean monthly, String range) {
        RewardDialogFragment f = new RewardDialogFragment();
        Bundle args = new Bundle();
        args.putInt(RankingNotificationHelper.EXTRA_REWARD_TOKENS, tokens);
        args.putInt(RankingNotificationHelper.EXTRA_REWARD_RANK, rank);
        args.putBoolean(RankingNotificationHelper.EXTRA_REWARD_MONTHLY, monthly);
        args.putString(RankingNotificationHelper.EXTRA_REWARD_RANGE, range);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_reward, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        int tokens   = args.getInt(RankingNotificationHelper.EXTRA_REWARD_TOKENS, 0);
        int rank     = args.getInt(RankingNotificationHelper.EXTRA_REWARD_RANK, 0);
        boolean monthly = args.getBoolean(RankingNotificationHelper.EXTRA_REWARD_MONTHLY, false);
        String range = args.getString(RankingNotificationHelper.EXTRA_REWARD_RANGE, "");

        TextView tvTrophy   = view.findViewById(R.id.tvTrophy);
        TextView tvMessage  = view.findViewById(R.id.tvRewardMessage);
        TextView tvDateRange = view.findViewById(R.id.tvDateRange);
        TextView tvTokens   = view.findViewById(R.id.tvTokens);
        LinearLayout tokensRow = view.findViewById(R.id.tokensRow);
        MaterialButton btnClaim = view.findViewById(R.id.btnClaim);

        String type = monthly ? "mesečnoj" : "nedeljnoj";
        tvMessage.setText("Osvojio si " + rank + ". mesto na " + type + " rang listi.");
        tvDateRange.setText(range);

        btnClaim.setOnClickListener(v -> dismissAllowingStateLoss());

        playSound();
        animateTrophy(tvTrophy);
        animateTokenCount(tvTokens, tokensRow, tokens);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // Obavesti aktivnost da prikaže sledeću nagradu iz reda, ako postoji.
        if (isAdded()) {
            getParentFragmentManager().setFragmentResult(RESULT_DISMISSED, new Bundle());
        }
    }

    private void animateTrophy(View trophy) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(trophy, "scaleX", 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(trophy, "scaleY", 0f, 1f);
        ObjectAnimator rotate = ObjectAnimator.ofFloat(trophy, "rotation", -12f, 12f, -8f, 8f, 0f);
        rotate.setStartDelay(300);
        rotate.setDuration(700);

        AnimatorSet pop = new AnimatorSet();
        pop.setDuration(500);
        pop.setInterpolator(new OvershootInterpolator());
        pop.playTogether(scaleX, scaleY);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(pop, rotate);
        set.start();
    }

    private void animateTokenCount(TextView tvTokens, View tokensRow, int tokens) {
        tokensRow.setAlpha(0f);
        tokensRow.setTranslationY(40f);
        tokensRow.animate().alpha(1f).translationY(0f).setStartDelay(350).setDuration(450).start();

        ValueAnimator counter = ValueAnimator.ofInt(0, tokens);
        counter.setStartDelay(350);
        counter.setDuration(700);
        counter.addUpdateListener(a -> tvTokens.setText("+" + a.getAnimatedValue()));
        counter.start();
    }

    private void playSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            Ringtone ringtone = RingtoneManager.getRingtone(requireContext(), uri);
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {
            // Zvuk je opcioni — vizuelna animacija i dalje radi.
        }
    }
}
