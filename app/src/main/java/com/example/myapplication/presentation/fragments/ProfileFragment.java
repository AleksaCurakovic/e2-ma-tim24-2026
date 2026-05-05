package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.User;
import com.example.myapplication.presentation.viewModel.HomeViewModel;

public class ProfileFragment extends Fragment {

    private HomeViewModel homeViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        homeViewModel.loadUser();

        TextView tvUsername = view.findViewById(R.id.tvProfileUsername);
        TextView tvEmail    = view.findViewById(R.id.tvProfileEmail);
        TextView tvRegion   = view.findViewById(R.id.tvProfileRegion);
        TextView tvTokens   = view.findViewById(R.id.tvProfileTokens);
        TextView tvStars    = view.findViewById(R.id.tvProfileStars);
        TextView tvLeague   = view.findViewById(R.id.tvProfileLeague);
        ImageView ivLeague  = view.findViewById(R.id.ivProfileLeagueIcon);
        ImageView ivAvatar  = view.findViewById(R.id.ivProfileAvatar);
        ImageView ivQr      = view.findViewById(R.id.ivProfileQr);

        tvUsername.setText("Username");
        tvEmail.setText("email@example.com");
        tvRegion.setText("Region");
        tvTokens.setText("0");
        tvStars.setText("0");
        tvLeague.setText("League");

        homeViewModel.currentUser.observe(getViewLifecycleOwner(), user -> {
            if (user != null) applyUser(view, user);
        });
    }

    private void applyUser(View view, User user) {
        TextView tvUsername = view.findViewById(R.id.tvProfileUsername);
        TextView tvEmail    = view.findViewById(R.id.tvProfileEmail);
        TextView tvRegion   = view.findViewById(R.id.tvProfileRegion);
        TextView tvTokens   = view.findViewById(R.id.tvProfileTokens);
        TextView tvStars    = view.findViewById(R.id.tvProfileStars);
        TextView tvLeague   = view.findViewById(R.id.tvProfileLeague);
        ImageView ivLeague  = view.findViewById(R.id.ivProfileLeagueIcon);

        tvUsername.setText(user.getUsername() != null ? user.getUsername() : "Username");
        tvEmail.setText(user.getEmail() != null ? user.getEmail() : "email@example.com");
        tvRegion.setText(user.getRegion() != null ? user.getRegion() : "Region");
        tvTokens.setText(String.valueOf(user.getTokens()));
        tvStars.setText(String.valueOf(user.getStars()));
        tvLeague.setText(user.getLeagueName() != null ? user.getLeagueName() : "League");

        if (getContext() != null) {
            int resId = getResources().getIdentifier(
                    user.getLeagueIcon() != null ? user.getLeagueIcon() : "league0",
                    "drawable",
                    requireContext().getPackageName()
            );
            if (resId != 0) ivLeague.setImageResource(resId);
        }
    }
}
