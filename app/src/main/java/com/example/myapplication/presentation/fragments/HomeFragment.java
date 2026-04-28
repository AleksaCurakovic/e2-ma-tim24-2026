package com.example.myapplication.presentation.fragments;// HomeFragment.java


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

import com.example.myapplication.R;
import com.example.myapplication.presentation.viewModel.HomeViewModel;
import com.google.android.material.button.MaterialButton;

public class HomeFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        HomeViewModel homeViewModel = new ViewModelProvider(requireActivity())
                .get(HomeViewModel.class);

        TextView tvWelcome = view.findViewById(R.id.tvWelcome);
        MaterialButton btnPlay = view.findViewById(R.id.btnPlay);


        btnPlay.setOnClickListener(v -> {
            // TODO: handle play logic
            Toast.makeText(requireContext(), "Starting game...", Toast.LENGTH_SHORT).show();
        });
    }
}