package com.example.myapplication.service;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.myapplication.data.model.AppNotification;
import com.example.myapplication.data.model.ChatMessage;
import com.example.myapplication.data.model.GameInvite;
import com.example.myapplication.data.repository.AuthRepository;
import com.example.myapplication.data.repository.ChatRepository;
import com.example.myapplication.data.repository.GameInviteRepository;
import com.example.myapplication.data.repository.NotificationRepository;
import com.example.myapplication.util.ChatNotificationHelper;
import com.example.myapplication.util.InviteNotificationHelper;
import com.example.myapplication.util.RankingNotificationHelper;

import java.util.List;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Foreground servis koji sluša dolazne pozivnice za partiju nezavisno od toga da li
 * je aplikacija otvorena. Kada je neka aktivnost u prvom planu (registrovala je
 * listener), pozivnica se prosleđuje njoj radi prikaza dijaloga; inače se šalje
 * sistemska notifikacija i sprovodi automatsko odbijanje posle 10 sekundi.
 */
public class InviteForegroundService extends Service {

    public static final String ACTION_START  = "com.example.myapplication.INVITE_START";
    public static final String ACTION_STOP   = "com.example.myapplication.INVITE_STOP";
    public static final String ACTION_REJECT = "com.example.myapplication.INVITE_REJECT";

    public static final String EXTRA_UID       = "uid";
    public static final String EXTRA_INVITE_ID = "inviteId";

    private static final long AUTO_REJECT_MS = 10_000L;
    private static final int FOREGROUND_ID = 4242;

    /** Listener koji registruje aktivnost u prvom planu da bi prikazala dijalog. */
    public interface IncomingInviteListener { void onInvite(GameInvite invite); }
    private static volatile IncomingInviteListener foregroundListener;

    public static void setForegroundListener(@Nullable IncomingInviteListener listener) {
        foregroundListener = listener;
    }

    private static final long RANKING_CHECK_MS = 15 * 60 * 1000L; // provera kraja ciklusa na 15 min
    private static final long RANKING_FIRST_DELAY_MS = 8_000L;

    private final GameInviteRepository inviteRepo = new GameInviteRepository();
    private final AuthRepository authRepo = new AuthRepository();
    private final RankingService rankingService = new RankingService();
    private final NotificationRepository notificationRepo = new NotificationRepository();
    private final ChatRepository chatRepo = new ChatRepository();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<String> handledIds = new HashSet<>();
    private final Set<String> notifiedChatIds = new HashSet<>();
    private final Map<String, Runnable> autoRejectTasks = new HashMap<>();
    private boolean listening = false;
    private Runnable rankingCheckRunnable;
    private String myUid;
    private long chatBaseline = -1;
    private final Set<String> friendUids = new HashSet<>();

    // ----------------------------------------------------------- START/STOP API

    public static void start(Context context, String uid) {
        Intent intent = new Intent(context, InviteForegroundService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_UID, uid);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, InviteForegroundService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }

    // ---------------------------------------------------------------- LIFECYCLE

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            stopListeningAndSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_REJECT.equals(action) && intent != null) {
            String inviteId = intent.getStringExtra(EXTRA_INVITE_ID);
            if (inviteId != null) {
                cancelAutoReject(inviteId);
                inviteRepo.updateStatus(inviteId, GameInvite.STATUS_REJECTED, u -> {}, e -> {});
                inviteRepo.deleteInvite(inviteId);
                InviteNotificationHelper.cancelInviteNotification(this, inviteId);
            }
            return START_STICKY;
        }

        // ACTION_START (ili restart sistema)
        startForegroundNotification();

        String uid = intent != null ? intent.getStringExtra(EXTRA_UID) : null;
        if (uid != null && !listening) {
            listening = true;
            myUid = uid;
            inviteRepo.listenIncoming(uid, this::onIncomingInvite, e -> {});
            startRankingChecks();
            startChatListener();
        }
        return START_STICKY;
    }

    // ----------------------------------------------- REGIONALNI ČET U POZADINI

    /** Sluša poruke korisnikovog regiona i obaveštava kad app NIJE u prvom planu (tačka e). */
    private void startChatListener() {
        authRepo.loadUser(user -> {
            if (user == null || user.getRegion() == null || user.getRegion().isEmpty()) return;
            chatRepo.listen(user.getRegion(), this::onChatMessages, e -> {});
        }, e -> { /* tiho */ });
    }

    private void onChatMessages(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        long maxTs = 0;
        for (ChatMessage m : messages) maxTs = Math.max(maxTs, m.getTimestamp());

        // Prvi snapshot je istorija — ne obaveštavamo, samo postavljamo prag.
        if (chatBaseline < 0) { chatBaseline = maxTs; return; }

        // Aplikacija je otvorena → korisnik je "u aplikaciji", bez notifikacije.
        if (foregroundListener != null) { chatBaseline = Math.max(chatBaseline, maxTs); return; }

        ChatMessage latestNew = null;
        for (ChatMessage m : messages) {
            if (m.getTimestamp() > chatBaseline
                    && !notifiedChatIds.contains(m.getId())
                    && (myUid == null || !myUid.equals(m.getSenderUid()))) {
                notifiedChatIds.add(m.getId());
                latestNew = m;
            }
        }
        chatBaseline = Math.max(chatBaseline, maxTs);

        if (latestNew != null) {
            ChatNotificationHelper.showMessageNotification(this,
                    latestNew.getSenderUsername() != null ? latestNew.getSenderUsername() : "Igrač",
                    latestNew.getText());
        }
    }

    // ----------------------------------------------- RANG-NAGRADE U POZADINI

    /**
     * Periodično proverava da li je prošao nedeljni/mesečni ciklus i da li je
     * korisnik osvojio nagradu. Radi SAMO kada aplikacija nije u prvom planu —
     * dok je otvorena, finalizaciju i prikaz dijaloga obavlja HomeActivity.
     */
    private void startRankingChecks() {
        if (rankingCheckRunnable != null) return;
        rankingCheckRunnable = new Runnable() {
            @Override public void run() {
                checkRankingRewards();
                mainHandler.postDelayed(this, RANKING_CHECK_MS);
            }
        };
        mainHandler.postDelayed(rankingCheckRunnable, RANKING_FIRST_DELAY_MS);
    }

    private void checkRankingRewards() {
        // Ako je aplikacija u prvom planu, HomeActivity to već radi (izbegava se duplo dodeljivanje).
        if (foregroundListener != null) return;

        authRepo.loadUser(user -> {
            if (user == null) return;
            rankingService.finalizePreviousCycle(false, user, this::notifyRewardIfWon); // nedeljni
            rankingService.finalizePreviousCycle(true,  user, this::notifyRewardIfWon); // mesečni
        }, e -> { /* tiho — pokušaće ponovo */ });
    }

    private void notifyRewardIfWon(com.example.myapplication.data.model.RankReward reward) {
        if (reward != null) {
            RankingNotificationHelper.showRewardNotification(this, reward);
        }
    }

    private void startForegroundNotification() {
        Notification notification = InviteNotificationHelper.buildServiceNotification(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(FOREGROUND_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(FOREGROUND_ID, notification);
        }
    }

    private void onIncomingInvite(GameInvite invite) {
        if (invite == null || invite.getId() == null) return;
        if (handledIds.contains(invite.getId())) return;
        handledIds.add(invite.getId());

        // Upiši u istoriju obaveštenja primaoca (idempotentno preko determinističkog id-a).
        if (invite.getToUid() != null) {
            notificationRepo.add(invite.getToUid(), AppNotification.friendInvite(invite));
        }

        IncomingInviteListener listener = foregroundListener;
        if (listener != null) {
            // Aplikacija je u prvom planu — prepusti prikaz dijaloga aktivnosti.
            mainHandler.post(() -> {
                IncomingInviteListener l = foregroundListener;
                if (l != null) l.onInvite(invite);
                else notifyAndScheduleAutoReject(invite); // u međuvremenu otišla u pozadinu
            });
        } else {
            notifyAndScheduleAutoReject(invite);
        }
    }

    private void notifyAndScheduleAutoReject(GameInvite invite) {
        InviteNotificationHelper.showInviteNotification(this, invite);

        Runnable task = () -> {
            autoRejectTasks.remove(invite.getId());
            inviteRepo.expireIfPending(invite.getId());
            InviteNotificationHelper.cancelInviteNotification(this, invite.getId());
        };
        autoRejectTasks.put(invite.getId(), task);
        mainHandler.postDelayed(task, AUTO_REJECT_MS);
    }

    private void cancelAutoReject(String inviteId) {
        Runnable task = autoRejectTasks.remove(inviteId);
        if (task != null) mainHandler.removeCallbacks(task);
    }

    private void stopListeningAndSelf() {
        inviteRepo.detachIncoming();
        for (Runnable r : autoRejectTasks.values()) mainHandler.removeCallbacks(r);
        autoRejectTasks.clear();
        if (rankingCheckRunnable != null) { mainHandler.removeCallbacks(rankingCheckRunnable); rankingCheckRunnable = null; }
        chatRepo.detach();
        listening = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        inviteRepo.detachIncoming();
        for (Runnable r : autoRejectTasks.values()) mainHandler.removeCallbacks(r);
        autoRejectTasks.clear();
        if (rankingCheckRunnable != null) { mainHandler.removeCallbacks(rankingCheckRunnable); rankingCheckRunnable = null; }
        chatRepo.detach();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
