package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.presentation.viewModel.HomeViewModel;

public class ProfileFragment extends Fragment {

    private HomeViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);

        TextView tvUsername = view.findViewById(R.id.tvProfileUsername);
        TextView tvEmail    = view.findViewById(R.id.tvProfileEmail);
        TextView tvLeague   = view.findViewById(R.id.tvProfileLeague);
        TextView tvTokens   = view.findViewById(R.id.tvProfileTokens);
        TextView tvStars    = view.findViewById(R.id.tvProfileStars);
        TextView tvRegion   = view.findViewById(R.id.tvProfileRegion);
        ImageView ivLeague  = view.findViewById(R.id.ivProfileLeagueIcon);

        viewModel.loadUser();
        viewModel.currentUser.observe(getViewLifecycleOwner(), user -> {
            if (user == null) return;
            tvUsername.setText(user.getUsername());
            tvEmail.setText(user.getEmail());
            tvLeague.setText(user.getLeagueIcon());
            tvTokens.setText(String.valueOf(user.getTokens()));
            tvStars.setText(String.valueOf(user.getStars()));
            tvRegion.setText(user.getRegion());

            int resId = requireContext().getResources().getIdentifier(
                    user.getLeagueIcon(), "drawable", requireContext().getPackageName());
            if (resId != 0) ivLeague.setImageResource(resId);
        });


        EditText etOld     = view.findViewById(R.id.etOldPassword);
        EditText etNew     = view.findViewById(R.id.etNewPassword);
        EditText etConfirm = view.findViewById(R.id.etConfirmPassword);
        Button   btnChange = view.findViewById(R.id.btnChangePassword);

        btnChange.setOnClickListener(v -> {
            String oldPass     = etOld.getText().toString().trim();
            String newPass     = etNew.getText().toString().trim();
            String confirmPass = etConfirm.getText().toString().trim();


            if (oldPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                Toast.makeText(requireContext(), "Popunite sva polja.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPass.equals(confirmPass)) {
                etConfirm.setError("Sifre nisu iste.");
                return;
            }
            if (newPass.length() < 8) {
                etNew.setError("Sifra mora imati najmanje 8 karaktera.");
                return;
            }

            btnChange.setEnabled(false);
            viewModel.changePassword(oldPass, newPass);
        });

        viewModel.passwordChangeResult.observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            btnChange.setEnabled(true);
            if (result.equals("success")) {
                Toast.makeText(requireContext(), "Sifra promenjena uspešno.", Toast.LENGTH_SHORT).show();
                etOld.setText("");
                etNew.setText("");
                etConfirm.setText("");
            } else {
                String msg = result.startsWith("error:") ? result.substring(6) : result;
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
            }

            viewModel.passwordChangeResult.setValue(null);
        });
    }
}