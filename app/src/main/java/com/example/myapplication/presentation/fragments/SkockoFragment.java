package com.example.myapplication.presentation.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SkockoFragment extends Fragment {

    // The 6 possible symbols (must match Firestore values)
    private static final List<String> SYMBOLS = Arrays.asList(
            "skocko", "kvadrat", "krug", "srce", "trougao", "zvezda"
    );
    private static final int MAX_ATTEMPTS = 6;
    private static final int COMBO_SIZE   = 4;

    private GameViewModel vm;
    private String gameId;
    private String myId;
    private boolean isBonusMode;
    private int turnDurationMs;

    // Solution this player is trying to guess
    private List<String> solution;

    // Current attempt being built
    private final List<String> currentAttempt = new ArrayList<>();

    // All attempts made this turn (saved to Firestore when turn ends)
    private final List<List<String>> allAttempts = new ArrayList<>();

    private boolean turnFinished = false;

    // Timer
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timeUpRunnable;
    private Runnable progressUpdater;
    private long timerStartMs;
    private ProgressBar progressBar;

    // Views
    private LinearLayout layoutAttempts;   // rows of past attempts
    private LinearLayout layoutCurrentRow; // the row being built now
    private LinearLayout layoutSymbolPicker;
    private TextView tvTitle;
    private TextView tvInstruction;

    public SkockoFragment() {
        super(R.layout.fragment_skocko);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm    = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        myId  = vm.myUsername.getValue();
        gameId = getArguments().getString("gameId");
        isBonusMode   = getArguments().getBoolean("isBonusMode", false);
        turnDurationMs = getArguments().getInt("turnDurationMs", 30000);

        progressBar        = view.findViewById(R.id.skockoProgressBar);
        layoutAttempts     = view.findViewById(R.id.layoutAttempts);
        layoutCurrentRow   = view.findViewById(R.id.layoutCurrentRow);
        layoutSymbolPicker = view.findViewById(R.id.layoutSymbolPicker);
        tvTitle            = view.findViewById(R.id.tvSkockoTitle);
        tvInstruction      = view.findViewById(R.id.tvSkockoInstruction);

        setupSymbolPicker(view);

        view.findViewById(R.id.btnSubmitAttempt).setOnClickListener(v -> submitAttempt());
        view.findViewById(R.id.btnClearAttempt).setOnClickListener(v -> clearCurrentAttempt());

        if (isBonusMode) {
            setupBonusMode();
        } else {
            setupMainMode();
        }

        startTimer();
    }

    // =========================================================================
    // SETUP
    // =========================================================================

    private void setupMainMode() {
        tvTitle.setText("Skočko");
        tvInstruction.setText("Pogodi kombinaciju za 30 sekundi!");

        // Wait for solution to load from ViewModel
        vm.mySkockoSolution.observe(getViewLifecycleOwner(), sol -> {
            if (sol != null && !sol.isEmpty()) {
                solution = sol;
            }
        });
    }

    private void setupBonusMode() {
        tvTitle.setText("Bonus pokušaj!");
        tvInstruction.setText("Imaš 10 sekundi za jedan pokušaj!");

        // In bonus mode, we guess the OPPONENT's combination
        // Show the opponent's past attempts so we can deduce the solution
        vm.opponentSkockoSolution.observe(getViewLifecycleOwner(), sol -> {
            if (sol != null && !sol.isEmpty()) {
                solution = sol;
            }
        });

        // Show opponent's attempts above the current row
        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room == null) return;
            List<List<String>> opponentAttempts = isMyBonusTurnForP1(room)
                    ? room.getP2Attempts()
                    : room.getP1Attempts();

            if (opponentAttempts != null) {
                renderPastAttempts(opponentAttempts, solution);
            }
        });

        // Bonus = only 1 attempt allowed
        tvInstruction.setText("Pogledaj pokušaje protivnika i pogodi kombinaciju!");
    }

    /**
     * Returns true if I am P1 doing bonus (meaning P2 failed and P1 gets to guess P2's combo).
     * Returns false if I am P2 doing bonus.
     */
    private boolean isMyBonusTurnForP1(GameRoom room) {
        return myId.equals(room.getPlayerOne());
    }

    // =========================================================================
    // SYMBOL PICKER
    // =========================================================================

    private void setupSymbolPicker(View view) {
        // Each symbol button appends to currentAttempt
        int[] symbolButtonIds = {
                R.id.btnSkocko,
                R.id.btnKvadrat,
                R.id.btnKrug,
                R.id.btnSrce,
                R.id.btnTrougao,
                R.id.btnZvezda
        };

        for (int i = 0; i < symbolButtonIds.length; i++) {
            final String symbol = SYMBOLS.get(i);
            view.findViewById(symbolButtonIds[i]).setOnClickListener(v -> {
                if (currentAttempt.size() < COMBO_SIZE && !turnFinished) {
                    currentAttempt.add(symbol);
                    updateCurrentRow();
                }
            });
        }
    }

    private void clearCurrentAttempt() {
        currentAttempt.clear();
        updateCurrentRow();
    }

    private void updateCurrentRow() {
        layoutCurrentRow.removeAllViews();
        for (int i = 0; i < COMBO_SIZE; i++) {
            ImageView cell = makeSymbolCell(
                    i < currentAttempt.size() ? currentAttempt.get(i) : null,
                    false, false
            );
            layoutCurrentRow.addView(cell);
        }
    }

    // =========================================================================
    // ATTEMPT SUBMISSION
    // =========================================================================

    private void submitAttempt() {
        if (turnFinished) return;
        if (solution == null) {
            Toast.makeText(requireContext(), "Učitavanje...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentAttempt.size() < COMBO_SIZE) {
            Toast.makeText(requireContext(), "Izaberi 4 simbola!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isBonusMode && allAttempts.size() >= 1) {
            // Bonus = only 1 attempt
            return;
        }
        if (!isBonusMode && allAttempts.size() >= MAX_ATTEMPTS) {
            return;
        }

        List<String> attempt = new ArrayList<>(currentAttempt);
        allAttempts.add(attempt);

        boolean solved = attempt.equals(solution);
        renderAttemptRow(attempt, solution);
        clearCurrentAttempt();

        if (solved) {
            endTurn(true);
            return;
        }

        // Bonus mode = only 1 attempt, failed
        if (isBonusMode) {
            endTurn(false);
            return;
        }

        // Main mode — check if out of attempts
        if (allAttempts.size() >= MAX_ATTEMPTS) {
            endTurn(false);
        }
    }

    // =========================================================================
    // SCORING
    // =========================================================================

    private int calculateScore(int attemptNumber, boolean solved, boolean isBonus) {
        if (!solved) return 0;
        if (isBonus) return 10;
        if (attemptNumber <= 2) return 20;
        if (attemptNumber <= 4) return 15;
        return 10; // attempts 5-6
    }

    // =========================================================================
    // TURN END
    // =========================================================================

    private void endTurn(boolean solved) {
        if (turnFinished) return;
        turnFinished = true;
        cancelTimer();

        int score = calculateScore(allAttempts.size(), solved, isBonusMode);

        if (isBonusMode) {
            vm.finishBonusTurn(gameId, myId, score, solved);
        } else {
            vm.finishMainTurn(gameId, myId, score, allAttempts, solved);
        }

        // Navigate back to GameFragment which will handle the next phase
        Navigation.findNavController(requireView())
                .navigate(R.id.action_skockoFragment_to_gameFragment);
    }

    // =========================================================================
    // RENDERING
    // =========================================================================

    /**
     * Renders a completed attempt row with feedback dots.
     * Feedback: filled = correct position, half = wrong position, empty = not present.
     */
    private void renderAttemptRow(List<String> attempt, List<String> sol) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_skocko_attempt_row, layoutAttempts, false);

        int[] cellIds = {
                R.id.cell1, R.id.cell2, R.id.cell3, R.id.cell4
        };
        int[] feedbackIds = {
                R.id.feedback1, R.id.feedback2, R.id.feedback3, R.id.feedback4
        };

        List<Integer> feedback = computeFeedback(attempt, sol);

        for (int i = 0; i < COMBO_SIZE; i++) {
            ImageView cell = row.findViewById(cellIds[i]);
            cell.setImageResource(symbolDrawable(attempt.get(i)));

            ImageView fb = row.findViewById(feedbackIds[i]);
            switch (feedback.get(i)) {
                case 2: fb.setImageResource(R.drawable.ic_feedback_correct);  break; // right pos
                case 1: fb.setImageResource(R.drawable.ic_feedback_present);  break; // wrong pos
                case 0: fb.setImageResource(R.drawable.ic_feedback_absent);   break; // not present
            }
        }

        layoutAttempts.addView(row);
    }

    /**
     * Renders past attempts (for bonus mode — opponent's failed attempts).
     */
    private void renderPastAttempts(List<List<String>> attempts, List<String> sol) {
        layoutAttempts.removeAllViews();
        if (sol == null) return;
        for (List<String> attempt : attempts) {
            renderAttemptRow(attempt, sol);
        }
    }

    /**
     * Computes feedback for each position:
     *   2 = correct symbol in correct position
     *   1 = correct symbol in wrong position
     *   0 = symbol not in solution at all
     */
    private List<Integer> computeFeedback(List<String> attempt, List<String> sol) {
        int[] result = new int[COMBO_SIZE];
        boolean[] solUsed     = new boolean[COMBO_SIZE];
        boolean[] attemptUsed = new boolean[COMBO_SIZE];

        // First pass: exact matches
        for (int i = 0; i < COMBO_SIZE; i++) {
            if (attempt.get(i).equals(sol.get(i))) {
                result[i]     = 2;
                solUsed[i]    = true;
                attemptUsed[i] = true;
            }
        }

        // Second pass: wrong position matches
        for (int i = 0; i < COMBO_SIZE; i++) {
            if (attemptUsed[i]) continue;
            for (int j = 0; j < COMBO_SIZE; j++) {
                if (!solUsed[j] && attempt.get(i).equals(sol.get(j))) {
                    result[i]  = 1;
                    solUsed[j] = true;
                    break;
                }
            }
        }

        List<Integer> out = new ArrayList<>();
        for (int r : result) out.add(r);
        return out;
    }

    private ImageView makeSymbolCell(String symbol, boolean showFeedback, boolean correct) {
        ImageView iv = new ImageView(requireContext());
        int size = (int) (48 * requireContext().getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(4, 4, 4, 4);
        iv.setLayoutParams(params);
        if (symbol != null) {
            iv.setImageResource(symbolDrawable(symbol));
        } else {
            iv.setImageResource(R.drawable.empty);
        }
        return iv;
    }

    private int symbolDrawable(String symbol) {
        switch (symbol) {
            case "skocko":  return R.drawable.skocko;
            case "kvadrat": return R.drawable.square;
            case "krug":    return R.drawable.circle;
            case "srce":    return R.drawable.heart;
            case "trougao": return R.drawable.triangle;
            case "zvezda":  return R.drawable.star;
            default:        return R.drawable.empty;
        }
    }

    // =========================================================================
    // TIMER
    // =========================================================================

    private void startTimer() {
        timerStartMs = System.currentTimeMillis();

        if (progressBar != null) {
            progressBar.setMax(100);
            progressBar.setProgress(100);
        }

        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (progressBar == null || turnFinished) return;
                long elapsed   = System.currentTimeMillis() - timerStartMs;
                long remaining = turnDurationMs - elapsed;
                if (remaining <= 0) {
                    progressBar.setProgress(0);
                    return;
                }
                progressBar.setProgress((int) ((remaining * 100) / turnDurationMs));
                timerHandler.postDelayed(this, 100);
            }
        };
        timerHandler.post(progressUpdater);

        timeUpRunnable = () -> {
            if (!turnFinished) {
                Toast.makeText(requireContext(), "Vreme isteklo!", Toast.LENGTH_SHORT).show();
                endTurn(false);
            }
        };
        timerHandler.postDelayed(timeUpRunnable, turnDurationMs);
    }

    private void cancelTimer() {
        if (progressUpdater != null) timerHandler.removeCallbacks(progressUpdater);
        if (timeUpRunnable  != null) timerHandler.removeCallbacks(timeUpRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
    }
}