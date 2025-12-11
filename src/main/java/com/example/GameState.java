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

    // pontuações (Corrigido Hashmap para HashMap)
    private HashMap<String, String> playerToTeam = new HashMap<>();  
    private HashMap<String, Integer> teamPoints = new HashMap<>();   
    private HashMap<String, Integer> playerPoints = new HashMap<>();
    private HashMap<String, Integer> roundPoints = new HashMap<>();   

    // 3- respostas (Corrigido Hashset para HashSet)
    private HashMap<String, Integer> playerResponses = new HashMap<>(); 
    private HashSet<String> respondedPlayers = new HashSet<>();       

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
        this.roundFinished = false; // Fica aqui

        if (currentQuestionType == QuestionType.INDIVIDUAL) {
        // Latch: Bónus x2 para os primeiros 2 jogadores
            individualLatch = new ModifiedCountDownLatch(2, 2, waitTime, totalPlayers);
            teamBarrier = null;
        } else {
        	teamBarrier = new ModifiedBarrier(totalPlayers, waitTime, () -> {
        	    // CORREÇÃO: Adicionar verificação do estado para evitar corrida de dados
        	    if (currentQuestionType == QuestionType.TEAM) { // Verificação redundante mas segura
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

    // respostas

    public void broadcast(String msg) {
        for (DealWithClient client : clientThreads) {
            client.sendMessage(msg);
        }
    }

 // GameState.java

    public boolean registerResponse(String username, Integer answer) {
    	int factor = 1;
        
        synchronized(this) {
            if (this.roundFinished || respondedPlayers.contains(username)) return false; 
            
            // --- 1. Lógica INDIVIDUAL, Atribuição de Fator e CÁLCULO DE PONTOS ---
            if (currentQuestionType == QuestionType.INDIVIDUAL && individualLatch != null) {
                
                // CORREÇÃO: Só aplica o countdown (e bónus) se o jogador realmente respondeu (answer != -1)
                if (answer != -1) {
                    factor = individualLatch.countdown(); // Retorna 2 (bónus) ou 1
                } else {
                    factor = 1; // Sem resposta = sem bónus/sem pontuação
                    individualLatch.countdown(); // Necessário para a contagem total
                }
                
                Pergunta p = questions.get(currentQuestionIndex);

                System.out.println("DEBUG INDIVIDUAL: Jogador=" + username +  " | Resposta=" + answer + " | Correta=" + p.getCorrect() +" | Fator=" + factor);
                
                // Pontua apenas se a resposta não for nula (-1) E for correta
                if (answer != -1 && answer == p.getCorrect()) {
                    // Apenas aqui a pontuação é adicionada
                    addPoints(username, p.getPoints() * factor);
                }
            }
            
            // --- 2. Registo da Resposta ---
            playerResponses.put(username, answer);
            respondedPlayers.add(username);

            // --- 3. Fim da Ronda e Notificação ---
            if (respondedPlayers.size() == clientThreads.size()) {
                this.roundFinished = true; // Seta o flag
                notifyAll();               // Acorda o Servidor
            }
        } // FIM DO SYNCHRONIZED BLOCK

        // --- 4. Lógica TEAM (Barreira) - Permanece fora do sync ---
        if (currentQuestionType == QuestionType.TEAM && teamBarrier != null) {
            // Os jogadores que respondem chamam await() para sincronizar
            try { teamBarrier.await(); } catch (InterruptedException e) {}
        }
        
        return true;
    }


 // GameState.java

    public synchronized void waitForAllResponses() {
        long start = System.currentTimeMillis();
        long timeout = 30000;

        while (!roundFinished) {
            long elapsed = System.currentTimeMillis() - start;
            long remaining = timeout - elapsed;

            if (remaining <= 0) {
                // Timeout real
                roundFinished = true;
                System.out.println("DEBUG TIMEOUT: Ronda concluída por timeout.");
                break;
            }

            try {
                wait(remaining);
            } catch (InterruptedException e) {
                // CORREÇÃO: Quando ocorre uma interrupção (e.g., por notifyAll()),
                // garantimos que o estado de interrupção é restaurado.
                Thread.currentThread().interrupt(); 
                // O loop será reavaliado. Se roundFinished=true, saímos.
            }
        }
        
        // O roundFinished é agora definido dentro do while/if(remaining <= 0) ou no registerResponse
        
        System.out.println("DEBUG: Ronda terminou. roundFinished=" + roundFinished);
    }
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
            // Pontua apenas se a resposta não for nula (-1)
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
        // Para a ronda TEAM, a lógica é tratada pela Barreira.
    }
    


    // Gera o texto para o placar
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