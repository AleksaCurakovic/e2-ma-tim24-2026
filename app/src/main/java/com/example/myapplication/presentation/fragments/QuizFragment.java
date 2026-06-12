package com.example.myapplication.presentation.fragments;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuizFragment extends Fragment {

    private static final long QUESTION_DURATION_MS = 5_000L;

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

    private final List<QuizQuestion> questions = Arrays.asList(
            new QuizQuestion("Najvisi vrh na Balkanu je?",
                    Arrays.asList("Rila - Musala", "Durmitor - Bobotov kuk", "Prokletije - Jezerca", "Sar-planina - Titov vrh"), 0),
            new QuizQuestion("Koji naucnik je formulisao zakon gravitacije?",
                    Arrays.asList("Nikola Tesla", "Isak Njutn", "Galileo Galilej", "Dzejms Vot"), 1),
            new QuizQuestion("Prestonica Finske je?",
                    Arrays.asList("Oslo", "Kopenhagen", "Helsinki", "Rejkjavik"), 2),
            new QuizQuestion("Koliko je 7 * 8?",
                    Arrays.asList("48", "54", "56", "62"), 2),
            new QuizQuestion("Koji je najveci kontinent na svetu?",
                    Arrays.asList("Afrika", "Azija", "Severna Amerika", "Antarktik"), 1)
    );

    private GameViewModel vm;
    private String gameId;
    private String myUsername;

    private TextView tvTimer;
    private TextView tvQuestionCounter;
    private TextView tvQuestion;
    private LinearLayout layoutAnswers;
    private TextView tvResult;
    private TextView tvPlayerOneScore;
    private TextView tvPlayerTwoScore;
    private List<View> progressDots;

    private CountDownTimer questionTimer;
    private int renderedQuestionIndex = -1;
    private boolean initializing = false;
    private boolean advancing = false;

    public QuizFragment() {
        super(R.layout.fragment_quiz);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId = getArguments() != null ? getArguments().getString("gameId") : null;
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : null;

        tvTimer = view.findViewById(R.id.tvQuizTimer);
        tvQuestionCounter = view.findViewById(R.id.tvQuizQuestionCounter);
        tvQuestion = view.findViewById(R.id.tvQuizQuestion);
        layoutAnswers = view.findViewById(R.id.layoutQuizAnswers);
        tvResult = view.findViewById(R.id.tvQuizResult);
        tvPlayerOneScore = view.findViewById(R.id.tvPlayerOneScore);
        tvPlayerTwoScore = view.findViewById(R.id.tvPlayerTwoScore);

        TextView tvSubtitle = view.findViewById(R.id.tvQuizSubtitle);
        if (tvSubtitle != null) tvSubtitle.setText("5 pitanja, 5 sekundi po pitanju");

        progressDots = new ArrayList<>(5);
        progressDots.add(view.findViewById(R.id.progressDot1));
        progressDots.add(view.findViewById(R.id.progressDot2));
        progressDots.add(view.findViewById(R.id.progressDot3));
        progressDots.add(view.findViewById(R.id.progressDot4));
        progressDots.add(view.findViewById(R.id.progressDot5));

        vm.gameRoom.observe(getViewLifecycleOwner(), this::onRoomUpdated);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }

    private void onRoomUpdated(GameRoom room) {
        if (room == null || !"quiz".equals(room.getCurrentMinigameType())) return;

        tvPlayerOneScore.setText(String.valueOf(room.getPlayerOneRoundScore()));
        tvPlayerTwoScore.setText(String.valueOf(room.getPlayerTwoRoundScore()));

        if ("MINIGAME_DONE".equals(room.getRoundPhase())) {
            showFinished(room);
            return;
        }

        if (room.getQuizQuestionStartedAt() == 0 && isPlayerOne(room) && !initializing) {
            initializing = true;
            startQuestion(0);
            return;
        }

        int index = room.getQuizQuestionIndex();
        if (index >= questions.size()) {
            showFinished(room);
            return;
        }

        if (index != renderedQuestionIndex) {
            renderedQuestionIndex = index;
            bindQuestion(room, index);
        }

        boolean myAnswered = isPlayerOne(room)
                ? Boolean.TRUE.equals(room.getQuizP1Answered())
                : Boolean.TRUE.equals(room.getQuizP2Answered());
        setButtonsEnabled(!myAnswered);
        updateResultText(room, myAnswered);
        startTimerFor(room);

        boolean bothAnswered = Boolean.TRUE.equals(room.getQuizP1Answered())
                && Boolean.TRUE.equals(room.getQuizP2Answered());
        long remaining = room.getQuizQuestionStartedAt() + QUESTION_DURATION_MS - System.currentTimeMillis();
        if (isPlayerOne(room) && !advancing && (bothAnswered || remaining <= 0)) {
            advancing = true;
            tvTimer.postDelayed(() -> advanceQuestion(room), bothAnswered ? 700L : 0L);
        }
    }

    private void bindQuestion(GameRoom room, int index) {
        QuizQuestion question = questions.get(index);
        tvQuestionCounter.setText("Pitanje " + (index + 1) + "/" + questions.size());
        tvQuestion.setText(question.prompt);
        tvResult.setVisibility(View.GONE);
        updateProgressDots(index);
        buildAnswerButtons(room, question);
    }

    private void buildAnswerButtons(GameRoom room, QuizQuestion question) {
        layoutAnswers.removeAllViews();
        for (int i = 0; i < question.answers.size(); i++) {
            MaterialButton btn = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setText(question.answers.get(i));
            btn.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.quiz_button_stroke)));
            btn.setStrokeWidth(2);
            btn.setCornerRadius(18);
            btn.setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.quiz_button_ripple)));
            btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            final int idx = i;
            btn.setOnClickListener(v -> submitAnswer(room, idx));
            layoutAnswers.addView(btn);
        }
    }

    private void submitAnswer(GameRoom room, int selectedIndex) {
        boolean p1 = isPlayerOne(room);
        boolean alreadyAnswered = p1
                ? Boolean.TRUE.equals(room.getQuizP1Answered())
                : Boolean.TRUE.equals(room.getQuizP2Answered());
        if (alreadyAnswered || room.getQuizQuestionIndex() >= questions.size()) return;

        QuizQuestion question = questions.get(room.getQuizQuestionIndex());
        boolean correct = selectedIndex == question.correctIndex;
        int delta = correct ? (Boolean.TRUE.equals(room.getQuizCorrectClaimed()) ? 0 : 10) : -5;

        Map<String, Object> updates = new HashMap<>();
        if (p1) {
            updates.put("quizP1Answered", true);
            updates.put("quizP1Correct", correct);
            updates.put("playerOneRoundScore", room.getPlayerOneRoundScore() + delta);
            updates.put("playerOneScore", room.getPlayerOneScore() + delta);
        } else {
            updates.put("quizP2Answered", true);
            updates.put("quizP2Correct", correct);
            updates.put("playerTwoRoundScore", room.getPlayerTwoRoundScore() + delta);
            updates.put("playerTwoScore", room.getPlayerTwoScore() + delta);
        }
        if (correct && !Boolean.TRUE.equals(room.getQuizCorrectClaimed())) {
            updates.put("quizCorrectClaimed", true);
        }

        tvResult.setVisibility(View.VISIBLE);
        if (correct && delta > 0) {
            tvResult.setText("Tacno! +10");
            tvResult.setTextColor(0xFF15803D);
        } else if (correct) {
            tvResult.setText("Tacno, ali protivnik je bio brzi. 0");
            tvResult.setTextColor(0xFF475569);
        } else {
            tvResult.setText("Netacno. -5");
            tvResult.setTextColor(0xFFDC2626);
        }
        setButtonsEnabled(false);
        vm.advancePhase(gameId, updates);
    }

    private void startQuestion(int index) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("quizQuestionIndex", index);
        updates.put("quizQuestionStartedAt", System.currentTimeMillis());
        updates.put("quizP1Answered", false);
        updates.put("quizP2Answered", false);
        updates.put("quizP1Correct", false);
        updates.put("quizP2Correct", false);
        updates.put("quizCorrectClaimed", false);
        if (index == 0) {
            updates.put("playerOneRoundScore", 0);
            updates.put("playerTwoRoundScore", 0);
        }
        vm.advancePhase(gameId, updates);
    }

    private void advanceQuestion(GameRoom room) {
        if (!isAdded()) return;
        int next = room.getQuizQuestionIndex() + 1;
        Map<String, Object> updates = new HashMap<>();
        if (next >= questions.size()) {
            updates.put("roundPhase", "MINIGAME_DONE");
            updates.put("quizQuestionStartedAt", 0);
        } else {
            updates.put("quizQuestionIndex", next);
            updates.put("quizQuestionStartedAt", System.currentTimeMillis());
            updates.put("quizP1Answered", false);
            updates.put("quizP2Answered", false);
            updates.put("quizP1Correct", false);
            updates.put("quizP2Correct", false);
            updates.put("quizCorrectClaimed", false);
        }
        advancing = false;
        initializing = false;
        vm.advancePhase(gameId, updates);
    }

    private void startTimerFor(GameRoom room) {
        long remaining = Math.max(0, room.getQuizQuestionStartedAt() + QUESTION_DURATION_MS - System.currentTimeMillis());
        stopTimer();
        tvTimer.setText((remaining / 1000) + "s");
        questionTimer = new CountDownTimer(remaining, 250L) {
            @Override
            public void onTick(long ms) {
                if (isAdded()) tvTimer.setText((ms / 1000) + "s");
            }

            @Override
            public void onFinish() {
                if (isAdded()) tvTimer.setText("0s");
            }
        }.start();
    }

    private void stopTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }

    private void updateProgressDots(int activeIndex) {
        for (int i = 0; i < progressDots.size(); i++) {
            progressDots.get(i).setBackground(ContextCompat.getDrawable(requireContext(),
                    i == activeIndex
                            ? R.drawable.bg_quiz_progress_active
                            : R.drawable.bg_quiz_progress_inactive));
        }
    }

    private void updateResultText(GameRoom room, boolean myAnswered) {
        if (!myAnswered) {
            tvResult.setVisibility(View.GONE);
            return;
        }
        boolean correct = isPlayerOne(room)
                ? Boolean.TRUE.equals(room.getQuizP1Correct())
                : Boolean.TRUE.equals(room.getQuizP2Correct());
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText(correct ? "Odgovor je zabelezen." : "Netacan odgovor je zabelezen.");
        tvResult.setTextColor(correct ? 0xFF15803D : 0xFFDC2626);
    }

    private void showFinished(GameRoom room) {
        stopTimer();
        setButtonsEnabled(false);
        layoutAnswers.removeAllViews();
        tvQuestionCounter.setText("Ko zna zna");
        tvQuestion.setText("Runda zavrsena!");
        tvResult.setVisibility(View.VISIBLE);
        boolean p1 = isPlayerOne(room);
        int score = p1 ? room.getPlayerOneRoundScore() : room.getPlayerTwoRoundScore();
        tvResult.setText("Tvoj skor: " + score + " bodova");
        tvResult.setTextColor(0xFF0F172A);
    }

    private void setButtonsEnabled(boolean enabled) {
        for (int i = 0; i < layoutAnswers.getChildCount(); i++) {
            layoutAnswers.getChildAt(i).setEnabled(enabled);
        }
    }

    private boolean isPlayerOne(GameRoom room) {
        return myUsername != null && myUsername.equals(room.getPlayerOne());
    }
}
