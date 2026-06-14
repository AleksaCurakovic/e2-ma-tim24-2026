package com.example.myapplication.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/**
 * Centralizovana definicija svih notifikacionih kanala. Svaki TIP obaveštenja ima
 * svoj kanal kako bi korisnik mogao zasebno da kontroliše svaki tip, a aplikacija
 * lako da se proširi novim tipovima (npr. prelazak u ligu).
 *
 * Napomena: SERVICE nije isto što i INVITES. SERVICE je obavezna TRAJNA, tiha
 * notifikacija foreground servisa (Android zahtev da servis radi u pozadini),
 * dok je INVITES kanal za stvarne, glasne notifikacije pozivnica.
 */
public final class NotificationChannels {

    public static final String INVITES = "game_invites";       // pozivnice za partiju (glasno)
    public static final String RANKING = "ranking_rewards";    // plasman i nagrade na rang listama
    public static final String CHAT    = "region_chat";        // poruke u regionalnom četu
    public static final String LEAGUE  = "league_promotions";  // prelazak u ligu (buduće proširenje)
    public static final String SERVICE = "background_service"; // trajna notifikacija pozadinskog servisa (tiho)

    private NotificationChannels() {}

    /** Kreira sve kanale (idempotentno). Pozvati pre slanja bilo koje notifikacije. */
    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        createChannel(nm, INVITES, "Pozivnice za partiju",
                "Pozivi prijatelja na partiju", NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, RANKING, "Rang liste",
                "Plasman i nagrade na nedeljnim/mesečnim rang listama", NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, CHAT, "Regionalni čet",
                "Nove poruke u tvom regionu", NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, LEAGUE, "Lige",
                "Obaveštenja o prelasku u ligu", NotificationManager.IMPORTANCE_HIGH);
        createChannel(nm, SERVICE, "Pozadinski servis",
                "Omogućava prijem obaveštenja dok je aplikacija zatvorena", NotificationManager.IMPORTANCE_LOW);
    }

    private static void createChannel(NotificationManager nm, String id, String name,
                                      String description, int importance) {
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription(description);
        nm.createNotificationChannel(channel);
    }
}
