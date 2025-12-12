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


    private HashMap<String, String> playerToTeam = new HashMap<>();  
    private HashMap<String, Integer> teamPoints = new HashMap<>();   
    private HashMap<String, Integer> playerPoints = new HashMap<>();
    private HashMap<String, Integer> roundPoints = new HashMap<>();   

    private HashMap<String, Integer> playerResponses = new HashMap<>(); 
    private HashSet<String> respondedPlayers = new HashSet<>();       

    private HashSet<String> respondedTeams = new HashSet<>();       

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
        
        respondedTeams.clear();
        
        int totalPlayers = clientThreads.size();
        int waitTime = 30000;
        this.roundFinished = false; 

        if (currentQuestionType == QuestionType.INDIVIDUAL) {
            individualLatch = new ModifiedCountDownLatch(2, 2, waitTime, totalPlayers);
            teamBarrier = null;
        } else {
        	teamBarrier = new ModifiedBarrier(totalPlayers, waitTime, () -> {
        	    if (currentQuestionType == QuestionType.TEAM) { 
        	        System.out.println("DEBUG BARRIER: Barreira atingida (ou timeout). A calcular pontos de equipa.");
        	        calculateTeamPoints(); 
        	    }
        	});
            individualLatch = null;
        }
    }


    public boolean hasMoreQuestions() {
        return currentQuestionIndex < totalQuestions;
    }


    public void broadcast(String msg) {
        for (DealWithClient client : clientThreads) {
            client.sendMessage(msg);
        }
    }

    public boolean registerResponse(String username, Integer answer) {
        int factor = 1;
        String team = playerToTeam.get(username);
        
        if (team == null) {
             System.err.println("ERRO FATAL: Jogador " + username + " não atribuído a uma equipa!");
             return false;
        }
        
        synchronized(this) {
            if (this.roundFinished || respondedPlayers.contains(username)) return false; 
            
            // --- 1. Lógica INDIVIDUAL (e Bónus) ---
            if (currentQuestionType == QuestionType.INDIVIDUAL && individualLatch != null) {
                
                if (answer != -1) {
                    factor = individualLatch.countdown(); 
                } else {
                    factor = 1; 
                    individualLatch.countdown(); 
                }
                
                Pergunta p = questions.get(currentQuestionIndex);
                
                if (answer != -1 && answer == p.getCorrect()) {
                    addPoints(username, p.getPoints() * factor);
                }
            }
            
            // --- 2. Registo da Resposta e Rastreio de Respostas Individuais ---
            playerResponses.put(username, answer);
            respondedPlayers.add(username);

            // --- 3. Condição de Fim de Ronda (CORREÇÃO E VERIFICAÇÃO CRÍTICA) ---
            boolean shouldFinish = false;
            
            if (currentQuestionType == QuestionType.TEAM) {
                
                respondedTeams.add(team); 
                shouldFinish = (respondedTeams.size() == totalTeams);
                
                // *** DEBUG CRÍTICO AQUI ***
                System.out.println("--- DEBUG FIM RONDA TEAM ---");
                System.out.println("Equipas que responderam: " + respondedTeams.size());
                System.out.println("Total de Equipas esperadas: " + totalTeams);
                System.out.println("Ronda deve terminar: " + shouldFinish);
                
            } else { // INDIVIDUAL
                
                shouldFinish = (respondedPlayers.size() == clientThreads.size());
                
                // *** DEBUG CRÍTICO AQUI ***
                System.out.println("--- DEBUG FIM RONDA INDIVIDUAL ---");
                System.out.println("Jogadores que responderam: " + respondedPlayers.size());
                System.out.println("Total de Jogadores esperados: " + clientThreads.size());
                System.out.println("Ronda deve terminar: " + shouldFinish);
            }

            if (shouldFinish) {
                this.roundFinished = true; 
                notifyAll();               // Acorda o Servidor
            }

        } // FIM DO SYNCHRONIZED BLOCK

        // --- 4. Lógica TEAM (Barreira) ---
        if (currentQuestionType == QuestionType.TEAM && teamBarrier != null) {
            try { teamBarrier.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
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
                else break; 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("DEBUG: Servidor parou de esperar (Tempo acabou ou todos responderam).");
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
        
        for (Map.Entry<String, Integer> entry : playerResponses.entrySet()) {
            String user = entry.getKey();
            int ans = entry.getValue();
            if (ans == -1) continue;
            
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