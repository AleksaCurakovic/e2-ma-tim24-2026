package com.example.myapplication.presentation.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.myapplication.R;
import com.example.myapplication.data.model.GameRoom;
import com.example.myapplication.presentation.viewModel.GameViewModel;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AsocijacijeFragment extends Fragment {

    // ─── Konstante ────────────────────────────────────────────────────────────
    private static final int    COLS          = 4;
    private static final int    ROWS          = 4;
    private static final long   TURN_DURATION = 120_000L; // 2 min po rundi

    private boolean boardReady = false;

    private static final String[] COL_IDS = {"A", "B", "C", "D"};

    // ─── ViewModel / identifikatori ───────────────────────────────────────────
    private GameViewModel vm;
    private String gameId;
    private String myUsername;

    // ─── UI ───────────────────────────────────────────────────────────────────
    private TextView       tvStatus;
    private TextView       tvTimer;
    private LinearLayout[] columnLayouts  = new LinearLayout[COLS];
    private Button[]       btnGuessColumn = new Button[COLS];
    private MaterialButton btnGuessFinal;
    private TextView       tvWaiting;

    // ─── Lokalno stanje ───────────────────────────────────────────────────────
    /**
     * isMyTurn = da li JA trenutno smem da kliknem (otvorim polje / pogađam).
     * Ažurira se i na promenu roundPhase (nova runda) i na promenu asocTurnPlayer
     * (promena poteza unutar runde).
     */

    private boolean cellOpenedThisTurn = false;
    private LinearLayout overlayWaiting;     // zamenjuje stari tvWaiting
    private TextView     tvWaitingSubtitle;
    private boolean isMyTurn     = false;
    private boolean turnFinished = false;   // runda završena (final solved / timeout)
    private String  activePhase  = null;   // "P1_TURN" ili "P2_TURN"

    private final Set<String>  localOpenedCells   = new HashSet<>();
    private final Set<Integer> localSolvedColumns = new HashSet<>();
    private boolean            localFinalSolved   = false;

    /** Podaci pitanja učitani iz Firestore-a */
    private List<String>[] columnWords = new List[COLS];
    private String[]       columnSols  = new String[COLS];
    private String         finalSolution;

    /** Sprečava dvostruko pokretanje iste faze */
    private final Set<String> completedPhases = new HashSet<>();

    private CountDownTimer turnTimer;

    // ─── Konstruktor ──────────────────────────────────────────────────────────
    public AsocijacijeFragment() {
        super(R.layout.fragment_asocijacije);
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        vm         = new ViewModelProvider(requireActivity()).get(GameViewModel.class);
        gameId     = getArguments() != null ? getArguments().getString("gameId")     : "";
        myUsername = getArguments() != null ? getArguments().getString("myUsername") : "";

        bindViews(view);

        // Osluškujemo i roundPhase (nova runda) I gameRoom (promena asocTurnPlayer unutar runde)
        vm.currentPhase.observe(getViewLifecycleOwner(), phase -> {
            if (phase != null) onPhaseChanged(phase);
        });

        vm.gameRoom.observe(getViewLifecycleOwner(), room -> {
            if (room != null && activePhase != null && !turnFinished) {
                onRoomUpdated(room);
            }
        });

        absentHandler.postDelayed(absentPoll, 2000);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        cancelTimer();
        absentHandler.removeCallbacks(absentPoll);
    }

    // ─── Napuštanje protivnika usred runde ─────────────────────────────────────

    /**
     * Heartbeat protivnika ne menja gameRoom, pa ga periodično proveravamo. Ako protivnik
     * napusti partiju dok je na NJEGOVOM pod-potezu (asocTurnPlayer = protivnik), preostali
     * igrač preuzima potez da ne ostane zaglavljen čekajući protivnika.
     */
    private final Handler absentHandler = new Handler(Looper.getMainLooper());
    private final Runnable absentPoll = new Runnable() {
        @Override public void run() {
            claimTurnIfOpponentAbsent();
            absentHandler.postDelayed(this, 2000);
        }
    };

    private void claimTurnIfOpponentAbsent() {
        if (turnFinished || activePhase == null || isMyTurn) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || !"asocijacije".equals(room.getCurrentMinigameType())) return;
        if (opponentPresent()) return;

        // Preuzmi potez i nastavi sam.
        isMyTurn = true;
        Map<String, Object> updates = new HashMap<>();
        updates.put("asocTurnPlayer", myUsername);
        vm.advancePhase(gameId, updates);

        tvWaiting.setVisibility(View.GONE);
        tvTimer.setVisibility(View.VISIBLE);
        String roundLabel = "P1_TURN".equals(activePhase) ? "Runda 1/2" : "Runda 2/2";
        tvStatus.setText("Asocijacije  •  " + roundLabel + "  (Tvoj red!)");

        if (!boardReady) {
            loadDataThenStart(room);   // izgradi tablu, primeni stanje, pokreni tajmer
        } else {
            cellOpenedThisTurn = false;
            setInteractionEnabled(true);
            updateGuessButtonStates();
        }
    }

    // ─── Vezivanje pogleda ────────────────────────────────────────────────────

    private void bindViews(View root) {
        tvStatus  = root.findViewById(R.id.tvStatus);
        tvTimer   = root.findViewById(R.id.tvTimer);
        tvWaiting = root.findViewById(R.id.tvWaiting);

        columnLayouts[0]  = root.findViewById(R.id.colLayoutA);
        columnLayouts[1]  = root.findViewById(R.id.colLayoutB);
        columnLayouts[2]  = root.findViewById(R.id.colLayoutC);
        columnLayouts[3]  = root.findViewById(R.id.colLayoutD);

        btnGuessColumn[0] = root.findViewById(R.id.btnGuessColA);
        btnGuessColumn[1] = root.findViewById(R.id.btnGuessColB);
        btnGuessColumn[2] = root.findViewById(R.id.btnGuessColC);
        btnGuessColumn[3] = root.findViewById(R.id.btnGuessColD);

        btnGuessFinal = root.findViewById(R.id.btnGuessFinal);

        for (int col = 0; col < COLS; col++) {
            final int c = col;
            btnGuessColumn[c].setOnClickListener(v -> onGuessColumn(c));
        }
        btnGuessFinal.setOnClickListener(v -> onGuessFinal());
    }

    // ─── Upravljanje fazama runde ─────────────────────────────────────────────

    private void onPhaseChanged(String phase) {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;
        if (!"asocijacije".equals(room.getCurrentMinigameType())) return;

        if ("MINIGAME_DONE".equals(phase)) {
            cancelTimer();
            showWaiting("Kraj asocijacija!");
            return;
        }

        if (!phase.equals("P1_TURN") && !phase.equals("P2_TURN")) return;

        // Nova faza — uvek resetuj lokalno stanje
        if (!phase.equals(activePhase)) {
            activePhase  = phase;
            turnFinished = false;
            cellOpenedThisTurn = false;
            localOpenedCells.clear();
            localSolvedColumns.clear();
            localFinalSolved = false;
            boardReady = false;
        }

        // Sprečavamo ponovni ulazak samo ako SMO MI već završili OVU fazu
        // i faza se nije promenila (echo od Firestorea)
        if (completedPhases.contains(phase) && phase.equals(activePhase) && turnFinished) return;

        refreshTurnState(room);

        if (isMyTurn) {
            if (!boardReady) {
                loadDataThenStart(room);
            }
        } else {
            showWaiting("Čekaj na protivnika...");
            applyRoomStateToBoard(room);
        }
    }

    private void onRoomUpdated(GameRoom room) {
        if (!"asocijacije".equals(room.getCurrentMinigameType())) return;
        if (turnFinished) return;

        boolean wasMyTurn = isMyTurn;
        refreshTurnState(room);

        // Primeni nova otvorena polja / rešene kolone
        applyRoomStateToBoard(room);

        if (isMyTurn && !wasMyTurn) {
            // Potez je prešao na mene – aktiviraj interakciju
            tvWaiting.setVisibility(View.GONE);
            tvTimer.setVisibility(View.VISIBLE);
            String roundLabel = "P1_TURN".equals(activePhase) ? "Runda 1/2" : "Runda 2/2";
            tvStatus.setText("Asocijacije  •  " + roundLabel + "  (Tvoj red!)");
            setInteractionEnabled(true);
            cellOpenedThisTurn = false;
        } else if (!isMyTurn && wasMyTurn) {
            // Potez je prešao na protivnika
            setInteractionEnabled(false);
            showWaiting("Čekaj na protivnika...");
        }

    }

    /**
     * Čita asocTurnPlayer iz sobe i setuje isMyTurn.
     * Ako asocTurnPlayer nije postavljen, aktivan igrač je onaj čija je runda (P1/P2).
     */
    private void refreshTurnState(GameRoom room) {
        String turnPlayer = room.getAsocTurnPlayer();
        if (turnPlayer != null && !turnPlayer.isEmpty()) {
            isMyTurn = myUsername.equals(turnPlayer);
        } else {
            // Fallback: prvi potez u rundi
            if ("P1_TURN".equals(activePhase)) isMyTurn = myUsername.equals(room.getPlayerOne());
            else if ("P2_TURN".equals(activePhase)) isMyTurn = myUsername.equals(room.getPlayerTwo());
            else isMyTurn = false;
        }
    }

    // ─── Učitavanje pitanja iz Firestore-a ───────────────────────────────────

    private void loadDataThenStart(GameRoom room) {
        if (!isAdded() || turnFinished) return;

        String entry = room.getMinigamePlaylist().get(room.getCurrentMinigameIndex());
        String qId   = entry.contains(":") ? entry.split(":")[1] : entry;

        if (qId.isEmpty()) { finishLoad(room); return; }

        vm.fetchAssociationQuestion(qId,
                doc -> {
                    if (!isAdded()) return;
                    String roundKey = "P1_TURN".equals(activePhase) ? "round1" : "round2";
                    Map<String, Object> round = (Map<String, Object>) doc.get(roundKey); // ulazimo u mapu
                    if (round == null) { tvStatus.setText("Greška: nema podataka!"); return; }

                    for (int i = 0; i < COLS; i++) {
                        columnWords[i] = (List<String>) round.get("column" + COL_IDS[i] + "_clues");
                        columnSols[i]  = (String) round.get("column" + COL_IDS[i] + "_solution");
                    }
                    finalSolution = (String) round.get("final_solution");
                    finishLoad(room);
                },
                e -> { if (!isAdded()) return;
                    tvStatus.setText("Greška pri učitavanju!"); }
        );
    }

    private void finishLoad(GameRoom room) {
        if (boardReady) return;
        boardReady = true;
        buildBoard();
        applyRoomStateToBoard(room);

        String roundLabel = "P1_TURN".equals(activePhase) ? "Runda 1/2" : "Runda 2/2";
        tvStatus.setText("Asocijacije  •  " + roundLabel + "  (Tvoj red!)");
        tvTimer.setVisibility(View.VISIBLE);
        tvWaiting.setVisibility(View.GONE);

        // Aktiviraj ćelije direktno — NE zovi setInteractionEnabled(true)
        // jer ono overriduje dugmad za pogađanje
        for (int col = 0; col < COLS; col++) {
            if (columnLayouts[col] == null) continue;
            for (int row = 0; row < ROWS; row++) {
                View child = columnLayouts[col].findViewWithTag("cell_" + col + "_" + row);
                if (child != null) {
                    String key = col + "_" + row;
                    child.setEnabled(!localOpenedCells.contains(key) && !localSolvedColumns.contains(col));
                }
            }
        }
        // Dugmad za pogađanje postavi posebno — podaci su sigurno učitani ovde
        for (int col = 0; col < COLS; col++) {
            btnGuessColumn[col].setEnabled(!localSolvedColumns.contains(col) && !localFinalSolved);
        }
        btnGuessFinal.setEnabled(!localFinalSolved);

        GameRoom current = vm.gameRoom.getValue();
        if (current != null && (current.getAsocTurnPlayer() == null || current.getAsocTurnPlayer().isEmpty())) {
            Map<String, Object> init = new HashMap<>();
            init.put("asocTurnPlayer", myUsername);
            vm.advancePhase(gameId, init);
        }

        startTimer(TURN_DURATION);
    }

    // ─── Izgradnja UI table ───────────────────────────────────────────────────

    private void buildBoard() {
        for (int col = 0; col < COLS; col++) {
            LinearLayout layout = columnLayouts[col];
            // Uklanjamo samo dinamički dodane ćelije (ne naslov kolone koji je u XML-u)
            // Naslov (MaterialCardView) je uvek first child, ćelije su iza njega
            while (layout.getChildCount() > 1) layout.removeViewAt(1);

            for (int row = 0; row < ROWS; row++) {
                Button cell = new Button(requireContext());
                cell.setText("?");
                cell.setTextColor(0xFF94A3B8);       // sivo "?" vidljivo
                cell.setTextSize(13f);
                cell.setTag("cell_" + col + "_" + row);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
                lp.setMargins(dp(3), dp(3), dp(3), dp(3));
                cell.setLayoutParams(lp);
                cell.setBackgroundColor(0xFF1E293B); // tamno plava ćelija
                cell.setElevation(dp(2));
                final int c = col, r = row;
                cell.setOnClickListener(v -> onCellClicked(c, r));
                layout.addView(cell);
            }

            btnGuessColumn[col].setEnabled(false);
            btnGuessColumn[col].setText("Reši " + COL_IDS[col]);
        }
        btnGuessFinal.setEnabled(false);
    }

    // ─── Sinhronizacija sa Firestore stanjem ──────────────────────────────────

    private void applyRoomStateToBoard(GameRoom room) {
        if (room == null) return;

        List<String>  openedCells = safeStrings(room.getAsocOpenedCells());
        List<Integer> solvedCols  = safeIntegers(room.getAsocSolvedColumns());
        boolean       finalSolved = Boolean.TRUE.equals(room.getAsocFinalSolved());

        localOpenedCells.addAll(openedCells);
        for (int c : solvedCols) localSolvedColumns.add(c);
        localFinalSolved = finalSolved;

        for (String key : openedCells) {
            int[] pos = parseKey(key);
            if (pos != null) revealCell(pos[0], pos[1]);
        }
        for (int c : solvedCols) markColumnSolved(c);

        if (finalSolved) setInteractionEnabled(false);

        updateGuessButtonStates();
    }

    // ─── Klik na ćeliju ───────────────────────────────────────────────────────

    private void onCellClicked(int col, int row) {
        if (!isMyTurn || turnFinished) return;
        if (cellOpenedThisTurn) return;
        String key = col + "_" + row;
        if (localOpenedCells.contains(key)) return;

        cellOpenedThisTurn = true;
        localOpenedCells.add(key);
        revealCell(col, row);
        updateGuessButtonStates();
        commitOpenCell(key);
    }

    private void revealCell(int col, int row) {
        if (columnLayouts[col] == null) return;
        View child = columnLayouts[col].findViewWithTag("cell_" + col + "_" + row);
        if (child instanceof Button) {
            Button btn = (Button) child;
            btn.setBackgroundColor(0xFF6366F1);
            btn.setTextColor(0xFFFFFFFF);
            if (columnWords[col] != null && row < columnWords[col].size())
                btn.setText(columnWords[col].get(row));
            btn.setEnabled(false);
        }
    }

    private void markColumnSolved(int col) {
        if (columnLayouts[col] == null) return;
        for (int r = 0; r < ROWS; r++) {
            View child = columnLayouts[col].findViewWithTag("cell_" + col + "_" + r);
            if (child instanceof Button) {
                Button btn = (Button) child;
                btn.setBackgroundColor(0xFF22C55E);
                btn.setTextColor(0xFFFFFFFF);
                btn.setEnabled(false);
                // ← ovo nedostaje — upiši reč čak i ako polje nije otvoreno
                if (columnWords[col] != null && r < columnWords[col].size()) {
                    btn.setText(columnWords[col].get(r));
                }
            }
        }
        btnGuessColumn[col].setEnabled(false);
        btnGuessColumn[col].setText(columnSols[col] != null ? columnSols[col] : "✓");
    }

    private void updateGuessButtonStates() {
        if (!isMyTurn || turnFinished) return;
        for (int col = 0; col < COLS; col++) {
            // dugme aktivno samo ako su podaci učitani i kolona nije već rešena
            boolean dataLoaded = columnSols[col] != null;
            btnGuessColumn[col].setEnabled(
                    dataLoaded && !localSolvedColumns.contains(col) && !localFinalSolved);
        }
        btnGuessFinal.setEnabled(finalSolution != null && !localFinalSolved);
    }

    // ─── Pogađanje rešenja kolone ─────────────────────────────────────────────

    private void onGuessColumn(int col) {
        if (!isMyTurn || turnFinished || localSolvedColumns.contains(col)) return;
        showGuessDialog("Rešenje kolone " + COL_IDS[col], guess -> {
            if (guess.trim().equalsIgnoreCase(columnSols[col])) {
                // Tačno – potez OSTAJE, igrač nastavlja
                localSolvedColumns.add(col);
                markColumnSolved(col);
                cellOpenedThisTurn = false;
                updateGuessButtonStates();
                commitColumnSolved(col);
            } else if (opponentPresent()) {
                // Netačno – potez PRELAZI na protivnika
                setInteractionEnabled(false);
                commitTurnLost();
            } else {
                // Protivnik je napustio partiju — ne predajemo potez njemu; zadržavamo
                // potez i dozvoljavamo da ponovo otvorimo polje i nastavimo da igramo.
                keepTurnAfterWrongGuess();
            }
        });
    }

    // ─── Pogađanje krajnjeg rešenja ───────────────────────────────────────────

    private void onGuessFinal() {
        if (!isMyTurn || turnFinished || localFinalSolved) return;
        showGuessDialog("Krajnje rešenje", guess -> {
            if (guess.trim().equalsIgnoreCase(finalSolution)) {
                // Tačno – runda završava, računamo bodove
                localFinalSolved = true;
                setInteractionEnabled(false);
                commitFinalSolved();
            } else if (opponentPresent()) {
                // Netačno – potez PRELAZI na protivnika
                setInteractionEnabled(false);
                commitTurnLost();
            } else {
                // Protivnik je napustio partiju — zadržavamo potez i nastavljamo.
                keepTurnAfterWrongGuess();
            }
        });
    }

    /**
     * Protivnik je odsutan: pogrešan odgovor ne predaje potez. Resetujemo "otvoreno
     * polje u ovom potezu" i ponovo aktiviramo tablu da igrač može da nastavi sam.
     */
    private void keepTurnAfterWrongGuess() {
        cellOpenedThisTurn = false;
        setInteractionEnabled(true);
        updateGuessButtonStates();
    }

    /** Da li je protivnik trenutno prisutan (suprotni slot od mene). */
    private boolean opponentPresent() {
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return true;
        boolean iAmP1 = myUsername.equals(room.getPlayerOne());
        return vm.isPlayerPresent(!iAmP1);
    }

    // ─── Firestore upisi ──────────────────────────────────────────────────────

    private void commitOpenCell(String cellKey) {
        if (!isAdded()) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;

        List<String> newOpened = new ArrayList<>(safeStrings(room.getAsocOpenedCells()));
        if (!newOpened.contains(cellKey)) newOpened.add(cellKey);

        Map<String, Object> updates = new HashMap<>();
        updates.put("asocOpenedCells", newOpened);
        // nema asocTurnPlayer — potez ostaje na meni
        vm.advancePhase(gameId, updates);
    }

    /** Beleži pogođenu kolonu; asocTurnPlayer ostaje isti jer potez ostaje. */
    private void commitColumnSolved(int col) {
        if (!isAdded()) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;

        List<Integer> newSolved = new ArrayList<>(safeIntegers(room.getAsocSolvedColumns()));
        if (!newSolved.contains(col)) newSolved.add(col);

        Map<String, Object> updates = new HashMap<>();
        updates.put("asocSolvedColumns", newSolved);
        // asocTurnPlayer se ne menja
        vm.advancePhase(gameId, updates);
    }

    /** Netačan odgovor – prebacuje potez. */
    private void commitTurnLost() {
        if (!isAdded()) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null) return;
        cellOpenedThisTurn = false;
        Map<String, Object> updates = new HashMap<>();
        updates.put("asocTurnPlayer", otherPlayer(room));
        vm.advancePhase(gameId, updates);
    }


    private void commitFinalSolved() {
        if (!isAdded()) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || activePhase == null) return;

        turnFinished = true;
        cancelTimer();
        completedPhases.add(activePhase);

        boolean isP1 = myUsername.equals(room.getPlayerOne());
        // Prosledi localOpenedCells i localSolvedColumns — ne čekamo Firestore sync
        for (int col = 0; col < COLS; col++) {
            if (!localSolvedColumns.contains(col)) {
                markColumnSolved(col);
            }
        }
        tvStatus.setText("🎉 Pogodio si krajnje rešenje!");
        tvWaiting.setVisibility(View.VISIBLE);
        tvWaiting.setText("Čekaj protivnika...");
        vm.submitAsocScore(gameId, isP1, localOpenedCells, localSolvedColumns,
                nextPhaseAfter(activePhase));
    }

    private void commitTimeExpired() {
        if (!isAdded()) return;
        GameRoom room = vm.gameRoom.getValue();
        if (room == null || activePhase == null) return;

        turnFinished = true;
        completedPhases.add(activePhase);

        Map<String, Object> updates = new HashMap<>();
        updates.put("roundPhase", nextPhaseAfter(activePhase));
        // Resetuj stanje za drugu rundu
        updates.put("asocOpenedCells",   new ArrayList<>());
        updates.put("asocSolvedColumns", new ArrayList<>());
        updates.put("asocTurnPlayer",    "");
        vm.advancePhase(gameId, updates);
    }

    // ─── Bodovanje ────────────────────────────────────────────────────────────

    /**
     * Spec (tačke f, g, h):
     *   Skor kolone           = 2 + neotvorena polja
     *   Krajnje rešenje score = 7 + 6×potpuno_netaknute_kolone + zbir_kolona
     *
     * Koristimo localOpenedCells jer Firestore možda još nije sinkovao poslednje
     * otvoreno polje kada se ova metoda poziva iz commitFinalSolved().
     */
    private int calculateTotalScore(GameRoom room) {
        // Kombinujemo Firestore stanje sa lokalno poznatim (radi u slučaju lag-a)
        Set<String>  openedCells = new HashSet<>(localOpenedCells);
        openedCells.addAll(safeStrings(room.getAsocOpenedCells()));

        Set<Integer> solvedCols = new HashSet<>(localSolvedColumns);
        solvedCols.addAll(safeIntegers(room.getAsocSolvedColumns()));

        int untouchedCount = 0;
        int colScoreSum    = 0;

        for (int col = 0; col < COLS; col++) {
            int     openedInCol = countOpenedInColumn(col, openedCells);
            boolean solved      = solvedCols.contains(col);

            if (!solved && openedInCol == 0) {
                // Potpuno neotkrivena kolona → 6 bodova
                untouchedCount++;
            } else {
                // Rešena ili delimično otvorena → 2 + neotvorena
                colScoreSum += 2 + (ROWS - openedInCol);
            }
        }

        return 7 + 6 * untouchedCount + colScoreSum;
    }

    private int countOpenedInColumn(int col, Set<String> openedCells) {
        int count = 0;
        for (String key : openedCells) {
            int[] pos = parseKey(key);
            if (pos != null && pos[0] == col) count++;
        }
        return count;
    }

    // ─── Timer ────────────────────────────────────────────────────────────────

    private void startTimer(long durationMs) {
        cancelTimer();
        turnTimer = new CountDownTimer(durationMs, 500) {
            @Override public void onTick(long ms) {
                if (!isAdded()) return;
                long sec = ms / 1000;
                tvTimer.setText(sec / 60 + ":" + String.format("%02d", sec % 60));
            }
            @Override public void onFinish() {
                if (!isAdded()) return;
                tvTimer.setText("0:00");
                if (!turnFinished) commitTimeExpired();
            }
        }.start();
    }

    private void cancelTimer() {
        if (turnTimer != null) { turnTimer.cancel(); turnTimer = null; }
    }

    // ─── UI pomoćne metode ────────────────────────────────────────────────────

    private void showWaiting(String message) {
        tvStatus.setText(message);
        tvTimer.setText("");
        tvTimer.setVisibility(View.INVISIBLE);
        tvWaiting.setVisibility(View.VISIBLE);
        tvWaiting.setText(message);
        setInteractionEnabled(false);
    }

    private void setInteractionEnabled(boolean enabled) {
        for (int col = 0; col < COLS; col++) {
            if (columnLayouts[col] != null) {
                for (int row = 0; row < ROWS; row++) {
                    View child = columnLayouts[col].findViewWithTag("cell_" + col + "_" + row);
                    if (child != null) {
                        String key = col + "_" + row;
                        boolean open   = localOpenedCells.contains(key);
                        boolean solved = localSolvedColumns.contains(col);
                        child.setEnabled(enabled && !open && !solved);
                    }
                }
            }
            if (btnGuessColumn[col] != null){
                boolean dataLoaded = columnSols[col] != null;
                btnGuessColumn[col].setEnabled(
                        enabled && dataLoaded && !localSolvedColumns.contains(col) && !localFinalSolved);
            }
        }
        if (btnGuessFinal != null)
            btnGuessFinal.setEnabled(enabled && !localFinalSolved);
    }

    private void showGuessDialog(String hint, java.util.function.Consumer<String> onSubmit) {
        if (!isAdded()) return;
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(hint);
        input.setGravity(Gravity.CENTER);

        new AlertDialog.Builder(requireContext())
                .setTitle("Unesite odgovor")
                .setMessage(hint)
                .setView(input)
                .setPositiveButton("Potvrdi", (d, w) -> {
                    String guess = input.getText().toString().trim();
                    if (!guess.isEmpty()) onSubmit.accept(guess);
                })
                .setNegativeButton("Odustani", null)
                .show();
    }

    // ─── Faze ─────────────────────────────────────────────────────────────────

    private String nextPhaseAfter(String phase) {
        if ("P1_TURN".equals(phase)) return "P2_TURN";
        return "MINIGAME_DONE";
    }

    // ─── Pomoćne metode ───────────────────────────────────────────────────────

    private List<String>  safeStrings(List<String> v)   { return v == null ? new ArrayList<>() : new ArrayList<>(v); }
    private List<Integer> safeIntegers(List<Integer> v) { return v == null ? new ArrayList<>() : new ArrayList<>(v); }
    private String otherPlayer(GameRoom room) {
        return myUsername.equals(room.getPlayerOne()) ? room.getPlayerTwo() : room.getPlayerOne();
    }

    private int[] parseKey(String key) {
        if (key == null) return null;
        String[] parts = key.split("_");
        if (parts.length != 2) return null;
        try { return new int[]{ Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) }; }
        catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object obj) {
        if (obj instanceof List) return (List<String>) obj;
        return new ArrayList<>();
    }

    private int dp(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}