package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.myapplication.data.model.User;
import com.example.myapplication.data.repository.AuthRepository;
import com.example.myapplication.service.AuthService;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class HomeViewModel extends ViewModel {

    private final AuthService authService = new AuthService(new AuthRepository());

    public final MutableLiveData<User> currentUser = new MutableLiveData<>();
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private boolean registeredUser = false;

    public final MutableLiveData<String> passwordChangeResult = new MutableLiveData<>();

    public void setRegistered(boolean registered) { this.registeredUser = registered; }
    public boolean isRegistered() { return registeredUser; }

    public void loadUser() {
        authService.loadUser(
                user -> currentUser.setValue(user),
                e -> errorMessage.setValue("Failed to load user: " + e.getMessage())
        );
    }

    public void deductToken(OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        authService.deductToken(onSuccess, onFailure);
    }

    public void applyGameRewards(int starsDelta,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        authService.applyGameRewards(starsDelta, onSuccess, onFailure);
    }

    public void changePassword(String oldPassword, String newPassword) {
        authService.changePassword(oldPassword, newPassword,
                unused -> passwordChangeResult.setValue("success"),
                e -> passwordChangeResult.setValue("error:" + e.getMessage())
        );
    }

    public void updateAvatar(int avatarId) {
        authService.updateAvatar(avatarId,
                unused -> loadUser(),
                e -> errorMessage.setValue(e.getMessage())
        );
    }

    public void recordGameStats(boolean won) {
        authService.recordGameStats(won,
                unused -> {},
                e -> errorMessage.setValue(e.getMessage())
        );
    }

    public void logout() {
        authService.logout();
    }

    /** Postavlja status da li korisnik trenutno učestvuje u partiji. */
    public void setInGame(boolean inGame) {
        authService.setInGame(inGame, unused -> {}, e -> {});
    }
}
