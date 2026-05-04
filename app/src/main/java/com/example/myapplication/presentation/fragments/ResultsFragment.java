package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;

public class ResultsFragment extends Fragment {

    private GameViewModel vm;
    private String gameId;
    private boolean hasDeletedRoom = false;

    public ResultsFragment() {
        super(R.layout.fragment_results);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : null;

        initializeViews(view);

        GameRoom room = vm.gameRoom.getValue();
        if (room != null) {
            displayResults(room, view);
            deleteRoomOnce();
        }
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

        if (p1Score > p2Score) {
            tvWinner.setText("Winner: " + room.getPlayerOne() + " 🏆");
        } else if (p2Score > p1Score) {
            tvWinner.setText("Winner: " + room.getPlayerTwo() + " 🏆");
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
