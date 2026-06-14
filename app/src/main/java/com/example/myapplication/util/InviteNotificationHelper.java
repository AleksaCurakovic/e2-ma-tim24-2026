package com.example.myapplication.util;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameInvite;
import com.example.myapplication.presentation.activities.HomeActivity;
import com.example.myapplication.service.InviteForegroundService;

/** Notifikacije vezane za pozivnice: trajna servisna + akcijabilna pozivnica (tačka d). */
public final class InviteNotificationHelper {

    // Ekstra podaci koje HomeActivity čita kad se notifikacija otvori.
    public static final String EXTRA_ACTION    = "inviteAction";
    public static final String ACTION_SHOW     = "show";
    public static final String ACTION_ACCEPT   = "accept";
    public static final String EXTRA_INVITE_ID = "inviteId";
    public static final String EXTRA_FROM      = "fromUsername";
    public static final String EXTRA_TO        = "toUsername";
    public static final String EXTRA_GAME_ID   = "gameId";

    private InviteNotificationHelper() {}

    public static void ensureChannel(Context context) {
        NotificationChannels.ensureChannels(context);
    }

    /** Trajna notifikacija foreground servisa. */
    public static Notification buildServiceNotification(Context context) {
        ensureChannel(context);
        Intent open = new Intent(context, HomeActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 0, open, immutableFlags());

        return new NotificationCompat.Builder(context, NotificationChannels.SERVICE)
                .setSmallIcon(R.drawable.friends)
                .setContentTitle("Aktivan")
                .setContentText("Spreman za pozivnice i obaveštenja")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pi)
                .build();
    }

    /** Akcijabilna notifikacija za dolaznu pozivnicu (Prihvati / Odbij). */
    public static void showInviteNotification(Context context, GameInvite invite) {
        ensureChannel(context);
        int id = notificationId(invite.getId());

        // Tap na telo: otvori app i prikaži dijalog pozivnice.
        Intent showIntent = new Intent(context, HomeActivity.class);
        showIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        putInviteExtras(showIntent, invite, ACTION_SHOW);
        PendingIntent showPi = PendingIntent.getActivity(
                context, id, showIntent, immutableFlags());

        // Prihvati: otvori app i uđi u partiju.
        Intent acceptIntent = new Intent(context, HomeActivity.class);
        acceptIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        putInviteExtras(acceptIntent, invite, ACTION_ACCEPT);
        PendingIntent acceptPi = PendingIntent.getActivity(
                context, id + 1, acceptIntent, immutableFlags());

        // Odbij: obradi servis bez otvaranja aplikacije.
        Intent rejectIntent = new Intent(context, InviteForegroundService.class);
        rejectIntent.setAction(InviteForegroundService.ACTION_REJECT);
        rejectIntent.putExtra(InviteForegroundService.EXTRA_INVITE_ID, invite.getId());
        PendingIntent rejectPi = PendingIntent.getService(
                context, id + 2, rejectIntent, immutableFlags());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.INVITES)
                .setSmallIcon(R.drawable.friends)
                .setContentTitle("Nova pozivnica za partiju")
                .setContentText(invite.getFromUsername() + " te poziva na partiju!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(showPi)
                .addAction(0, "Odbij", rejectPi)
                .addAction(0, "Prihvati", acceptPi);

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // Bez dozvole za notifikacije.
        }
    }

    public static void cancelInviteNotification(Context context, String inviteId) {
        NotificationManagerCompat.from(context).cancel(notificationId(inviteId));
    }

    private static void putInviteExtras(Intent intent, GameInvite invite, String action) {
        intent.putExtra(EXTRA_ACTION, action);
        intent.putExtra(EXTRA_INVITE_ID, invite.getId());
        intent.putExtra(EXTRA_FROM, invite.getFromUsername());
        intent.putExtra(EXTRA_TO, invite.getToUsername());
        intent.putExtra(EXTRA_GAME_ID, invite.getGameId());
    }

    private static int notificationId(String inviteId) {
        return inviteId != null ? Math.abs(inviteId.hashCode()) % 100000 + 5000 : 5000;
    }

    private static int immutableFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }
}
