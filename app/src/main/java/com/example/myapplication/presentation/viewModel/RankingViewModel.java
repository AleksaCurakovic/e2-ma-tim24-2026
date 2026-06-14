package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.RankReward;
import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.data.model.User;
import com.example.myapplication.service.RankingService;
import com.example.myapplication.util.CycleUtil;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RankingViewModel extends ViewModel {

    private final RankingService rankingService = new RankingService();

    public final MutableLiveData<List<RankingEntry>> ranking = new MutableLiveData<>();
    public final MutableLiveData<String> currentCycleId = new MutableLiveData<>();
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    // Nagrade osvojene na kraju proteklih ciklusa (prikazuju se kao dialog/notifikacija).
    public final MutableLiveData<List<RankReward>> rewards = new MutableLiveData<>();

    /** Učitava rang listu za tekući nedeljni ili mesečni ciklus. */
    public void loadRanking(boolean monthly) {
        String cycleId = monthly ? CycleUtil.getCurrentMonthlyCycleId()
                                  : CycleUtil.getCurrentWeeklyCycleId();
        currentCycleId.setValue(cycleId);
        rankingService.getRanking(cycleId,
                list -> ranking.setValue(list),
                e -> errorMessage.setValue(e.getMessage()));
    }

    public void recordGameResult(String uid, String username, String leagueIcon, int starsWon,
                                 OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        rankingService.recordGameResult(uid, username, leagueIcon, starsWon, onSuccess, onFailure);
    }

    /**
     * Proverava i dodeljuje nagrade za protekli nedeljni i mesečni ciklus.
     * Kad obe provere završe, emituje listu osvojenih nagrada kroz {@link #rewards}.
     */
    public void finalizeRewards(User user) {
        if (user == null) return;

        final List<RankReward> collected = new ArrayList<>();
        final AtomicInteger pending = new AtomicInteger(2);

        OnSuccessListener<RankReward> onOneDone = reward -> {
            if (reward != null) {
                synchronized (collected) { collected.add(reward); }
            }
            if (pending.decrementAndGet() == 0) {
                synchronized (collected) {
                    if (!collected.isEmpty()) rewards.postValue(new ArrayList<>(collected));
                }
            }
        };

        rankingService.finalizePreviousCycle(false, user, onOneDone); // nedeljni
        rankingService.finalizePreviousCycle(true,  user, onOneDone); // mesečni
    }
}
