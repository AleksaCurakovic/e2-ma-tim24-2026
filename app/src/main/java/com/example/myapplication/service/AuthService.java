package com.example.myapplication.service;

import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.AuthRepository;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class AuthService {

    private final AuthRepository repository;

    public AuthService(AuthRepository repository) {
        this.repository = repository;
    }

    public void register(String email, String username, String region, String password,
                         OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.register(email, username, region, password, onSuccess, onFailure);
    }

    public void login(String emailOrUsername, String password,
                      OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.login(emailOrUsername, password, onSuccess, onFailure);
    }

    public void loginAsGuest(OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        repository.loginAsGuest(onSuccess, onFailure);
    }

    public void loadUser(OnSuccessListener<User> onSuccess, OnFailureListener onFailure) {
        repository.loadUser(onSuccess, onFailure);
    }

    public void logout() {
        repository.logout();
    }

    public void updateAvatar(int avatarId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.updateAvatar(avatarId, onSuccess, onFailure);
    }

    public void recordGameStats(boolean won, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.recordGameStats(won, onSuccess, onFailure);
    }

    public boolean isGuest() {
        return repository.isGuest();
    }

    public void deductToken(OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.deductToken(onSuccess, onFailure);
    }

    public void applyGameRewards(int starsDelta,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        repository.applyGameRewards(starsDelta, onSuccess, onFailure);
    }

    public void changePassword(String oldPassword, String newPassword,
                               OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        repository.changePassword(oldPassword, newPassword, onSuccess, onFailure);
    }
}
