package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.presentation.viewModel.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class RegisterFragment extends Fragment {

    private AuthViewModel viewModel;

    private String selectedRegion = "";

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        TextInputEditText etEmail = view.findViewById(R.id.etEmail);
        TextInputEditText etUsername = view.findViewById(R.id.etUsername);
        TextInputEditText etPassword = view.findViewById(R.id.etPassword);
        TextInputEditText etRepeatPassword = view.findViewById(R.id.etRepeatPassword);
        MaterialButton btnRegister = view.findViewById(R.id.btnRegister);
        MaterialButton btnSelectRegion = view.findViewById(R.id.btnSelectRegion);
        TextView tvSelectedRegion = view.findViewById(R.id.tvSelectedRegion);
        TextView tvLogin = view.findViewById(R.id.tvLogin);
        ProgressBar progressBar = view.findViewById(R.id.progressBar);


        btnSelectRegion.setOnClickListener(v -> {
            SerbiaMapDialog mapDialog = new SerbiaMapDialog();
            mapDialog.setOnRegionConfirmedListener(region -> {
                selectedRegion = region;
                tvSelectedRegion.setText("Izabrano: " + region);
            });
            mapDialog.show(getParentFragmentManager(), "SerbiaMapDialog");
        });

        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            String repeatPassword = etRepeatPassword.getText().toString();


            viewModel.register(email, username, selectedRegion, password, repeatPassword);
        });

        tvLogin.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_register_to_login));

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.registerSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) {
                Toast.makeText(requireContext(),
                        "Registracija uspesna, proverite email za verifikaciju.",
                        Toast.LENGTH_LONG).show();
                Navigation.findNavController(view).navigate(R.id.action_register_to_login);
            }
        });

        viewModel.errorMessage.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.errorMessage.setValue(null);
        viewModel.loginSuccess.setValue(null);
        viewModel.registerSuccess.setValue(null);
    }
}