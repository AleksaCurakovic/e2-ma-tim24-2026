package com.example.myapplication.data.model;

import java.util.ArrayList;
import java.util.List;

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

    // --- Skocko specific ---
    // Stores the docId of the skocko puzzle (same for both players, each reads p1/p2 solution)
    private String skockoDocId;

    // P1's guesses saved when their turn ends (so P2 can see during P2_BONUS)
    private List<List<String>> p1Attempts; // each attempt is a list of 4 symbols
    private boolean p1Solved;              // did P1 find the solution?

    // P2's guesses saved when their turn ends (so P1 can see during P1_BONUS)
    private List<List<String>> p2Attempts;
    private boolean p2Solved;

    // Current minigame type so fragments know what to load
    private String currentMinigameType; // "skocko", "memory", "quiz", etc.

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

        this.p1Attempts = new ArrayList<>();
        this.p2Attempts = new ArrayList<>();
        this.p1Solved = false;
        this.p2Solved = false;
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

    // --- Skocko getters/setters ---
    public String getSkockoDocId() { return skockoDocId; }
    public void setSkockoDocId(String skockoDocId) { this.skockoDocId = skockoDocId; }

    public List<List<String>> getP1Attempts() { return p1Attempts; }
    public void setP1Attempts(List<List<String>> p1Attempts) { this.p1Attempts = p1Attempts; }

    public boolean isP1Solved() { return p1Solved; }
    public void setP1Solved(boolean p1Solved) { this.p1Solved = p1Solved; }

    public List<List<String>> getP2Attempts() { return p2Attempts; }
    public void setP2Attempts(List<List<String>> p2Attempts) { this.p2Attempts = p2Attempts; }

    public boolean isP2Solved() { return p2Solved; }
    public void setP2Solved(boolean p2Solved) { this.p2Solved = p2Solved; }

    public String getCurrentMinigameType() { return currentMinigameType; }
    public void setCurrentMinigameType(String currentMinigameType) { this.currentMinigameType = currentMinigameType; }
}