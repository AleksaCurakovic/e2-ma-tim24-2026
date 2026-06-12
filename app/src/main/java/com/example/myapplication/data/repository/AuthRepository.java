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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthRepository {

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void register(String email, String username, String region, String password,
                         OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {


        db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(query -> {

                    auth.createUserWithEmailAndPassword(email, password)
                            .addOnSuccessListener(authResult -> {
                                FirebaseUser firebaseUser = authResult.getUser();
                                String uid = firebaseUser.getUid();
                                firebaseUser.sendEmailVerification();
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


    public void login(String emailOrUsername, String password,
                      OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (emailOrUsername.contains("@")) {
            signInWithEmail(emailOrUsername, password, onSuccess, onFailure);
        } else {
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
                        onFailure.onFailure(new Exception("Molim vas da potvrdite email adresu pre nego što se ulogujete."));
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

                    String guestName = "Guest_" + uid.substring(0, 8);

                    User guestUser = new User();
                    guestUser.setUsername(guestName);

                    db.collection("users").document(uid)
                            .set(guestUser)
                            .addOnSuccessListener(unused -> onSuccess.onSuccess(guestName))
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(e ->
                        onFailure.onFailure(new Exception("Nemoguce da se ulogujete kao gost: " + e.getMessage()))
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

                    boolean shouldAward = lastLogin == null ||
                            (now - lastLogin) >= 24 * 60 * 60 * 1000L;

                    if (shouldAward) {
                        Long currentTokens = snapshot.getLong("tokens");
                        long newTokens = (currentTokens != null ? currentTokens : 0) + 5;

                        db.collection("users").document(uid)
                                .update(
                                        "tokens", newTokens,
                                        "lastLoginTime", now
                                )
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(onFailure);
                    } else {
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
            onFailure.onFailure(new Exception("Nema ulogovanog korisnika"));
            return;
        }

        db.collection("users").document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        onSuccess.onSuccess(snapshot.toObject(User.class));
                    } else {
                        onFailure.onFailure(new Exception("Korisnik nije pronadjen"));
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void logout() {
        auth.signOut();
    }

    public void updateAvatar(int avatarId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null || firebaseUser.isAnonymous()) {
            onFailure.onFailure(new Exception("Nema ulogovanog registrovanog korisnika"));
            return;
        }

        db.collection("users").document(firebaseUser.getUid())
                .update("avatarId", avatarId)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void recordGameStats(boolean won, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null || firebaseUser.isAnonymous()) {
            onSuccess.onSuccess(null);
            return;
        }

        db.collection("users").document(firebaseUser.getUid())
                .update(
                        "totalGames", FieldValue.increment(1),
                        won ? "wonGames" : "lostGames", FieldValue.increment(1)
                )
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
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
                            .update("tokens", tokens - 5)
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

    public void changePassword(String oldPassword, String newPassword,
                               OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            onFailure.onFailure(new Exception("Nema ulogovanog korisnika"));
            return;
        }

        // Re-authenticate first with old password
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), oldPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(unused ->
                        user.updatePassword(newPassword)
                                .addOnSuccessListener(onSuccess)
                                .addOnFailureListener(e ->
                                        onFailure.onFailure(new Exception("Nemoguce promeniti sifru: " + e.getMessage())))
                )
                .addOnFailureListener(e ->
                        onFailure.onFailure(new Exception("Stara sifra nije ispravna"))
                );
    }
}
