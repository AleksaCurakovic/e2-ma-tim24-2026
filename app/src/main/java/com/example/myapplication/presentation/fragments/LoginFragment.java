package com.example.myapplication.presentation.fragments;

import android.content.Intent;
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
import com.example.myapplication.presentation.activities.HomeActivity;
import com.example.myapplication.presentation.viewModel.AuthViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class LoginFragment extends Fragment {

    private AuthViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        TextInputEditText etEmailOrUsername = view.findViewById(R.id.etEmailOrUsername);
        TextInputEditText etPassword = view.findViewById(R.id.etPassword);
        MaterialButton btnLogin = view.findViewById(R.id.btnLogin);
        TextView tvRegister = view.findViewById(R.id.tvRegister);
        TextView tvGuest = view.findViewById(R.id.tvContinueAsGuest);
        ProgressBar progressBar = view.findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> {
            String input = etEmailOrUsername.getText().toString().trim();
            String pass = etPassword.getText().toString();
            viewModel.login(input, pass);
        });

        tvRegister.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.action_login_to_register));


        tvGuest.setOnClickListener(v -> navigateToHome(false));

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.loginSuccess.observe(getViewLifecycleOwner(), success -> {
            if (Boolean.TRUE.equals(success)) navigateToHome(true);
        });

        viewModel.errorMessage.observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clear LiveData state so it doesn't bleed into other fragments
        viewModel.errorMessage.setValue(null);
        viewModel.loginSuccess.setValue(null);
        viewModel.registerSuccess.setValue(null);
    }
    private void navigateToHome(boolean isRegistered) {
        Intent intent = new Intent(requireActivity(), HomeActivity.class);
        intent.putExtra("isRegistered", isRegistered);
        startActivity(intent);
        requireActivity().finish();
    }
}