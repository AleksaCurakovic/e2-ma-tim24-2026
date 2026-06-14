package com.example.myapplication.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.myapplication.R;
import com.example.myapplication.presentation.activities.HomeActivity;

/** Sistemska notifikacija o pristigloj poruci u regionalnom četu (tačka e). */
public final class ChatNotificationHelper {

    private static final int NOTIFICATION_ID = 3001;

    private ChatNotificationHelper() {}

    public static void showMessageNotification(Context context, String sender, String text) {
        NotificationChannels.ensureChannels(context);

        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(context, 4, intent, flags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.CHAT)
                .setSmallIcon(R.drawable.friends)
                .setContentTitle("Nova poruka • " + sender)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException ignored) {
            // Bez dozvole za notifikacije.
        }
    }
}
