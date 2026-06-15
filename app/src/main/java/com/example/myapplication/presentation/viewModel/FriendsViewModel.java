package com.example.myapplication.presentation.viewModel;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.FriendProfile;
import com.example.myapplication.data.model.GameInvite;
import com.example.myapplication.data.model.RankingEntry;
import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.FriendRepository;
import com.example.myapplication.data.repository.GameInviteRepository;
import com.example.myapplication.data.repository.GameRepository;
import com.example.myapplication.service.RankingService;
import com.example.myapplication.util.CycleUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FriendsViewModel extends ViewModel {

    private static final long INVITE_TIMEOUT_MS = 12_000L; // malo duže od 10s primaoca

    private final FriendRepository friendRepo = new FriendRepository();
    private final GameInviteRepository inviteRepo = new GameInviteRepository();
    private final GameRepository gameRepo = new GameRepository();
    private final RankingService rankingService = new RankingService();

    public final MutableLiveData<List<FriendProfile>> friends = new MutableLiveData<>();
    public final MutableLiveData<List<User>> searchResults = new MutableLiveData<>();
    public final MutableLiveData<String> message = new MutableLiveData<>();
    public final MutableLiveData<InviteEvent> inviteEvent = new MutableLiveData<>();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentInviteId;
    private String myUsername;
    private Runnable timeoutRunnable;

    // ----------------------------------------------------------- LISTA / PRETRAGA

    public void loadFriends(String myUid) {
        if (myUid == null) return;
        friendRepo.getUser(myUid, me -> {
            List<String> uids = me != null ? me.getFriends() : new ArrayList<>();
            if (uids.isEmpty()) { friends.postValue(new ArrayList<>()); return; }

            friendRepo.getUsers(uids, users ->
                    // Učitaj mesečnu rang listu da bismo dodelili rangove prijateljima.
                    rankingService.getRanking(CycleUtil.getCurrentMonthlyCycleId(), ranking -> {
                        Map<String, Integer> rankByUid = new HashMap<>();
                        for (RankingEntry e : ranking) rankByUid.put(e.getUid(), e.getRank());

                        List<FriendProfile> profiles = new ArrayList<>();
                        for (User u : users) {
                            int rank = rankByUid.containsKey(u.getUid()) ? rankByUid.get(u.getUid()) : 0;
                            profiles.add(new FriendProfile(u, rank));
                        }
                        friends.postValue(profiles);
                    }, e -> {
                        // Bez rangova ako lista nije dostupna.
                        List<FriendProfile> profiles = new ArrayList<>();
                        for (User u : users) profiles.add(new FriendProfile(u, 0));
                        friends.postValue(profiles);
                    }),
                    e -> message.postValue("Greška pri učitavanju prijatelja: " + e.getMessage()));
        }, e -> message.postValue("Greška pri učitavanju korisnika: " + e.getMessage()));
    }

    public void search(String query, String myUid, List<String> existingFriendUids) {
        friendRepo.searchByUsername(query, users -> {
            List<User> filtered = new ArrayList<>();
            for (User u : users) {
                if (u.getUid() == null) continue;
                if (u.getUid().equals(myUid)) continue;                 // ne ja
                if (existingFriendUids != null && existingFriendUids.contains(u.getUid())) continue; // već prijatelj
                filtered.add(u);
            }
            searchResults.postValue(filtered);
        }, e -> message.postValue("Greška pri pretrazi: " + e.getMessage()));
    }

    public void addFriend(String myUid, User friend, Runnable onAdded) {
        friendRepo.addFriendMutual(myUid, friend.getUid(),
                unused -> {
                    message.postValue(friend.getUsername() + " je dodat u prijatelje.");
                    if (onAdded != null) onAdded.run();
                },
                e -> message.postValue("Greška pri dodavanju: " + e.getMessage()));
    }

    public void addFriendByUid(String myUid, String friendUid, List<String> existingFriendUids, Runnable onAdded) {
        if (friendUid == null || friendUid.trim().isEmpty()) {
            message.postValue("QR kod nije validan.");
            return;
        }
        if (friendUid.equals(myUid)) {
            message.postValue("Ne mozes dodati sebe u prijatelje.");
            return;
        }
        if (existingFriendUids != null && existingFriendUids.contains(friendUid)) {
            message.postValue("Ovaj igrac je vec u listi prijatelja.");
            return;
        }
        friendRepo.getUser(friendUid,
                friend -> addFriend(myUid, friend, onAdded),
                e -> message.postValue("Korisnik iz QR koda nije pronadjen."));
    }

    // --------------------------------------------------------------- POZIVNICE

    /** Šalje pozivnicu prijatelju i pokreće praćenje odgovora. */
    public void sendInvite(User me, User friend) {
        if (me == null || friend == null) return;
        if (!friend.isLoggedIn()) { message.postValue(friend.getUsername() + " nije ulogovan."); return; }
        if (friend.isInGame())   { message.postValue(friend.getUsername() + " je trenutno u partiji."); return; }

        myUsername = me.getUsername();
        String gameId = UUID.randomUUID().toString();
        GameInvite invite = new GameInvite(me.getUid(), me.getUsername(),
                friend.getUid(), friend.getUsername(), gameId);

        inviteEvent.postValue(InviteEvent.waiting(friend.getUsername()));

        inviteRepo.createInvite(invite, inviteId -> {
            currentInviteId = inviteId;
            startTimeout();
            inviteRepo.listenToInvite(inviteId, this::onInviteUpdate,
                    e -> message.postValue("Greška: " + e.getMessage()));
        }, e -> {
            inviteEvent.postValue(InviteEvent.error());
            message.postValue("Greška pri slanju pozivnice: " + e.getMessage());
        });
    }

    private void onInviteUpdate(GameInvite invite) {
        String status = invite.getStatus();
        if (GameInvite.STATUS_ACCEPTED.equals(status)) {
            cancelTimeout();
            inviteRepo.detachSent();
            createRoomAndStart(invite);
        } else if (GameInvite.STATUS_REJECTED.equals(status)) {
            cancelTimeout();
            inviteRepo.detachSent();
            inviteRepo.deleteInvite(invite.getId());
            inviteEvent.postValue(InviteEvent.rejected(invite.getToUsername()));
        } else if (GameInvite.STATUS_EXPIRED.equals(status)) {
            cancelTimeout();
            inviteRepo.detachSent();
            inviteRepo.deleteInvite(invite.getId());
            inviteEvent.postValue(InviteEvent.expired(invite.getToUsername()));
        }
    }

    private void createRoomAndStart(GameInvite invite) {
        gameRepo.fetchMinigameIds(playlist ->
                gameRepo.createGameRoom(invite.getGameId(), invite.getFromUsername(),
                        invite.getToUsername(), playlist, true, // friendly = prijateljska partija
                        unused -> {
                            inviteRepo.deleteInvite(invite.getId());
                            inviteEvent.postValue(InviteEvent.start(invite.getGameId(),
                                    invite.getFromUsername()));
                        },
                        e -> {
                            inviteEvent.postValue(InviteEvent.error());
                            message.postValue("Greška pri kreiranju sobe: " + e.getMessage());
                        }),
                e -> {
                    inviteEvent.postValue(InviteEvent.error());
                    message.postValue("Greška: " + e.getMessage());
                });
    }

    /** Pošiljalac prekida svoj zahtev za partiju. */
    public void cancelInvite() {
        cancelTimeout();
        if (currentInviteId != null) {
            String id = currentInviteId;
            inviteRepo.updateStatus(id, GameInvite.STATUS_CANCELLED, unused -> {}, e -> {});
            inviteRepo.deleteInvite(id);
        }
        inviteRepo.detachSent();
        currentInviteId = null;
        inviteEvent.postValue(InviteEvent.cancelled());
    }

    private void startTimeout() {
        cancelTimeout();
        timeoutRunnable = () -> {
            if (currentInviteId != null) {
                inviteRepo.updateStatus(currentInviteId, GameInvite.STATUS_EXPIRED, unused -> {}, e -> {});
                inviteRepo.deleteInvite(currentInviteId);
                inviteRepo.detachSent();
                currentInviteId = null;
                inviteEvent.postValue(InviteEvent.expired(null));
            }
        };
        handler.postDelayed(timeoutRunnable, INVITE_TIMEOUT_MS);
    }

    private void cancelTimeout() {
        if (timeoutRunnable != null) handler.removeCallbacks(timeoutRunnable);
        timeoutRunnable = null;
    }

    public void clearInviteId() { currentInviteId = null; }

    @Override
    protected void onCleared() {
        super.onCleared();
        cancelTimeout();
        inviteRepo.detachSent();
        inviteRepo.detachIncoming();
    }

    // ------------------------------------------------------------------- EVENT

    public static class InviteEvent {
        public enum Type { WAITING, START, REJECTED, EXPIRED, CANCELLED, ERROR }
        public final Type type;
        public final String gameId;
        public final String myUsername;
        public final String friendName;

        private InviteEvent(Type type, String gameId, String myUsername, String friendName) {
            this.type = type;
            this.gameId = gameId;
            this.myUsername = myUsername;
            this.friendName = friendName;
        }
        static InviteEvent waiting(String friend) { return new InviteEvent(Type.WAITING, null, null, friend); }
        static InviteEvent start(String gameId, String myUsername) { return new InviteEvent(Type.START, gameId, myUsername, null); }
        static InviteEvent rejected(String friend) { return new InviteEvent(Type.REJECTED, null, null, friend); }
        static InviteEvent expired(String friend) { return new InviteEvent(Type.EXPIRED, null, null, friend); }
        static InviteEvent cancelled() { return new InviteEvent(Type.CANCELLED, null, null, null); }
        static InviteEvent error() { return new InviteEvent(Type.ERROR, null, null, null); }
    }
}
