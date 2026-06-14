package com.example.myapplication.service;

import com.example.myapplication.data.model.AppNotification;
import com.example.myapplication.data.model.RankReward;
import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.NotificationRepository;
import com.example.myapplication.data.repository.RankingRepository;
import com.example.myapplication.util.CycleUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.List;

public class RankingService {

    private final RankingRepository repository;
    private final NotificationRepository notificationRepository = new NotificationRepository();

    public RankingService(RankingRepository repository) {
        this.repository = repository;
    }

    public RankingService() {
        this(new RankingRepository());
    }

    /**
     * Tokeni za plasman, prema specifikaciji.
     *   1. mesto: 5 (nedeljna) / 10 (mesečna)
     *   2. mesto: 3 / 6
     *   3. mesto: 2 / 4
     *   4-10.   : 1 / 2
     */
    public static int tokensForRank(int rank, boolean monthly) {
        switch (rank) {
            case 1: return monthly ? 10 : 5;
            case 2: return monthly ? 6 : 3;
            case 3: return monthly ? 4 : 2;
            default:
                if (rank >= 4 && rank <= 10) return monthly ? 2 : 1;
                return 0;
        }
    }

    public void recordGameResult(String uid, String username, String leagueIcon, int starsWon,
                                 OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.recordGameResult(uid, username, leagueIcon, starsWon, onSuccess, onFailure);
    }

    public void getRanking(String cycleId,
                           OnSuccessListener<List<RankingEntry>> onSuccess,
                           OnFailureListener onFailure) {
        repository.getRanking(cycleId, onSuccess, onFailure);
    }

    /**
     * Proverava da li je prethodni ciklus (nedeljni ili mesečni) prošao a da nagrada
     * korisniku još nije dodeljena. Ako jeste, računa plasman, dodeljuje tokene i
     * obeležava ciklus kao obrađen. Obrada je idempotentna preko polja lastClaimed*Cycle.
     *
     * {@code onComplete} se uvek poziva tačno jednom: sa osvojenom nagradom (tokens > 0)
     * ili sa {@code null} (nema nagrade, već obrađeno, ili greška).
     */
    public void finalizePreviousCycle(boolean monthly, User user,
                                      OnSuccessListener<RankReward> onComplete) {
        if (user == null || user.getUid() == null) {
            onComplete.onSuccess(null);
            return;
        }

        String prevId  = monthly ? CycleUtil.getPreviousMonthlyCycleId()
                                  : CycleUtil.getPreviousWeeklyCycleId();
        String claimed = monthly ? user.getLastClaimedMonthlyCycle()
                                  : user.getLastClaimedWeeklyCycle();

        if (prevId.equals(claimed)) { // već obrađeno
            onComplete.onSuccess(null);
            return;
        }

        String claimField = monthly ? "lastClaimedMonthlyCycle" : "lastClaimedWeeklyCycle";

        repository.getRanking(prevId, ranking -> {
            int rank = 0;
            for (RankingEntry e : ranking) {
                if (user.getUid().equals(e.getUid())) {
                    rank = e.getRank();
                    break;
                }
            }
            final int tokens = tokensForRank(rank, monthly);
            final int finalRank = rank;

            repository.awardTokensAndMarkClaimed(user.getUid(), tokens, claimField, prevId,
                    unused -> {
                        // Lokalno ažuriramo marker da se u istoj sesiji ne obradi ponovo.
                        if (monthly) user.setLastClaimedMonthlyCycle(prevId);
                        else user.setLastClaimedWeeklyCycle(prevId);

                        if (finalRank > 0) {
                            RankReward reward = new RankReward(monthly, prevId, finalRank, tokens,
                                    CycleUtil.getDateRangeLabel(prevId));
                            // Upiši u istoriju i nagradu i sam plasman (obaveštenje o plasmanu).
                            notificationRepository.add(user.getUid(), AppNotification.fromReward(reward));
                            // Sistemsku notifikaciju (push) emituje pozivalac samo kad ima nagrade.
                            onComplete.onSuccess(tokens > 0 ? reward : null);
                        } else {
                            onComplete.onSuccess(null);
                        }
                    },
                    e -> onComplete.onSuccess(null)); // pokušaće ponovo pri sledećem otvaranju
        }, e -> onComplete.onSuccess(null));          // nema mreže / liste — kasnije
    }
}
