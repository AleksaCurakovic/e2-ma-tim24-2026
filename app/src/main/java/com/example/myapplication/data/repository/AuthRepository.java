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
                        onFailure.onFailure(new Exception("Please verify your email before logging in."));
                    } else {
                        checkAndAwardDailyReward(user.getUid(), onSuccess, onFailure);
                    }
                })
                .addOnFailureListener(onFailure);
    }
    public void loginAsGuest(OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    String uid = firebaseUser.getUid();

                    // Use first 8 chars of Firebase uid as guest name
                    String guestName = "Guest_" + uid.substring(0, 8);

                    User guestUser = new User();
                    guestUser.setUsername(guestName);

                    db.collection("users").document(uid)
                            .set(guestUser)
                            .addOnSuccessListener(unused -> onSuccess.onSuccess(guestName))
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(e ->
                        onFailure.onFailure(new Exception("Could not continue as guest: " + e.getMessage()))
                );
    }

    private void checkAndAwardDailyReward(String uid,
                                          OnSuccessListener<Void> onSuccess,
                                          OnFailureListener onFailure) {
        db.collection("users").document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        onSuccess.onSuccess(null);
                        return;
                    }

                    long now = System.currentTimeMillis();
                    Long lastLogin = snapshot.getLong("lastLoginTime");

                    // Check if 24 hours have passed since last login
                    boolean shouldAward = lastLogin == null ||
                            (now - lastLogin) >= 24 * 60 * 60 * 1000L;

                    if (shouldAward) {
                        // Get current tokens and increment by 5
                        Long currentTokens = snapshot.getLong("tokens");
                        long newTokens = (currentTokens != null ? currentTokens : 0) + 5;

                        // Update tokens and lastLoginTime in one call
                        db.collection("users").document(uid)
                                .update(
                                        "tokens", newTokens,
                                        "lastLoginTime", now
                                )
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(onFailure);
                    } else {
                        // No reward but still update lastLoginTime
                        db.collection("users").document(uid)
                                .update("lastLoginTime", now)
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(onFailure);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void loadUser(OnSuccessListener<User> onSuccess, OnFailureListener onFailure) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            onFailure.onFailure(new Exception("No user logged in"));
            return;
        }

        db.collection("users").document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        onSuccess.onSuccess(snapshot.toObject(User.class));
                    } else {
                        onFailure.onFailure(new Exception("User data not found"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void logout() {
        auth.signOut();
    }

    public boolean isGuest() {
        FirebaseUser u = auth.getCurrentUser();
        return u != null && u.isAnonymous();
    }

    public void deductToken(OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null || firebaseUser.isAnonymous()) {
            onSuccess.onSuccess(null);
            return;
        }
        String uid = firebaseUser.getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    long tokens = snapshot.getLong("tokens") != null ? snapshot.getLong("tokens") : 0;
                    if (tokens <= 0) {
                        onFailure.onFailure(new Exception("Nemaš dovoljno tokena za igranje!"));
                        return;
                    }
                    db.collection("users").document(uid)
                            .update("tokens", tokens - 1)
                            .addOnSuccessListener(onSuccess)
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    public void applyGameRewards(int starsDelta,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null || firebaseUser.isAnonymous()) {
            onSuccess.onSuccess(null);
            return;
        }
        String uid = firebaseUser.getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    long currentStars = snapshot.getLong("stars") != null ? snapshot.getLong("stars") : 0;
                    long currentTokens = snapshot.getLong("tokens") != null ? snapshot.getLong("tokens") : 0;

                    long newStars = Math.max(0, currentStars + starsDelta);
                    long earnedTokens = newStars / 50;
                    long remainingStars = newStars % 50;
                    long newTokens = currentTokens + earnedTokens;

                    db.collection("users").document(uid)
                            .update("stars", remainingStars, "tokens", newTokens)
                            .addOnSuccessListener(onSuccess)
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }
}