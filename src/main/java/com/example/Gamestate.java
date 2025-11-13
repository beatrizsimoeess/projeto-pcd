package com.example;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;


public class Gamestate {

    // 1- info

    private final String gameCode;          
    private List<Pergunta> questions;      
    private int currentQuestionIndex = 0;  
    private List<Jogador> clientThreads;   

    private int totalTeams;               
    private int playersPerTeam;           
    private int totalQuestions;           
    private QuestionType currentQuestionType; 
    public enum QuestionType {
        INDIVIDUAL, 
        EQUIPA     
    }

    // 2- pontuações

    private Map<String, String> playerToTeam = new HashMap<>();  
    private Map<String, Integer> teamPoints = new HashMap<>();   
    private Map<String, Integer> playerPoints = new HashMap<>(); 
    private Map<String, Integer> roundPoints = new HashMap<>();   

    // 3- respostas
    
    private Map<String, Integer> playerResponses = new HashMap<>(); 
    private Set<String> respondedPlayers = new HashSet<>();       

    // 4- timers e sincro

    private Timer roundTimer;                                 
    private ModifiedCountDownLatch individualLatch;          
    private ModifiedBarrier<Integer> teamBarrier;           
    private Lock lock = new ReentrantLock();                 


    public String getGameCode() { return gameCode; }
    public int getTotalTeams() { return totalTeams; }
    public int getPlayersPerTeam() { return playersPerTeam; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public List<Pergunta> getQuestionts() {return questions;}

    public Gamestate(String gameCode, List<Pergunta> questions, int totalTeams, int playersPerTeam) {
        this.gameCode = gameCode;
        this.questions = new ArrayList<>(questions);
        this.totalQuestions = questions.size();
        this.totalTeams = totalTeams;
        this.playersPerTeam = playersPerTeam;
        this.clientThreads = new ArrayList<>();
        this.currentQuestionIndex = 0;
        this.currentQuestionType = QuestionType.INDIVIDUAL; 
        this.playerToTeam = new HashMap<>();
        this.teamPoints = new HashMap<>();
        this.playerPoints = new HashMap<>();
        this.roundPoints = new HashMap<>();
        this.playerResponses = new HashMap<>();
        this.respondedPlayers = new HashSet<>();
        this.lock = new ReentrantLock();
        this.individualLatch = null;
        this.teamBarrier = null;
        this.roundTimer = null;
    }

    // ============================
    // jogadores
 
    public synchronized void registerClient(Jogador jogador) {
        clientThreads.add(jogador);
    }

 
    public synchronized void assignPlayerToTeam(String username, String teamName) {
        playerToTeam.put(username, teamName);
        teamPoints.putIfAbsent(teamName, 0);   
        playerPoints.putIfAbsent(username, 0); 
    }

   
    public synchronized Pergunta getCurrentQuestion() {
        return questions.get(currentQuestionIndex);
    }

    public synchronized void nextQuestion() {
        currentQuestionIndex++;
        playerResponses.clear();
        respondedPlayers.clear();
        roundPoints.clear();
    }

 
    public boolean hasMoreQuestions() {
        return currentQuestionIndex < totalQuestions;
    }

    // respostas

    public boolean registerResponse(String username, Integer answer) {
        lock.lock();
        try {
            if (respondedPlayers.contains(username))
                return false;
            playerResponses.put(username, answer);
            respondedPlayers.add(username);
            return true;
        } finally {
            lock.unlock();
        }
    }


    public boolean allPlayersAnswered(int totalExpected) {
        lock.lock();
        try {
            return respondedPlayers.size() >= totalExpected;
        } finally {
            lock.unlock();
        }
    }

    
    public Map<String, Integer> getPlayerResponsesSnapshot() {
        lock.lock();
        try {
            return new HashMap<>(playerResponses);
        } finally {
            lock.unlock();
        }
    }

    // pontos
    // ============================

    public synchronized void addPointsToPlayer(String username, int points) {
        playerPoints.merge(username, points, Integer::sum);
        String team = playerToTeam.get(username);
        if (team != null) {
            teamPoints.merge(team, points, Integer::sum);
        }
        roundPoints.put(username, points); 
    }

  
    public synchronized void addPointsToTeam(String teamName, int points) {
        teamPoints.merge(teamName, points, Integer::sum);
    }

  
    public synchronized Map<String, Integer> getTeamLeaderboard() {
        return new HashMap<>(teamPoints);
    }

   
    public synchronized Map<String, Integer> getRoundPoints() {
        return new HashMap<>(roundPoints);
    }

    //sinc

   
    public void initTeamBarrier(int teamSize, long timeoutMillis, Runnable barrierAction) {
        this.teamBarrier = new ModifiedBarrier<>(teamSize, timeoutMillis, responses -> barrierAction.run());
    }

   
    public void initIndividualLatch(int totalPlayers, long timeoutMillis) {
        this.individualLatch = new ModifiedCountDownLatch(totalPlayers, timeoutMillis);
    }

 
    public void stopAllClients() {
        for (Jogador client : clientThreads) {
            client.interrupt();
        }
    }
}
