package com.example;

import java.util.*;

public class GameState {

    private final String gameCode;          
    private List<Pergunta> questions;      
    private int currentQuestionIndex = 0;  
    private List<DealWithClient> clientThreads; 

    private int totalTeams;               
    private int playersPerTeam;           
    private int totalQuestions; 
    private boolean roundFinished = false; 

    private QuestionType currentQuestionType = QuestionType.INDIVIDUAL; 
    public enum QuestionType { INDIVIDUAL, TEAM }

    private HashMap<String, String> playerToTeam = new HashMap<>();  
    private HashMap<String, Integer> teamPoints = new HashMap<>();   
    private HashMap<String, Integer> playerPoints = new HashMap<>();
    private HashMap<String, Integer> roundPoints = new HashMap<>();    

    private HashMap<String, Integer> playerResponses = new HashMap<>(); 
    private HashSet<String> respondedPlayers = new HashSet<>();        

    private ModifiedCountDownLatch individualLatch;          
    private ModifiedBarrier teamBarrier;           

    public GameState(String gameCode, List<Pergunta> questions, int totalTeams, int playersPerTeam) {
        this.gameCode = gameCode;
        this.questions = new ArrayList<>(questions);
        this.totalQuestions = questions.size();
        this.totalTeams = totalTeams;
        this.playersPerTeam = playersPerTeam;
        this.clientThreads = new ArrayList<>();
    }

    public String getGameCode() { return gameCode; }
    public int getTotalTeams() { return totalTeams; }
    public int getPlayersPerTeam() { return playersPerTeam; }
    public int getTotalQuestions() { return totalQuestions; }
    public synchronized int getClientThreadsCount() { return clientThreads.size(); }
    public synchronized void registerClient(DealWithClient client) { clientThreads.add(client); }
    public synchronized QuestionType getCurrentType() { return currentQuestionType; }
    public synchronized int getCurrentQuestionIndex (){ return currentQuestionIndex; }
    public synchronized Pergunta getCurrentQuestion() { return questions.get(currentQuestionIndex); }

    public synchronized void assignPlayerToTeam(String username, String teamName) {
        playerToTeam.put(username, teamName);
        teamPoints.putIfAbsent(teamName, 0);   
        playerPoints.putIfAbsent(username, 0); 
    }
    public synchronized void prepareNextRound() {
        if (currentQuestionIndex % 2 == 0) {
            currentQuestionType = QuestionType.INDIVIDUAL;
        } else {
            currentQuestionType = QuestionType.TEAM;
        }

        System.out.println("DEBUG STATE: Ronda " + currentQuestionIndex + " (" + currentQuestionType + ")");

        playerResponses.clear();
        respondedPlayers.clear();
        roundPoints.clear(); 
        this.roundFinished = false; 
        
        int totalPlayers = clientThreads.size();
        int waitTime = 30000;

        if (currentQuestionType == QuestionType.INDIVIDUAL) {
            individualLatch = new ModifiedCountDownLatch(2, 2, waitTime, totalPlayers);
            teamBarrier = null;
        } else {
            teamBarrier = new ModifiedBarrier(totalPlayers, waitTime, () -> {
                System.out.println("DEBUG BARRIER: Barreira cheia. A calcular e avançar.");
                calculateTeamPoints(); 
                synchronized(this) {
                    this.roundFinished = true;
                    notifyAll(); 
                }
            });
            individualLatch = null;
        }
    }

    public boolean registerResponse(String username, Integer answer) {
        int factor = 1;
        String team = playerToTeam.get(username);
        
        ModifiedBarrier barrierThisRound = this.teamBarrier;
        QuestionType typeThisRound = this.currentQuestionType;

        synchronized(this) {
            if (this.roundFinished || respondedPlayers.contains(username)) return false; 
            // Lógica INDIVIDUAL
            if (typeThisRound == QuestionType.INDIVIDUAL && individualLatch != null) {
                if (answer != -1) factor = individualLatch.countdown(); 
                else individualLatch.countdown();
                
                Pergunta p = questions.get(currentQuestionIndex);
                if (answer != -1 && answer == p.getCorrect()) {
                    addPoints(username, p.getPoints() * factor);
                }
            }
            
            playerResponses.put(username, answer);
            respondedPlayers.add(username);

            if (typeThisRound == QuestionType.INDIVIDUAL) {
                if (respondedPlayers.size() == clientThreads.size()) {
                    this.roundFinished = true; 
                    notifyAll(); 
                }
            }
        } 

        if (typeThisRound == QuestionType.TEAM && barrierThisRound != null) {
            try { 
                barrierThisRound.await(); 
            } catch (InterruptedException e) { 
                Thread.currentThread().interrupt(); 
            }
        }
        
        return true;
    }

    public synchronized void waitForAllResponses() {
        long startTime = System.currentTimeMillis();
        long timeout = 30000; 
        
        while (!roundFinished && (System.currentTimeMillis() - startTime < timeout)) {
            try {
                long timeLeft = timeout - (System.currentTimeMillis() - startTime);
                if (timeLeft > 0) wait(timeLeft); 
            } catch (InterruptedException e) { break; }
        }
    }
    
    private synchronized void addPoints(String username, int points) {
        playerPoints.merge(username, points, Integer::sum);
        String team = playerToTeam.get(username);
        if (team != null) {
            teamPoints.merge(team, points, Integer::sum);
            roundPoints.merge(team, points, Integer::sum);
        }
    }

    public synchronized void calculateTeamPoints() {
        if (currentQuestionType != QuestionType.TEAM) return;
        
        Pergunta p = questions.get(currentQuestionIndex);
        int pointsPerQuestion = p.getPoints();
        int correctIndex = p.getCorrect();

        HashMap<String, List<Boolean>> resultsByTeam = new HashMap<>();
        
        for (String team : teamPoints.keySet()) { 
            resultsByTeam.put(team, new ArrayList<>());
        }
        
        for (Map.Entry<String, Integer> entry : playerResponses.entrySet()) {
            String user = entry.getKey();
            Integer ans = entry.getValue();
            String team = playerToTeam.get(user);
            
            if (team != null) {
                boolean correct = (ans == correctIndex);
                resultsByTeam.get(team).add(correct);
            }
        }

        for (Map.Entry<String, List<Boolean>> entry : resultsByTeam.entrySet()) {
            String team = entry.getKey();
            List<Boolean> results = entry.getValue();
            
            int answersCount = results.size();
            boolean everyoneAnswered = (answersCount == this.playersPerTeam);
            
            boolean anyWrong = results.contains(false);
            boolean atLeastOneCorrect = results.contains(true);
            
            int pointsToAward = 0;

            if (everyoneAnswered && !anyWrong) {
                pointsToAward = pointsPerQuestion * 2; 
                System.out.println("DEBUG PONTOS: Equipa " + team + " TODOS acertaram! Bónus Dobro.");
            } 
            else if (atLeastOneCorrect) {
                pointsToAward = pointsPerQuestion;
                System.out.println("DEBUG PONTOS: Equipa " + team + " acertou parcialmente. Pontos normais.");
            } else {
                System.out.println("DEBUG PONTOS: Equipa " + team + " falhou completamente.");
            }

            if (pointsToAward > 0) {
                teamPoints.merge(team, pointsToAward, Integer::sum);
                roundPoints.merge(team, pointsToAward, Integer::sum);
            }
        }
    }

    public void broadcast(String msg) {
        for (DealWithClient client : clientThreads) {
            client.sendMessage(msg);
        }
    }
    
    public synchronized String getLeaderboard() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : teamPoints.entrySet()) {
            String team = entry.getKey();
            int total = entry.getValue();
            int rPoints = roundPoints.get(team) != null ? roundPoints.get(team) : 0;
            sb.append(team).append(": ").append(total).append(" (+").append(rPoints).append(");  ");
        }
        if (sb.length() == 0) return "A aguardar pontuações...";
        return sb.toString();
    }
    
    public boolean hasMoreQuestions() { return currentQuestionIndex < totalQuestions; }
    public synchronized void nextQuestion() { currentQuestionIndex++; }
}