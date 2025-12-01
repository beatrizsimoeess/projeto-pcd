package com.example;

import java.util.*;


public class GameState {

    // 1- info

    private final String gameCode;          
    private List<Pergunta> questions;      
    private int currentQuestionIndex = 0;  
    private List<DealWithClient> clientThreads; 

    private int totalTeams;               
    private int playersPerTeam;           
    private int totalQuestions;           
    private QuestionType currentQuestionType; 
    public enum QuestionType {
        INDIVIDUAL, 
        EQUIPA     
    }

    // 2- pontuações
    private map<String, String> playerToTeam = new Hashmap<>();  
    private map<String, Integer> teamPoints = new Hashmap<>();   
    private map<String, Integer> playerPoints = new Hashmap<>(); 
    private map<String, Integer> roundPoints = new Hashmap<>();   

    // 3- respostas
    private map<String, Integer> playerResponses = new Hashmap<>(); 
    private set<String> respondedPlayers = new Hashset<>();       

    // 4- timers e sincro
    private ModifiedCountDownLatch individualLatch;          
    private ModifiedBarrier<Integer> teamBarrier;           


    public String getGameCode() {return gameCode; }
    public int getTotalTeams() { return totalTeams; }
    public int getPlayersPerTeam() { return playersPerTeam; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public List<Pergunta> getQuestionts() {return questions;}
    

    public GameState(String gameCode, List<Pergunta> questions, int totalTeams, int playersPerTeam) {
        this.gameCode = gameCode;
        this.questions = new ArrayList<>(questions);
        this.totalQuestions = questions.size();
        this.totalTeams = totalTeams;
        this.playersPerTeam = playersPerTeam;
        this.clientThreads = new ArrayList<>(); 
        this.currentQuestionIndex = 0;
        this.currentQuestionType = QuestionType.INDIVIDUAL; 
        
        this.playerToTeam = new Hashmap<>();
        this.teamPoints = new Hashmap<>();
        this.playerPoints = new Hashmap<>();
        this.roundPoints = new Hashmap<>();
        this.playerResponses = new Hashmap<>();
        this.respondedPlayers = new Hashset<>();

        this.individualLatch = null;
        this.teamBarrier = null;
    }

    public synchronized int getClientThreadsCount() {
        return clientThreads.size();
    }
    
 
    public synchronized void registerClient(DealWithClient jogador) {
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
    
    public synchronized boolean registerResponse(String username, Integer answer) {
        if (respondedPlayers.contains(username))
            return false;
        playerResponses.put(username, answer);
        respondedPlayers.add(username);
        return true;
    }


    public synchronized boolean allPlayersAnswered(int totalExpected) {
        return respondedPlayers.size() >= totalExpected;
    }

    
    public synchronized map<String, Integer> getPlayerResponsesSnapshot() {
        return playerResponses.cloneMap();
    }

    // pontos

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

  
    public synchronized map<String, Integer> getTeamLeaderboard() {
        return teamPoints.cloneMap();
    }

   
    public synchronized map<String, Integer> getRoundPoints() {
        return roundPoints.cloneMap();
    }

    //sinc
   
    public synchronized void initTeamBarrier(int teamSize, long timeoutMillis, Runnable barrierAction) {        
        this.teamBarrier = new ModifiedBarrier<>(teamSize, timeoutMillis, responses -> barrierAction);
    }

    public synchronized void initIndividualLatch(int totalPlayers, long timeoutMillis) {   
        this.individualLatch = new ModifiedCountDownLatch(totalPlayers, timeoutMillis);
    }
 
    public synchronized void stopAllClients() {
        for (DealWithClient client : clientThreads) {
            client.interrupt();
        }
    }
}