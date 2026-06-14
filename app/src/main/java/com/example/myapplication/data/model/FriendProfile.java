package com.example.myapplication.data.model;

/** Prijatelj sa podacima za prikaz: korisnik + trenutni mesečni rang. */
public class FriendProfile {
    public final User user;
    public final int monthlyRank; // 0 = nije rangiran u tekućem mesecu

    public FriendProfile(User user, int monthlyRank) {
        this.user = user;
        this.monthlyRank = monthlyRank;
    }
}
