package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.AuthRepository;
import com.example.myapplication.service.AuthService;

public class HomeViewModel extends ViewModel {

    private final AuthService authService = new AuthService(new AuthRepository());

    public final MutableLiveData<User> currentUser = new MutableLiveData<>();
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public void loadUser() {
        authService.loadUser(
                user -> currentUser.setValue(user),
                e -> errorMessage.setValue("Failed to load user: " + e.getMessage())
        );
    }
}