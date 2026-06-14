package com.example.myapplication.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameRoom {

    private String gameId;
    private String playerOne;
    private String playerTwo;
    private String gameState;
    private long createdAt;
    // Prijateljska partija: ne dodeljuje zvezde/statistiku/rang i ne troši tokene.
    private boolean friendly;
    // Korisničko ime igrača koji je napustio partiju (null ako niko nije napustio).
    private String leftPlayer;
    private List<String> minigamePlaylist;
    private int currentMinigameIndex;
    private int playerOneScore;
    private int playerTwoScore;

    private String roundPhase;
    private int playerOneRoundScore;
    private int playerTwoRoundScore;
    private int roundNumber;

    private String currentMinigameType;

    private List<Map<String, Object>> p1GuessHistory = new ArrayList<>();
    private List<Map<String, Object>> p2GuessHistory = new ArrayList<>();


    private Object mojBrojTarget;
    private List<Object> mojBrojNumbers;
    private int    mojBrojP1Result;
    private int    mojBrojP2Result;
    private boolean mojBrojP1Submitted;
    private boolean mojBrojP2Submitted;

    private int quizQuestionIndex;
    private long quizQuestionStartedAt;
    private boolean quizP1Answered;
    private boolean quizP2Answered;
    private boolean quizP1Correct;
    private boolean quizP2Correct;
    private boolean quizCorrectClaimed;

    private Map<String, String> spojniceMatches = new HashMap<>();

    private int asocRoundIndex;
    private List<String> asocOpenedCells = new ArrayList<>();
    private List<Integer> asocSolvedColumns = new ArrayList<>();
    private String asocTurnPlayer;
    private boolean asocFinalSolved;
    private long asocRoundStartedAt;
    private String asocQuestionId;

    public GameRoom() {}

    public GameRoom(String gameId, String playerOne, String playerTwo, List<String> playlist) {
        this.gameId = gameId;
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
        this.minigamePlaylist = playlist;
        this.currentMinigameIndex = 0;
        this.gameState = "PLAYING";
        this.playerOneScore = 0;
        this.playerTwoScore = 0;
        this.createdAt = System.currentTimeMillis();

        this.roundNumber = 0;
        this.roundPhase = "P1_TURN";
        this.playerOneRoundScore = 0;
        this.playerTwoRoundScore = 0;

    }

    // --- Existing getters/setters ---
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }

    public String getPlayerOne() { return playerOne; }
    public void setPlayerOne(String playerOne) { this.playerOne = playerOne; }

    public String getPlayerTwo() { return playerTwo; }
    public void setPlayerTwo(String playerTwo) { this.playerTwo = playerTwo; }

    public String getGameState() { return gameState; }
    public void setGameState(String gameState) { this.gameState = gameState; }

    public boolean isFriendly() { return friendly; }
    public void setFriendly(boolean friendly) { this.friendly = friendly; }

    public String getLeftPlayer() { return leftPlayer; }
    public void setLeftPlayer(String leftPlayer) { this.leftPlayer = leftPlayer; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public List<String> getMinigamePlaylist() { return minigamePlaylist; }
    public void setMinigamePlaylist(List<String> minigamePlaylist) { this.minigamePlaylist = minigamePlaylist; }

    public int getCurrentMinigameIndex() { return currentMinigameIndex; }
    public void setCurrentMinigameIndex(int currentMinigameIndex) { this.currentMinigameIndex = currentMinigameIndex; }

    public int getPlayerOneScore() { return playerOneScore; }
    public void setPlayerOneScore(int playerOneScore) { this.playerOneScore = playerOneScore; }

    public int getPlayerTwoScore() { return playerTwoScore; }
    public void setPlayerTwoScore(int playerTwoScore) { this.playerTwoScore = playerTwoScore; }

    public String getRoundPhase() { return roundPhase; }
    public void setRoundPhase(String roundPhase) { this.roundPhase = roundPhase; }

    public int getPlayerOneRoundScore() { return playerOneRoundScore; }
    public void setPlayerOneRoundScore(int s) { this.playerOneRoundScore = s; }

    public int getPlayerTwoRoundScore() { return playerTwoRoundScore; }
    public void setPlayerTwoRoundScore(int s) { this.playerTwoRoundScore = s; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public String getCurrentMinigameType() { return currentMinigameType; }
    public void setCurrentMinigameType(String currentMinigameType) { this.currentMinigameType = currentMinigameType; }

    public List<Map<String, Object>> getP1GuessHistory() { return p1GuessHistory; }
    public void setP1GuessHistory(List<Map<String, Object>> p1GuessHistory) { this.p1GuessHistory = p1GuessHistory; }

    public List<Map<String, Object>> getP2GuessHistory() { return p2GuessHistory; }
    public void setP2GuessHistory(List<Map<String, Object>> p2GuessHistory) { this.p2GuessHistory = p2GuessHistory; }

    public Object getMojBrojTarget() { return mojBrojTarget; }
    public void setMojBrojTarget(Object mojBrojTarget) { this.mojBrojTarget = mojBrojTarget; }

    public List<Object> getMojBrojNumbers() { return mojBrojNumbers; }
    public void setMojBrojNumbers(List<Object> mojBrojNumbers) { this.mojBrojNumbers = mojBrojNumbers; }

    public int getMojBrojP1Result() { return mojBrojP1Result; }
    public void setMojBrojP1Result(int mojBrojP1Result) { this.mojBrojP1Result = mojBrojP1Result; }

    public int getMojBrojP2Result() { return mojBrojP2Result; }
    public void setMojBrojP2Result(int mojBrojP2Result) { this.mojBrojP2Result = mojBrojP2Result; }

    public Boolean getMojBrojP1Submitted() { return mojBrojP1Submitted; }
    public void setMojBrojP1Submitted(boolean mojBrojP1Submitted) { this.mojBrojP1Submitted = mojBrojP1Submitted; }

    public Boolean getMojBrojP2Submitted() { return mojBrojP2Submitted; }
    public void setMojBrojP2Submitted(boolean mojBrojP2Submitted) { this.mojBrojP2Submitted = mojBrojP2Submitted; }

    public int getQuizQuestionIndex() { return quizQuestionIndex; }
    public void setQuizQuestionIndex(int quizQuestionIndex) { this.quizQuestionIndex = quizQuestionIndex; }

    public long getQuizQuestionStartedAt() { return quizQuestionStartedAt; }
    public void setQuizQuestionStartedAt(long quizQuestionStartedAt) { this.quizQuestionStartedAt = quizQuestionStartedAt; }

    public Boolean isQuizP1Answered() { return quizP1Answered; }
    public void setQuizP1Answered(boolean quizP1Answered) { this.quizP1Answered = quizP1Answered; }

    public Boolean isQuizP2Answered() { return quizP2Answered; }
    public void setQuizP2Answered(boolean quizP2Answered) { this.quizP2Answered = quizP2Answered; }

    public Boolean getQuizP1Correct() { return quizP1Correct; }
    public void setQuizP1Correct(boolean quizP1Correct) { this.quizP1Correct = quizP1Correct; }

    public Boolean getQuizP2Correct() { return quizP2Correct; }
    public void setQuizP2Correct(boolean quizP2Correct) { this.quizP2Correct = quizP2Correct; }

    public Boolean getQuizCorrectClaimed() { return quizCorrectClaimed; }
    public void setQuizCorrectClaimed(boolean quizCorrectClaimed) { this.quizCorrectClaimed = quizCorrectClaimed; }

    public Map<String, String> getSpojniceMatches() {
        return spojniceMatches;
    }

    public void setSpojniceMatches(Map<String, String> spojniceMatches) {
        this.spojniceMatches = spojniceMatches;
    }
    public int getAsocRoundIndex() { return asocRoundIndex; }
    public void setAsocRoundIndex(int asocRoundIndex) { this.asocRoundIndex = asocRoundIndex; }

    public List<String> getAsocOpenedCells() { return asocOpenedCells; }
    public void setAsocOpenedCells(List<String> asocOpenedCells) { this.asocOpenedCells = asocOpenedCells; }

    public List<Integer> getAsocSolvedColumns() { return asocSolvedColumns; }
    public void setAsocSolvedColumns(List<Integer> asocSolvedColumns) { this.asocSolvedColumns = asocSolvedColumns; }

    public String getAsocQuestionId() { return asocQuestionId; }
    public void setAsocQuestionId(String v) { asocQuestionId = v; }
    public String getAsocTurnPlayer() { return asocTurnPlayer; }
    public void setAsocTurnPlayer(String asocTurnPlayer) { this.asocTurnPlayer = asocTurnPlayer; }

    public Boolean getAsocFinalSolved() { return asocFinalSolved; }
    public void setAsocFinalSolved(boolean asocFinalSolved) { this.asocFinalSolved = asocFinalSolved; }

    public long getAsocRoundStartedAt() { return asocRoundStartedAt; }
    public void setAsocRoundStartedAt(long asocRoundStartedAt) { this.asocRoundStartedAt = asocRoundStartedAt; }
}