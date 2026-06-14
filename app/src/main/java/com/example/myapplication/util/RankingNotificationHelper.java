package com.example.myapplication.util;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.myapplication.R;
import com.example.myapplication.data.model.RankReward;
import com.example.myapplication.presentation.activities.HomeActivity;

/** Sistemska notifikacija o osvojenoj nagradi na rang listi (tačka g). */
public final class RankingNotificationHelper {

    public static final String EXTRA_SHOW_REWARD   = "showReward";
    public static final String EXTRA_REWARD_TOKENS = "rewardTokens";
    public static final String EXTRA_REWARD_RANK   = "rewardRank";
    public static final String EXTRA_REWARD_MONTHLY = "rewardMonthly";
    public static final String EXTRA_REWARD_RANGE  = "rewardRange";

    private RankingNotificationHelper() {}

    public static void ensureChannel(Context context) {
        NotificationChannels.ensureChannels(context);
    }

    public static void showRewardNotification(Context context, RankReward reward) {
        ensureChannel(context);

        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(EXTRA_SHOW_REWARD, true);
        intent.putExtra(EXTRA_REWARD_TOKENS, reward.tokens);
        intent.putExtra(EXTRA_REWARD_RANK, reward.rank);
        intent.putExtra(EXTRA_REWARD_MONTHLY, reward.monthly);
        intent.putExtra(EXTRA_REWARD_RANGE, reward.dateRange);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;

        PendingIntent pi = PendingIntent.getActivity(
                context, reward.monthly ? 2 : 1, intent, flags);

        String type = reward.monthly ? "mesečnoj" : "nedeljnoj";
        String title = "Osvojio si nagradu! 🏆";
        String text = reward.rank + ". mesto na " + type + " rang listi — +"
                + reward.tokens + " tokena";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NotificationChannels.RANKING)
                .setSmallIcon(R.drawable.tickets)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi);

        try {
            NotificationManagerCompat.from(context)
                    .notify(reward.monthly ? 1002 : 1001, builder.build());
        } catch (SecurityException ignored) {
            // Korisnik nije dao dozvolu za notifikacije — dialog se i dalje prikazuje u aplikaciji.
        }
    }
}
