package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KorakFragment extends Fragment {

    private static final int TOTAL_STEPS    = 7;
    private static final long ROUND_DURATION_MS = 70_000;
    private static final long STEP_INTERVAL_MS  = 10_000;

    private GameViewModel vm;
    private String gameId;
    private String myUsername;

    private TextView tvStatus;
    private TextView tvTimer;
    private LinearLayout layoutSteps;
    private LinearLayout layoutAnswerInput;
    private android.widget.ScrollView scrollSteps;
    private EditText etAnswer;
    private com.google.android.material.textfield.TextInputLayout tilAnswer;
    private MaterialButton btnSubmitAnswer;

    private List<String> steps;
    private String answer;
    private int revealedSteps = 0;
    private boolean isMyTurn      = false;
    private boolean isBonusMode   = false;
    private boolean turnFinished  = false;
    private String  activePhase   = null;
    private String  lastStartedPhase = null;

    private CountDownTimer turnTimer;

    public KorakFragment() {
        super(R.layout.fragment_korak);
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm         = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId     = getArguments().getString("gameId");
        myUsername = getArguments().getString("myUsername");

        tvStatus          = view.findViewById(R.id.tvKorakStatus);
        tvTimer           = view.findViewById(R.id.tvKorakTimer);
        layoutSteps       = view.findViewById(R.id.layoutSteps);
        layoutAnswerInput = view.findViewById(R.id.layoutAnswerInput);
        scrollSteps       = view.findViewById(R.id.scrollSteps);
        etAnswer          = view.findViewById(R.id.etAnswer);
        tilAnswer         = view.findViewById(R.id.tilAnswer);
        btnSubmitAnswer   = view.findViewById(R.id.btnSubmitAnswer);

        btnSubmitAnswer.setOnClickListener(v -> submitAnswer());
        etAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                submitAnswer();
                return true;
            }
            return false;
        });

        vm.currentPhase.observe(getViewLifecycleOwner(), phase -> {
            if (phase != null) onPhaseChanged(phase);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }

    // =========================================================================
    // PHASE HANDLING
    // =========================================================================

    private void onPhaseChanged(String phase) {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;

        isBonusMode = phase.contains("BONUS");
        isMyTurn    = isActivePlayer(phase, room);

        if (!isMyTurn) {
            if (phase.equals("SHOWING_RESULTS")) lastStartedPhase = null;
            showWaiting("Čekaj...");
            cancelTimer();
            return;
        }

        if (!phase.equals(lastStartedPhase)) {
            lastStartedPhase = phase;
            loadDataThenStart(room, phase);
        }
    }

    private boolean isActivePlayer(String phase, GameRoom room) {
        switch (phase) {
            case "P1_TURN":
            case "P1_BONUS": return myUsername.equals(room.getPlayerOne());
            case "P2_TURN":
            case "P2_BONUS": return myUsername.equals(room.getPlayerTwo());
            default:         return false;
        }
    }

    private void showWaiting(String message) {
        tvStatus.setText(message);
        tvTimer.setText("");
        tvTimer.setVisibility(View.INVISIBLE);
        layoutAnswerInput.setVisibility(View.GONE);
    }

    // =========================================================================
    // TURN START
    // =========================================================================

    private void loadDataThenStart(GameRoom room, String phase) {
        tvStatus.setText("Učitavanje...");
        String entry        = room.getMinigamePlaylist().get(room.getCurrentMinigameIndex());
        String docId        = entry.contains(":") ? entry.split(":")[1] : entry;
        String playerPrefix = playerPrefixForPhase(phase);

        vm.fetchKorakSolution(docId, playerPrefix,
                data -> {
                    answer = (String) data.get("answer");
                    steps  = (List<String>) data.get("steps");
                    startTurn(room, phase);
                },
                e -> tvStatus.setText("Greška pri učitavanju!")
        );
    }

    /**
     * Main turns: each player uses their own data.
     * Bonus turns: the bonus player sees the OTHER player's steps/answer.
     *   P2_BONUS → p1 data (P2 tries to guess P1's answer)
     *   P1_BONUS → p2 data (P1 tries to guess P2's answer)
     */
    private String playerPrefixForPhase(String phase) {
        switch (phase) {
            case "P1_TURN":  return "p1";
            case "P2_TURN":  return "p2";
            case "P2_BONUS": return "p1";
            case "P1_BONUS": return "p2";
            default:         return "p1";
        }
    }

    private void startTurn(GameRoom room, String phase) {
        activePhase   = phase;
        revealedSteps = 0;
        turnFinished  = false;
        etAnswer.setText("");

        layoutSteps.removeAllViews();

        if (isBonusMode) {
            // Show all steps the opponent already revealed, then one input row
            tvStatus.setText("Bonus! Pogodi za 5 bodova!");
            revealAllSteps();
            tvTimer.setVisibility(View.VISIBLE);
            layoutAnswerInput.setVisibility(View.VISIBLE);
            btnSubmitAnswer.setEnabled(true);
            startTimer(STEP_INTERVAL_MS); // 10s for bonus
        } else {
            tvStatus.setText("Tvoj red!");
            revealStep(); // reveal first step immediately
            tvTimer.setVisibility(View.VISIBLE);
            layoutAnswerInput.setVisibility(View.VISIBLE);
            btnSubmitAnswer.setEnabled(true);
            startTimer(ROUND_DURATION_MS); // 70s continuous timer
        }
    }

    // =========================================================================
    // STEP REVEAL
    // =========================================================================

    private void revealStep() {
        if (revealedSteps >= TOTAL_STEPS) return;
        addStepCard(revealedSteps, steps.get(revealedSteps));
        revealedSteps++;
        if (scrollSteps != null) scrollSteps.post(() -> scrollSteps.fullScroll(android.widget.ScrollView.FOCUS_DOWN));
    }

    private void revealAllSteps() {
        for (int i = 0; i < TOTAL_STEPS; i++) {
            addStepCard(i, steps.get(i));
        }
        revealedSteps = TOTAL_STEPS;
    }

    private void addStepCard(int index, String text) {
        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardParams);
        card.setRadius(dp(8));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.skocko_cell_filled));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView tvIndex = new TextView(requireContext());
        tvIndex.setText(String.valueOf(index + 1));
        tvIndex.setTextSize(14f);
        tvIndex.setTypeface(null, android.graphics.Typeface.BOLD);
        tvIndex.setMinWidth(dp(28));
        tvIndex.setTextColor(ContextCompat.getColor(requireContext(), R.color.skocko_absent));

        TextView tvText = new TextView(requireContext());
        tvText.setText(text);
        tvText.setTextSize(15f);
        tvText.setPadding(dp(8), 0, 0, 0);

        inner.addView(tvIndex);
        inner.addView(tvText);
        card.addView(inner);
        layoutSteps.addView(card);
    }

    // =========================================================================
    // ANSWER SUBMISSION
    // =========================================================================

    private void submitAnswer() {
        if (turnFinished) return;
        String input = etAnswer.getText().toString().trim();
        if (input.isEmpty()) return;

        if (input.equalsIgnoreCase(answer)) {
            turnFinished = true;
            cancelTimer();
            layoutAnswerInput.setVisibility(View.GONE);
            tvStatus.setText("Tačno! ✓");
            commitTurnToFirestore(true);
        } else {
            etAnswer.setText("");
            if (tilAnswer != null) tilAnswer.setError("Nije tačno, pokušaj ponovo");
        }
    }

    // =========================================================================
    // FIRESTORE WRITE
    // =========================================================================

    private void commitTurnToFirestore(boolean solved) {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || activePhase == null) return;

        int    score     = scoreForTurn(solved);
        String nextPhase = nextPhaseAfter(activePhase, solved);

        Map<String, Object> updates = new HashMap<>();
        updates.put("roundPhase", nextPhase);

        if (!isBonusMode) {
            if (activePhase.equals("P1_TURN")) {
                updates.put("playerOneRoundScore", score);
                updates.put("playerOneScore", room.getPlayerOneScore() + score);
            } else {
                updates.put("playerTwoRoundScore", score);
                updates.put("playerTwoScore", room.getPlayerTwoScore() + score);
            }
        } else {
            if (solved) {
                if (activePhase.equals("P1_BONUS")) {
                    updates.put("playerOneRoundScore", room.getPlayerOneRoundScore() + score);
                    updates.put("playerOneScore", room.getPlayerOneScore() + score);
                } else {
                    updates.put("playerTwoRoundScore", room.getPlayerTwoRoundScore() + score);
                    updates.put("playerTwoScore", room.getPlayerTwoScore() + score);
                }
            }
        }

        vm.advancePhase(gameId, updates);
    }

    /**
     * Step 1 (revealedSteps=1 when answered) → 20pts
     * Each additional step costs 2pts: step N → 20 - (N-1)*2
     * Step 7 → 8pts. Bonus → always 5pts.
     */
    private int scoreForTurn(boolean solved) {
        if (!solved) return 0;
        if (isBonusMode) return 5;
        int pts = 20 - (revealedSteps - 1) * 2;
        return Math.max(pts, 8);
    }

    private String nextPhaseAfter(String phase, boolean solved) {
        switch (phase) {
            case "P1_TURN":  return solved ? "P2_TURN" : "P2_BONUS";
            case "P2_BONUS": return "P2_TURN";
            case "P2_TURN":  return solved ? "SHOWING_RESULTS" : "P1_BONUS";
            case "P1_BONUS": return "SHOWING_RESULTS";
            default:         return "SHOWING_RESULTS";
        }
    }

    // =========================================================================
    // TIMER
    // =========================================================================

    private void startTimer(long durationMs) {
        cancelTimer();
        turnTimer = new CountDownTimer(durationMs, 500) {
            @Override
            public void onTick(long ms) {
                tvTimer.setText((ms / 1000) + "s");

                if (!isBonusMode) {
                    // Reveal a new step every 10s: at 60s, 50s, 40s, 30s, 20s, 10s
                    int elapsedSec = (int) ((ROUND_DURATION_MS - ms) / 1000);
                    int expectedRevealed = Math.min(elapsedSec / 10 + 1, TOTAL_STEPS);
                    while (revealedSteps < expectedRevealed) {
                        revealStep();
                    }
                }
            }

            @Override
            public void onFinish() {
                tvTimer.setText("0s");
                if (!turnFinished) {
                    turnFinished = true;
                    layoutAnswerInput.setVisibility(View.GONE);
                    commitTurnToFirestore(false);
                }
            }
        }.start();
    }

    private void cancelTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private int dp(int dp) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
