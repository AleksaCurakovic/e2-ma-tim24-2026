package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.repository.AuthRepository;
import com.example.myapplication.service.AuthService;

public class AuthViewModel extends ViewModel {

    private final AuthService authService = new AuthService(new AuthRepository());
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>();
    public final MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>();
    public final MutableLiveData<Boolean> guestLoginSuccess = new MutableLiveData<>();

    public void register(String email, String username, String region, String password, String repeatPassword) {
        if (!password.equals(repeatPassword)) {
            errorMessage.setValue("Passwords do not match");
            return;
        }

        if (email.isEmpty() || username.isEmpty() || region.isEmpty() || password.isEmpty()) {
            errorMessage.setValue("Please fill in all fields");
            return;
        }

        isLoading.setValue(true);
        authService.register(email, username, region, password,
                unused -> {
                    isLoading.setValue(false);
                    registerSuccess.setValue(true);
                },
                e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue(e.getMessage());
                });
    }

    public void login(String emailOrUsername, String password) {
        if (emailOrUsername.isEmpty() || password.isEmpty()) {
            errorMessage.setValue("Please fill in all fields");
            return;
        }
        isLoading.setValue(true);
        authService.login(emailOrUsername, password,
                unused -> {
                    isLoading.setValue(false);
                    loginSuccess.setValue(true);
                },
                e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue(e.getMessage());
                });
    }

    public void loginAsGuest() {
        isLoading.setValue(true);
        authService.loginAsGuest(
                guestName -> {
                    isLoading.setValue(false);
                    guestLoginSuccess.setValue(true);
                },
                e -> {
                    isLoading.setValue(false);
                    errorMessage.setValue(e.getMessage());
                }
        );
    }
}