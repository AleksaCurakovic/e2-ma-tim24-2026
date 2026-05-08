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

    private static final long ROUND_DURATION_MS = 30_000L;

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
            new QuizQuestion(
                    "Najviši vrh na Balkanu je?",
                    Arrays.asList("Rila – Musala", "Durmitor – Bobotov kuk", "Prokletije – Jezerca", "Šar-planina – Titov vrh"),
                    0
            ),
            new QuizQuestion(
                    "Koji naučnik je formulisao zakon gravitacije?",
                    Arrays.asList("Nikola Tesla", "Isak Njutn", "Galileo Galilej", "Džejms Vot"),
                    1
            ),
            new QuizQuestion(
                    "Prestonica Finske je?",
                    Arrays.asList("Oslo", "Kopenhagen", "Helsinki", "Rejkjavik"),
                    2
            ),
            new QuizQuestion(
                    "Koliko je 7 · 8?",
                    Arrays.asList("48", "54", "56", "62"),
                    2
            ),
            new QuizQuestion(
                    "Koji je najveći kontinent na svetu?",
                    Arrays.asList("Afrika", "Azija", "Severna Amerika", "Antarktik"),
                    1
            )
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
    private List<View> progressDots;

    private CountDownTimer roundTimer;
    private int currentQuestionIndex = 0;
    private int roundScore = 0;
    private boolean acceptingInput = true;
    private boolean roundEnded = false;

    public QuizFragment() {
        super(R.layout.fragment_quiz);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quiz, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId     = getArguments() != null ? getArguments().getString("gameId")     : null;
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : null;
        int roundNumber = getArguments() != null ? getArguments().getInt("roundNumber", 1) : 1;

        tvTimer           = view.findViewById(R.id.tvQuizTimer);
        tvQuestionCounter = view.findViewById(R.id.tvQuizQuestionCounter);
        tvQuestion        = view.findViewById(R.id.tvQuizQuestion);
        layoutAnswers     = view.findViewById(R.id.layoutQuizAnswers);
        tvResult          = view.findViewById(R.id.tvQuizResult);
        tvPlayerOneScore  = view.findViewById(R.id.tvPlayerOneScore);

        TextView tvSubtitle = view.findViewById(R.id.tvQuizSubtitle);
        if (tvSubtitle != null) tvSubtitle.setText("Runda " + roundNumber + "/2  •  5 brzih pitanja");

        progressDots = new ArrayList<>(5);
        progressDots.add(view.findViewById(R.id.progressDot1));
        progressDots.add(view.findViewById(R.id.progressDot2));
        progressDots.add(view.findViewById(R.id.progressDot3));
        progressDots.add(view.findViewById(R.id.progressDot4));
        progressDots.add(view.findViewById(R.id.progressDot5));

        startRoundTimer();
        bindQuestion();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }

    private void startRoundTimer() {
        tvTimer.setText(getString(R.string.quiz_timer_seconds, ROUND_DURATION_MS / 1000));
        roundTimer = new CountDownTimer(ROUND_DURATION_MS, 1_000L) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText(getString(R.string.quiz_timer_seconds, ms / 1_000L));
            }

            @Override
            public void onFinish() {
                tvTimer.setText(getString(R.string.quiz_timer_seconds, 0));
                endRound();
            }
        };
        roundTimer.start();
    }

    private void stopTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void bindQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            endRound();
            return;
        }

        QuizQuestion question = questions.get(currentQuestionIndex);
        tvQuestionCounter.setText(getString(R.string.quiz_question_counter, currentQuestionIndex + 1, questions.size()));
        tvQuestion.setText(question.prompt);
        tvResult.setVisibility(View.GONE);
        updateProgressDots();
        buildAnswerButtons(question);
        acceptingInput = true;
    }

    private void buildAnswerButtons(QuizQuestion question) {
        layoutAnswers.removeAllViews();
        for (int i = 0; i < question.answers.size(); i++) {
            MaterialButton btn = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            btn.setText(question.answers.get(i));
            btn.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.quiz_button_stroke)));
            btn.setStrokeWidth(2);
            btn.setCornerRadius(28);
            btn.setRippleColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.quiz_button_ripple)));
            btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            final int idx = i;
            btn.setOnClickListener(v -> evaluateAnswer(btn, idx));
            layoutAnswers.addView(btn);
        }
    }

    private void evaluateAnswer(MaterialButton selected, int selectedIndex) {
        if (!acceptingInput) return;
        acceptingInput = false;

        QuizQuestion question = questions.get(currentQuestionIndex);
        boolean isCorrect = selectedIndex == question.correctIndex;

        highlightAnswers(question.correctIndex, selectedIndex);

        int delta = isCorrect ? 10 : -5;
        roundScore += delta;
        tvPlayerOneScore.setText(String.valueOf(roundScore));

        tvResult.setVisibility(View.VISIBLE);
        if (isCorrect) {
            tvResult.setText(getString(R.string.quiz_correct_feedback, delta));
            tvResult.setTextColor(0xFF15803D);
        } else {
            tvResult.setText(getString(R.string.quiz_incorrect_feedback, delta));
            tvResult.setTextColor(0xFFDC2626);
        }

        selected.postDelayed(() -> {
            currentQuestionIndex++;
            bindQuestion();
        }, 800);
    }

    private void highlightAnswers(int correctIndex, int selectedIndex) {
        for (int i = 0; i < layoutAnswers.getChildCount(); i++) {
            View child = layoutAnswers.getChildAt(i);
            if (!(child instanceof MaterialButton)) continue;
            MaterialButton btn = (MaterialButton) child;
            if (i == correctIndex) {
                btn.setBackgroundTintList(ColorStateList.valueOf(0xFF15803D));
                btn.setTextColor(0xFFFFFFFF);
            } else if (i == selectedIndex) {
                btn.setBackgroundTintList(ColorStateList.valueOf(0xFFDC2626));
                btn.setTextColor(0xFFFFFFFF);
            } else {
                btn.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
                btn.setTextColor(0xFF1E293B);
            }
            btn.setEnabled(false);
        }
    }

    private void updateProgressDots() {
        for (int i = 0; i < progressDots.size(); i++) {
            progressDots.get(i).setBackground(ContextCompat.getDrawable(requireContext(),
                    i == currentQuestionIndex
                            ? R.drawable.bg_quiz_progress_active
                            : R.drawable.bg_quiz_progress_inactive));
        }
    }

    private void endRound() {
        if (roundEnded) return;
        roundEnded = true;
        stopTimer();
        acceptingInput = false;

        layoutAnswers.removeAllViews();
        tvQuestionCounter.setText(R.string.quiz_finished_counter);
        tvQuestion.setText(R.string.quiz_finished_title);
        tvResult.setVisibility(View.VISIBLE);
        tvResult.setText(getString(R.string.quiz_summary_template, roundScore));
        tvResult.setTextColor(0xFF0F172A);

        for (View dot : progressDots) {
            dot.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.bg_quiz_progress_inactive));
        }


        GameRoom room = vm.gameRoom.getValue();
        if (gameId != null && room != null && myUsername != null
                && myUsername.equals(room.getPlayerOne())) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("roundPhase", "MINIGAME_DONE");
            vm.advancePhase(gameId, updates);
        }
    }
}
