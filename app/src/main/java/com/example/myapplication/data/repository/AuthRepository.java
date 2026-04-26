package com.example.myapplication.data.repository;

import com.example.myapplication.data.model.User;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    // --- REGISTER ---
    public void register(String email, String username, String region, String password,
                         OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {

        // Step 1: Check username uniqueness first
        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(query -> {
                    // Step 2: Create the Firebase Auth account
                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(authResult -> {
                                FirebaseUser firebaseUser = authResult.getUser();
                                String uid = firebaseUser.getUid();

                                // Step 3: Send verification email
                                firebaseUser.sendEmailVerification();

                                // Step 4: Save user profile to Firestore
                                User user = new User(uid, username, email, region);
                                db.collection("users").document(uid)
                                        .set(user)
                                        .addOnSuccessListener(onSuccess)
                                        .addOnFailureListener(onFailure);
                            })
                            .addOnFailureListener(e -> {
                                String message;

                                if (e instanceof FirebaseAuthWeakPasswordException) {
                                    message = "Lozinka je previše slaba. Mora imati bar 8 karaktera.";
                                } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                                    message = "Email adresa nije ispravno napisana.";
                                } else if (e instanceof FirebaseAuthUserCollisionException) {
                                    message = "Korisnik sa ovim email-om već postoji.";
                                } else {
                                    message = "Greška pri registraciji: " + e.getLocalizedMessage();
                                }

                                onFailure.onFailure(new Exception(message));
                            });
                })
                .addOnFailureListener(onFailure);
    }

    // --- LOGIN ---
    // Accepts either email or username
    public void login(String emailOrUsername, String password,
                      OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {

        // Detect if input looks like an email
        if (emailOrUsername.contains("@")) {
            signInWithEmail(emailOrUsername, password, onSuccess, onFailure);
        } else {
            // Look up email by username in Firestore first
            db.collection("users")
                    .whereEqualTo("username", emailOrUsername)
                    .get()
                    .addOnSuccessListener(query -> {
                        if (query.isEmpty()) {
                            onFailure.onFailure(new Exception("Ne postoji korisnik sa unetim imenom."));
                            return;
                        }
                        String email = query.getDocuments().get(0).getString("email");
                        signInWithEmail(email, password, onSuccess, onFailure);
                    })
                    .addOnFailureListener(onFailure);
        }
    }

    private void signInWithEmail(String email, String password,
                                 OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null && !user.isEmailVerified()) {
                        auth.signOut();
                        onFailure.onFailure(new Exception("Verifikujte email adresu prvo."));
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }
    // --- GUEST ---
    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null && auth.getCurrentUser().isEmailVerified();
    }

    public void logout() {
        auth.signOut();
    }
}