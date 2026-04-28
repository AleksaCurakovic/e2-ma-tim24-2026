package com.example.myapplication.presentation.viewModel;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.example.myapplication.data.model.User;

public class HomeViewModel extends ViewModel {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public final MutableLiveData<User> currentUser = new MutableLiveData<>();
    public final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public void loadUser() {
        String uid = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid() : null;

        if (uid == null) {
            errorMessage.setValue("No user logged in");
            return;
        }

        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        currentUser.setValue(user);
                    }
                })
                .addOnFailureListener(e ->
                        errorMessage.setValue("Failed to load user: " + e.getMessage())
                );
    }
}