package com.example.myapplication.data.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GameRoom {

    private String gameId;
    private String playerOne;
    private String playerTwo;
    private String gameState; // "PLAYING", "FINISHED"
    private long createdAt;
    private List<String> minigamePlaylist;
    private int currentMinigameIndex;
    private int playerOneScore;
    private int playerTwoScore;

    private String roundPhase; // "P1_TURN", "P2_BONUS", "P2_TURN", "P1_BONUS", "SHOWING_RESULTS"
    private int playerOneRoundScore;
    private int playerTwoRoundScore;
    private int roundNumber;

    private String currentMinigameType; // "skocko", "memory", "quiz", etc.

    // Each entry: { "guess": ["skocko","srce",...], "feedback": ["CORRECT","ABSENT",...] }
    private List<Map<String, Object>> p1GuessHistory = new ArrayList<>();
    private List<Map<String, Object>> p2GuessHistory = new ArrayList<>();

    // Moj Broj per-round fields
    private Object mojBrojTarget;
    private List<Object> mojBrojNumbers;
    private int    mojBrojP1Result;
    private int    mojBrojP2Result;
    private boolean mojBrojP1Submitted;
    private boolean mojBrojP2Submitted;

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
}