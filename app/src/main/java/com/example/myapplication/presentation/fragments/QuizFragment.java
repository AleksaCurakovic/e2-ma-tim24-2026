package com.example.myapplication.presentation.fragments;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizFragment extends Fragment {

    private static final long QUESTION_DURATION_MS = 5_000L;
    private static final long FEEDBACK_DISPLAY_MS  = 2_000L;

    private static final class QuizQuestion {
        final String prompt;
        final List<String> answers;
        final int correctIndex;
        QuizQuestion(String prompt, List<String> answers, int correctIndex) {
            this.prompt = prompt;
            this.answers = answers;
            this.correctIndex = correctIndex;
        }
    }

    private final List<QuizQuestion> QUESTIONS = new ArrayList<>();
    private boolean questionsReady = false;
    private GameViewModel vm;
    private String gameId, myUsername;

    private TextView tvQuestionCounter, tvTimer, tvQuestion;
    private LinearLayout layoutAnswers;

    private CountDownTimer turnTimer = null;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int localQuestionIndex = -1;
    private boolean localAnswered = false;

    public QuizFragment() { super(R.layout.fragment_quiz); }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : "";
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : "";
        initializeViews(view);

        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room == null || !"koZnaZna".equals(room.getCurrentMinigameType())) return;
            if ("MINIGAME_DONE".equals(room.getRoundPhase())) { showFinished(); return; }

            if (!questionsReady) {
                fetchQuestions(room);
                return;
            }

            // Observer reaguje SAMO na promenu indeksa pitanja iz baze
            if (room.getQuizQuestionIndex() != localQuestionIndex) {
                localQuestionIndex = room.getQuizQuestionIndex();
                localAnswered = false;
                startQuestion(localQuestionIndex);
            }
        });
    }

    private void fetchQuestions(GameRoom room) {
        String currentMinigame = room.getMinigamePlaylist().get(room.getCurrentMinigameIndex());
        String docId = currentMinigame.contains(":") ? currentMinigame.split(":")[1] : currentMinigame;


        vm.fetchKoznaZnaData(docId, data -> {
            List<Map<String, Object>> qList = (List<Map<String, Object>>) data.get("questions");
            if (qList != null) {
                QUESTIONS.clear();
                for (Map<String, Object> qMap : qList) {
                    QUESTIONS.add(new QuizQuestion(
                            (String) qMap.get("question"),
                            (List<String>) qMap.get("answers"),
                            ((Long) qMap.get("correctAnswer")).intValue() - 1
                    ));
                }
                questionsReady = true;

                // ✅ Dodaj samo ovo
                GameRoom currentRoom = vm.gameRoom.getValue();
                if (currentRoom != null) {
                    localQuestionIndex = currentRoom.getQuizQuestionIndex();
                    localAnswered = false;
                    startQuestion(localQuestionIndex);
                }
            }
        }, e -> {});
    }

    private void startQuestion(int index) {
        if (index < 0 || index >= QUESTIONS.size()) return;
        tvQuestionCounter.setText("Pitanje " + (index + 1) + "/" + QUESTIONS.size());
        tvQuestion.setText(QUESTIONS.get(index).prompt);
        buildAnswerButtons(QUESTIONS.get(index));

        cancelTimer();
        turnTimer = new CountDownTimer(QUESTION_DURATION_MS, 100) {
            @Override public void onTick(long ms) { tvTimer.setText((ms / 1000) + "s"); }
            @Override public void onFinish() {
                tvTimer.setText("0s");
                if (!localAnswered) submitAnswer(-1, true);
                if (localQuestionIndex >= 0 && localQuestionIndex < QUESTIONS.size()) {
                    showCorrectAnswer();
                }
                handler.postDelayed(() -> {
                    if (shouldIDriveQuiz(vm.gameRoom.getValue())) advanceToNextQuestion();
                }, FEEDBACK_DISPLAY_MS);
            }
        }.start();
    }

    private void buildAnswerButtons(QuizQuestion q) {
        layoutAnswers.removeAllViews();
        for (int i = 0; i < q.answers.size(); i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(requireContext());
            btn.setText(q.answers.get(i));
            btn.setBackgroundTintList(ColorStateList.valueOf(0xFF3B82F6)); // Plava
            btn.setOnClickListener(v -> {
                if (localAnswered) return;
                localAnswered = true;
                btn.setBackgroundTintList(ColorStateList.valueOf(0xFFF59E0B)); // Narandžasta
                submitAnswer(idx, false);
            });
            layoutAnswers.addView(btn);
        }
    }

    private void submitAnswer(int selectedIndex, boolean isTimeout) {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;
        boolean isP1 = isPlayerOne(room);

        Map<String, Object> updates = new HashMap<>();
        updates.put(isP1 ? "quizP1Answered" : "quizP2Answered", true);
        updates.put(isP1 ? "quizP1Correct" : "quizP2Correct", (!isTimeout && selectedIndex == QUESTIONS.get(localQuestionIndex).correctIndex));

        FirebaseFirestore.getInstance().collection("gameRooms").document(gameId).update(updates);
    }

    private void showCorrectAnswer() {
        int correct = QUESTIONS.get(localQuestionIndex).correctIndex;
        for (int i = 0; i < layoutAnswers.getChildCount(); i++) {
            MaterialButton btn = (MaterialButton) layoutAnswers.getChildAt(i);
            if (i == correct) {
                btn.setBackgroundTintList(ColorStateList.valueOf(0xFF22C55E));
            }
            btn.setEnabled(false); // ← uvek posle setBackgroundTintList
        }
    }

    private void advanceToNextQuestion() {
        if (localQuestionIndex + 1 >= QUESTIONS.size()) {
            // Poslednje pitanje — završi minigame
            FirebaseFirestore.getInstance().collection("gameRooms").document(gameId)
                    .update("roundPhase", "MINIGAME_DONE");
        } else {
            FirebaseFirestore.getInstance().collection("gameRooms").document(gameId)
                    .update("quizQuestionIndex", localQuestionIndex + 1,
                            "quizP1Answered", false,
                            "quizP2Answered", false);
        }
    }

    private void showFinished() { cancelTimer(); layoutAnswers.removeAllViews(); tvQuestion.setText("Kraj!"); }
    private void cancelTimer() { if (turnTimer != null) turnTimer.cancel(); }
    private void initializeViews(View view) {
        tvQuestionCounter = view.findViewById(R.id.tvQuestionCounter);
        tvTimer = view.findViewById(R.id.tvTimer);
        tvQuestion = view.findViewById(R.id.tvQuestion);
        layoutAnswers = view.findViewById(R.id.layoutAnswers);
    }
    private boolean isPlayerOne(GameRoom room) { return myUsername != null && myUsername.equals(room.getPlayerOne()); }

    /**
     * Napredovanje pitanja inače vodi playerOne. Ako je playerOne napustio partiju
     * (nije prisutan), vođenje preuzima playerTwo da kviz ne bi stao.
     */
    private boolean shouldIDriveQuiz(GameRoom room) {
        if (room == null) return false;
        boolean p1Present = vm.isPlayerPresent(true);
        String driver = p1Present ? room.getPlayerOne() : room.getPlayerTwo();
        return myUsername != null && myUsername.equals(driver);
    }
    @Override public void onDestroyView() { super.onDestroyView(); cancelTimer(); handler.removeCallbacksAndMessages(null); }
}