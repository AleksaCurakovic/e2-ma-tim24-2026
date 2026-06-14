package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.data.model.User;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.example.myapplication.presentation.viewModel.RankingViewModel;
import com.google.android.material.button.MaterialButton;

public class ResultsFragment extends Fragment {

    private GameViewModel vm;
    private HomeViewModel homeVm;
    private RankingViewModel rankingVm;
    private String gameId;
    private boolean hasDeletedRoom = false;
    private boolean hasRecordedStats = false;
    private boolean hasRecordedRanking = false;

    public ResultsFragment() {
        super(R.layout.fragment_results);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm        = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        homeVm    = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        rankingVm = new ViewModelProvider(requireActivity()).get(RankingViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : null;

        initializeViews(view);

        GameRoom room = vm.gameRoom.getValue();
        if (room != null) {
            displayResults(room, view);
            String myUsername = getArguments() != null ? getArguments().getString("myUsername") : null;
            if (myUsername != null) {
                applyRewards(room, myUsername);
                if (myUsername.equals(room.getPlayerOne())) {
                    deleteRoomOnce();
                }
            }
        }
    }

    private void applyRewards(GameRoom room, String myUsername) {
        if (!homeVm.isRegistered()) return;

        // Partija je gotova — igrač više nije "u partiji".
        homeVm.setInGame(false);

        // Prijateljska partija: ne dobijaju se/gube zvezde, ne ulazi u statistiku,
        // ne koriste se tokeni i ne ulazi u rang listu (specifikacija e).
        if (room.isFriendly()) {
            if (isAdded()) {
                Toast.makeText(requireContext(),
                        "Prijateljska partija — bez zvezda i statistike.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        int p1Score = room.getPlayerOneScore();
        int p2Score = room.getPlayerTwoScore();
        boolean iAmP1 = myUsername.equals(room.getPlayerOne());
        int myScore   = iAmP1 ? p1Score : p2Score;
        int theirScore = iAmP1 ? p2Score : p1Score;

        String left = room.getLeftPlayer();
        boolean iLeft = left != null && left.equals(myUsername);
        boolean opponentLeft = left != null && !left.equals(myUsername);

        // Igrač koji je napustio partiju gubi i ne dobija zvezde (specifikacija f).
        if (iLeft) {
            if (!hasRecordedStats) {
                hasRecordedStats = true;
                homeVm.recordGameStats(false);
            }
            return;
        }

        boolean iWon = opponentLeft || myScore > theirScore;
        int delta = vm.computeStarsDelta(myScore, iWon);
        if (!hasRecordedStats) {
            hasRecordedStats = true;
            homeVm.recordGameStats(iWon);
        }

        recordRanking(Math.max(0, delta));

        homeVm.applyGameRewards(delta,
                unused -> {
                    String msg = iWon
                            ? "Pobedio si! +" + delta + " zvezdi"
                            : (delta >= 0
                                ? "Izgubio si, ali dobijaš " + delta + " zvezdi"
                                : "Izgubio si " + Math.abs(delta) + " zvezdi");
                    if (isAdded()) {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    }
                },
                e -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "Greška pri ažuriranju zvezdi", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Beleži odigranu partiju u rang listu (nedeljnu i mesečnu) za tekućeg korisnika.
     * Time igrač postaje rangiran (odigrao bar jednu partiju) i dobija osvojene zvezde.
     */
    private void recordRanking(int starsWon) {
        if (hasRecordedRanking) return;
        User user = homeVm.currentUser.getValue();
        if (user == null || user.getUid() == null) return;
        hasRecordedRanking = true;
        rankingVm.recordGameResult(
                user.getUid(), user.getUsername(), user.getLeagueIcon(), starsWon,
                unused -> {}, e -> {});
    }

    private void initializeViews(View view) {
        MaterialButton btnBackToHome = view.findViewById(R.id.btnBackToHome);
        btnBackToHome.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.homeFragment)
        );
    }

    private void displayResults(GameRoom room, View view) {
        TextView tvPlayerOneName = view.findViewById(R.id.tvPlayerOneName);
        TextView tvPlayerOneScore = view.findViewById(R.id.tvPlayerOneScore);
        TextView tvPlayerTwoName = view.findViewById(R.id.tvPlayerTwoName);
        TextView tvPlayerTwoScore = view.findViewById(R.id.tvPlayerTwoScore);
        TextView tvWinner = view.findViewById(R.id.tvWinner);

        tvPlayerOneName.setText(room.getPlayerOne());
        tvPlayerOneScore.setText(String.valueOf(room.getPlayerOneScore()));
        tvPlayerTwoName.setText(room.getPlayerTwo());
        tvPlayerTwoScore.setText(String.valueOf(room.getPlayerTwoScore()));

        int p1Score = room.getPlayerOneScore();
        int p2Score = room.getPlayerTwoScore();

        String left = room.getLeftPlayer();
        if (left != null) {
            // Napuštanje partije: pobednik je protivnik igrača koji je otišao.
            String winner = left.equals(room.getPlayerOne()) ? room.getPlayerTwo() : room.getPlayerOne();
            tvWinner.setText(left + " je napustio partiju. Pobednik: " + winner + " 🏆");
        } else if (p1Score > p2Score) {
            tvWinner.setText("Pobednik: " + room.getPlayerOne() + " 🏆");
        } else if (p2Score > p1Score) {
            tvWinner.setText("Pobednik: " + room.getPlayerTwo() + " 🏆");
        } else {
            tvWinner.setText("Nerešeno!");
        }
    }

    private void deleteRoomOnce() {
        if (!hasDeletedRoom && gameId != null) {
            hasDeletedRoom = true;
            vm.deleteRoom(gameId);
        }
    }
}
