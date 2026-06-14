package com.example.myapplication.data.model;

/** Opis nagrade osvojene na rang listi na kraju jednog ciklusa. */
public class RankReward {
    public final boolean monthly;
    public final String cycleId;
    public final int rank;
    public final int tokens;
    public final String dateRange;

    public RankReward(boolean monthly, String cycleId, int rank, int tokens, String dateRange) {
        this.monthly = monthly;
        this.cycleId = cycleId;
        this.rank = rank;
        this.tokens = tokens;
        this.dateRange = dateRange;
    }
}
