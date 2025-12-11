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
    public enum QuestionType {
        INDIVIDUAL, 
        TEAM     
    }

    // pontuações
    private Hashmap<String, String> playerToTeam = new Hashmap<>();  
    private Hashmap<String, Integer> teamPoints = new Hashmap<>();   
    private Hashmap<String, Integer> playerPoints = new Hashmap<>();
    private Hashmap<String, Integer> roundPoints = new Hashmap<>();   

    // 3- respostas
    private Hashmap<String, Integer> playerResponses = new Hashmap<>(); 
    private Hashset<String> respondedPlayers = new Hashset<>();       

    // 4- timers e sincro
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

    public String getGameCode() {return gameCode; }
    public int getTotalTeams() { return totalTeams; }
    public int getPlayersPerTeam() { return playersPerTeam; }
    public int getTotalQuestions() { return totalQuestions; }
    public List<Pergunta> getQuestionts() {return questions;}

    public synchronized int getClientThreadsCount() {
        return clientThreads.size();
    }

        public synchronized void nextQuestion() { currentQuestionIndex++; }
    
    public synchronized void registerClient(DealWithClient client) {
        clientThreads.add(client);
    }

    public synchronized QuestionType getCurrentType() { 
        return currentQuestionType; 
    }

    public synchronized int getCurrentQuestionIndex (){
        return currentQuestionIndex;
    }

    public synchronized void assignPlayerToTeam(String username, String teamName) {
        playerToTeam.put(username, teamName);
        teamPoints.putIfAbsent(teamName, 0);   
        playerPoints.putIfAbsent(username, 0); 
    }


    public synchronized Pergunta getCurrentQuestion() {
        return questions.get(currentQuestionIndex);
    }

    public synchronized void prepareNextRound() {
        if (currentQuestionIndex == 0) {
            currentQuestionType = QuestionType.INDIVIDUAL;
        } else {
            currentQuestionType = (currentQuestionType == QuestionType.INDIVIDUAL) ? QuestionType.TEAM: QuestionType.INDIVIDUAL;
        }

    System.out.println("DEBUG STATE: Ronda " + currentQuestionIndex + " definida como " + currentQuestionType);

        playerResponses.clear();
        respondedPlayers.clear();
        roundPoints.clear(); 
        
        int totalPlayers = clientThreads.size();
        int waitTime = 30000;
        this.roundFinished = false;

        if (currentQuestionType == QuestionType.INDIVIDUAL) {
        // Latch: Bónus x2 para os primeiros 2 jogadores [cite: 78, 84]
            individualLatch = new ModifiedCountDownLatch(2, 2, waitTime, totalPlayers);
            teamBarrier = null;
        } else {
            // Barreira Global para equipas [cite: 91]
            teamBarrier = new ModifiedBarrier(totalPlayers, waitTime, () -> {});
            individualLatch = null;
        }
    }


    public boolean hasMoreQuestions() {
        return currentQuestionIndex < totalQuestions;
    }

    // respostas

    public void broadcast(String msg) {
        for (DealWithClient client : clientThreads) {
            client.sendMessage(msg);
        }
    }

    
    public boolean registerResponse(String username, Integer answer) {
        int factor = 1;
        boolean isIndividual = false;
        
        synchronized(this) {
            if (respondedPlayers.contains(username)) return false; 
            
            if (currentQuestionType == QuestionType.INDIVIDUAL && individualLatch != null) {
                factor = individualLatch.countdown(); // Retorna 2 (bónus) ou 1
                isIndividual = true;
            }
            
            playerResponses.put(username, answer);
            respondedPlayers.add(username);

            if (respondedPlayers.size() == clientThreads.size()) {
                roundFinished = true;
                notifyAll();
            }
        }

        if (isIndividual) {
            Pergunta p = questions.get(currentQuestionIndex);
            System.out.println("DEBUG INDIVIDUAL: Jogador=" + username +  " | Resposta=" + answer + " | Correta=" + p.getCorrect() +" | Fator=" + factor);
            if (answer == p.getCorrect()) {
                addPoints(username, p.getPoints() * factor);
            }
        }
        
        if (currentQuestionType == QuestionType.TEAM && teamBarrier != null) {
            try { teamBarrier.await(); } catch (InterruptedException e) {}
        }
        
        return true;
    }


    public synchronized boolean allPlayersAnswered(int totalExpected) {
        return respondedPlayers.size() >= totalExpected;
    }

    public synchronized void waitForAllResponses() {
        long startTime = System.currentTimeMillis();
        long timeout = 30000; 
        
        // Fica preso aqui enquanto a ronda não acabar E houver tempo
        while (!roundFinished && (System.currentTimeMillis() - startTime < timeout)) {
            try {
                long timeLeft = timeout - (System.currentTimeMillis() - startTime);
                if (timeLeft > 0) wait(timeLeft); // Espera pelo notifyAll() no registerResponse
            } catch (InterruptedException e) {
                break;
            }
        }
        System.out.println("DEBUG: Servidor parou de esperar (Tempo acabou ou todos responderam).");
    }


    // pontos

    private synchronized void addPoints(String username, int points) {
        // 1. Total Jogador
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
        
        for (Map.Entry<String, Integer> entry : playerResponses.entrySet()) {
            String user = entry.getKey();
            int ans = entry.getValue();
            System.out.println("DEBUG EQUIPA: " + user + " respondeu " + ans + " (Correta: " + p.getCorrect() + ")");
            if (ans == p.getCorrect()) {
                addPoints(user, p.getPoints()); 
            }
        }
    }


    public synchronized void addPointsToTeam(String teamName, int points) {
        teamPoints.merge(teamName, points, Integer::sum);
    }

    public void waitForRoundFinish() {
        if (currentQuestionType == QuestionType.INDIVIDUAL && individualLatch != null) {
            try { individualLatch.await(); } catch (InterruptedException e) {}
        }
    }
    


    // Gera o texto para o placar [cite: 40]
    public synchronized String getLeaderboard() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : teamPoints.entrySet()) {
            String team = entry.getKey();
            int total = entry.getValue();
            int rPoints = roundPoints.get(team) != null ? roundPoints.get(team) : 0;
            
            sb.append(team).append(": ").append(total).append(" (Nesta ronda: +").append(rPoints).append(");  ");
        }
        if (sb.length() == 0) return "Sem pontuacoes ainda.";
        return sb.toString();
    }
}