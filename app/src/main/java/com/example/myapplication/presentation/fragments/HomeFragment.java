package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.User;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment {

    private GameViewModel gameViewModel;
    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        gameViewModel = new ViewModelProvider(requireActivity()).get(GameViewModel.class);

        TextView tvWelcome = view.findViewById(R.id.tvWelcome);
        MaterialButton btnPlay = view.findViewById(R.id.btnPlay);

        // Show username in welcome text
        homeViewModel.currentUser.observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                tvWelcome.setText("Welcome, " + user.getUsername() + "!");
            }
        });

        // Play button click
        btnPlay.setOnClickListener(v -> {
            User user = homeViewModel.currentUser.getValue();
            if (user == null) {
                Toast.makeText(requireContext(),
                        "Loading user data, please wait",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!homeViewModel.isRegistered()) {
                gameViewModel.startMatchmaking(user.getUsername());
            } else {
                btnPlay.setEnabled(false);
                homeViewModel.deductToken(
                        unused -> gameViewModel.startMatchmaking(user.getUsername()),
                        e -> {
                            btnPlay.setEnabled(true);
                            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                );
            }
        });

        // Show loading state on button
        gameViewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            btnPlay.setEnabled(!loading);
            btnPlay.setText(loading ? "Finding opponent..." : "▶  PLAY");
        });

        // Navigate when game is found
        gameViewModel.navigateToGame.observe(getViewLifecycleOwner(), gameId -> {
            if (gameId != null) {
                Bundle b = new Bundle();
                b.putString("gameId", gameId);
                String username = gameViewModel.myUsername.getValue();
                b.putString("myUsername", username != null ? username : "");
                Navigation.findNavController(requireView())
                        .navigate(R.id.action_homeFragment_to_gameFragment, b);
                gameViewModel.navigateToGame.setValue(null);
            }
        });

        // Show errors
        gameViewModel.errorMessage.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                gameViewModel.errorMessage.setValue(null);
            }
        });
    }

}