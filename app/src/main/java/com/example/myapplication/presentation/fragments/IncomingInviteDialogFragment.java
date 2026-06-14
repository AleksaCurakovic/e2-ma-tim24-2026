package com.example.myapplication.presentation.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameInvite;
import com.example.myapplication.data.repository.GameInviteRepository;
import com.example.myapplication.data.repository.GameRepository;
import com.google.android.material.button.MaterialButton;

/**
 * Dialog koji se prikazuje primaocu pozivnice za partiju. Ima 10s odbrojavanje
 * nakon kojeg se poziv automatski odbija. Ako pošiljalac u međuvremenu prekine
 * zahtev, dialog se sam zatvara.
 */
public class IncomingInviteDialogFragment extends DialogFragment {

    public static final String RESULT_ACCEPTED  = "invite_accepted";
    public static final String RESULT_DISMISSED = "invite_dismissed";
    public static final String ARG_INVITE_ID   = "inviteId";
    public static final String ARG_FROM_NAME   = "fromUsername";
    public static final String ARG_FROM_AVATAR = "fromAvatar";
    public static final String ARG_GAME_ID     = "gameId";
    public static final String ARG_TO_NAME     = "toUsername";

    private static final int[] AVATARS = {R.drawable.friends, R.drawable.business, R.drawable.star};
    private static final long TIMEOUT_MS = 10_000L;

    private final GameInviteRepository inviteRepo = new GameInviteRepository();
    private final GameRepository gameRepo = new GameRepository();

    private CountDownTimer timer;
    private String inviteId, gameId, toUsername;
    private boolean resolved = false; // sprečava dvostruku obradu (accept/reject/timeout)

    public static IncomingInviteDialogFragment newInstance(GameInvite invite) {
        IncomingInviteDialogFragment f = new IncomingInviteDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_INVITE_ID, invite.getId());
        b.putString(ARG_FROM_NAME, invite.getFromUsername());
        b.putString(ARG_GAME_ID, invite.getGameId());
        b.putString(ARG_TO_NAME, invite.getToUsername());
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_incoming_invite, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        inviteId   = args.getString(ARG_INVITE_ID);
        gameId     = args.getString(ARG_GAME_ID);
        toUsername = args.getString(ARG_TO_NAME);
        String fromName = args.getString(ARG_FROM_NAME, "Igrač");
        int avatarIdx   = args.getInt(ARG_FROM_AVATAR, 0);

        android.widget.ImageView avatar = view.findViewById(R.id.ivInviteAvatar);
        avatar.setImageResource(AVATARS[avatarIdx >= 0 && avatarIdx < AVATARS.length ? avatarIdx : 0]);

        android.widget.TextView msg = view.findViewById(R.id.tvInviteMessage);
        msg.setText(fromName + " te poziva na partiju!");

        android.widget.TextView countdown = view.findViewById(R.id.tvCountdown);
        MaterialButton btnAccept = view.findViewById(R.id.btnAccept);
        MaterialButton btnReject = view.findViewById(R.id.btnReject);

        btnAccept.setOnClickListener(v -> accept(msg, btnAccept, btnReject));
        btnReject.setOnClickListener(v -> reject());

        setCancelable(false);

        timer = new CountDownTimer(TIMEOUT_MS, 1000) {
            @Override public void onTick(long ms) {
                countdown.setText("Automatsko odbijanje za " + (ms / 1000 + 1) + "s");
            }
            @Override public void onFinish() {
                if (!resolved) {
                    resolved = true;
                    inviteRepo.updateStatus(inviteId, GameInvite.STATUS_EXPIRED, u -> {}, e -> {});
                    inviteRepo.deleteInvite(inviteId);
                    dismissAllowingStateLoss();
                }
            }
        }.start();

        // Ako pošiljalac otkaže (status više nije "pending" ili je dokument obrisan), zatvori dialog.
        inviteRepo.listenToInvite(inviteId, inv -> {
            if (!resolved && inv != null
                    && !GameInvite.STATUS_PENDING.equals(inv.getStatus())
                    && !GameInvite.STATUS_ACCEPTED.equals(inv.getStatus())) {
                resolved = true;
                dismissAllowingStateLoss();
            }
        }, e -> {});
    }

    private void accept(android.widget.TextView msg, MaterialButton accept, MaterialButton reject) {
        if (resolved) return;
        resolved = true;
        if (timer != null) timer.cancel();
        accept.setEnabled(false);
        reject.setEnabled(false);
        msg.setText("Pokrećem partiju...");

        inviteRepo.updateStatus(inviteId, GameInvite.STATUS_ACCEPTED, unused -> {
            // Sačekaj da pošiljalac kreira sobu, pa pokreni igru.
            gameRepo.listenToGameRoom(gameId, room -> {
                gameRepo.detachListeners();
                Bundle result = new Bundle();
                result.putString(ARG_GAME_ID, gameId);
                result.putString(ARG_TO_NAME, toUsername);
                if (isAdded()) {
                    getParentFragmentManager().setFragmentResult(RESULT_ACCEPTED, result);
                }
                dismissAllowingStateLoss();
            }, e -> {});
        }, e -> dismissAllowingStateLoss());
    }

    private void reject() {
        if (resolved) return;
        resolved = true;
        if (timer != null) timer.cancel();
        inviteRepo.updateStatus(inviteId, GameInvite.STATUS_REJECTED, u -> {}, e -> {});
        inviteRepo.deleteInvite(inviteId);
        dismissAllowingStateLoss();
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
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isAdded()) {
            getParentFragmentManager().setFragmentResult(RESULT_DISMISSED, new Bundle());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (timer != null) timer.cancel();
        inviteRepo.detachSent();
        gameRepo.detachListeners();
    }
}
