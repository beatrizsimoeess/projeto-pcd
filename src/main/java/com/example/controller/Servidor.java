package com.example.controller;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

import com.example.model.GameState;
import com.example.model.Hashmap;
import com.example.model.Pergunta;
import com.example.model.map;

public class Servidor {

    private static final String QUESTION_FILE_PATH = "src/main/resources/quizzes.json";
    private static final int DEFAULT_PORT = 12345;

    private final map<String, GameState> jogosAtivos;
    private final map<String, String> jogadoresGlobais;
    private final AtomicInteger codigoJogoCounter = new AtomicInteger(1000); 
    private volatile boolean running = true;
    private ServerSocket serverSocket; 

    public Servidor() {
        this.jogosAtivos = new Hashmap<>();
        this.jogadoresGlobais = new Hashmap<>();
    }
    
    public static void main(String[] args) {
        Servidor servidor = new Servidor();
        servidor.iniciarServidor(DEFAULT_PORT);
    }

    public void iniciarServidor(int porta) {
        new Thread(this::tuiLoop, "TUI-Thread").start();
        
        try {
            this.serverSocket = new ServerSocket(porta);
            System.out.println("Servidor IsKahoot a correr na porta " + porta);
            
            while (running) { 
                Socket clientSocket = serverSocket.accept();
                new DealWithClient(clientSocket, this).start();
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Erro no Servidor: " + e.getMessage());
            }
        }
    }
    
    private void tuiLoop() {
        try (Scanner scanner = new Scanner(System.in)) {
			System.out.println("TUI: Comandos disponíveis:");
			System.out.println("  new <nEquipas> <nJogadoresPorEquipa> <nPerguntas>");
			System.out.println("  list");
			System.out.println("  exit");
			
			while (running && scanner.hasNextLine()) {
			    System.out.print("> ");
			    String comando = scanner.nextLine().trim();
			    if (comando.toLowerCase().startsWith("new")) {
			        processarComandoNew(comando);
			    } else if (comando.equalsIgnoreCase("list")) {
			        listarJogosAtivos();
			    } else if (comando.equalsIgnoreCase("exit")) {
			        encerrarServidor();
			        break;
			    }
			}
		}
    }
    
    public void encerrarServidor() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException e) {}
        System.out.println("Servidor encerrado.");
    }
    
    private void processarComandoNew(String comando) {
        String[] partes = comando.split("\\s+");
        if (partes.length != 4) {
            System.err.println("Uso: new <nEquipas> <nJogadores> <nPerguntas>");
            return;
        }
        try {
            int numEquipas = Integer.parseInt(partes[1]);
            int jogadoresPorEquipa = Integer.parseInt(partes[2]);
            int numPerguntas = Integer.parseInt(partes[3]);
            
            String gameCode = String.valueOf(codigoJogoCounter.getAndIncrement());
            
            List<Pergunta> perguntas = Pergunta.readAllFromFile(QUESTION_FILE_PATH); 
            if (perguntas == null) {
                System.err.println("Erro ao ler perguntas. Verifique o ficheiro json.");
                return;
            }
            
            Collections.shuffle(perguntas);
            if (numPerguntas < perguntas.size()) {
                perguntas = perguntas.subList(0, numPerguntas);
            }

            GameState novoJogo = new GameState(gameCode, perguntas, numEquipas, jogadoresPorEquipa);
            jogosAtivos.put(gameCode, novoJogo);
            
            System.out.println("Jogo criado! Código: " + gameCode);
        } catch (Exception e) {
            System.err.println("Erro ao criar jogo: " + e.getMessage());
        }
    }

    private void listarJogosAtivos() {
        System.out.println("Jogos Ativos: " + jogosAtivos.entrySet().size());
    }
    
    public synchronized GameState registarJogador(String gameCode, String teamName, String username, DealWithClient clientThread) {
        if (jogadoresGlobais.get(username) != null) {
            System.err.println("Registo falhou: Username '" + username + "' já existe.");
            return null;
        }

        GameState jogo = jogosAtivos.get(gameCode);
        if (jogo == null) {
            System.err.println("Registo falhou: Jogo " + gameCode + " não existe.");
            return null;
        }

        synchronized (jogo) {
            int totalExpected = jogo.getTotalTeams() * jogo.getPlayersPerTeam();
            if (jogo.getClientThreadsCount() >= totalExpected) {
                System.err.println("Registo falhou: Jogo cheio.");
                return null;
            }

            if (jogo.isTeamFull(teamName)) {
                System.err.println("Registo falhou: A equipa '" + teamName + "' já está cheia (Máx: " + jogo.getPlayersPerTeam() + ").");
                return null; 
            }

            jogo.registerClient(clientThread);
            jogo.assignPlayerToTeam(username, teamName);
            jogadoresGlobais.put(username, gameCode);
            
            System.out.println("Jogador " + username + " entrou no Jogo " + gameCode);

            if (jogo.getClientThreadsCount() == totalExpected) {
                System.out.println("Sala cheia! A iniciar jogo " + gameCode + "...");
                Thread gameThread = new Thread(() -> iniciarCicloJogo(jogo));
                gameThread.start();
            }
            return jogo;
        }
    }

    public void iniciarCicloJogo(GameState jogo) {
        System.out.println(">>> INICIO DO JOGO " + jogo.getGameCode() + " <<<");
        
        while (jogo.hasMoreQuestions()) {
            jogo.prepareNextRound(); 
            
            Pergunta p = jogo.getCurrentQuestion();
            int atual = jogo.getCurrentQuestionIndex() + 1;
            int total = jogo.getTotalQuestions();
            String tipo = jogo.getCurrentType().toString();

            System.out.println("Pergunta " + tipo + ": " + p.getQuestion());

            String msg = "QUESTION " + atual + " " + total + " " + 
                        p.getQuestion().replace(" ", "_") + " " + 
                        String.join(";", p.getOptions());
            jogo.broadcast(msg);
            jogo.broadcast("TIMER 30");

            System.out.println("... Aguardando respostas ...");
            jogo.waitForAllResponses(); 

            String leaderboard = jogo.getLeaderboard();
            jogo.broadcast("RESULT Ronda_Terminada");
            jogo.broadcast("LEADERBOARD " + leaderboard.replace(";  ", ";"));
            System.out.println("PLACAR: " + leaderboard);

            try { 
                Thread.sleep(5000); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            jogo.nextQuestion();
        }
        jogo.broadcast("END_GAME");
        System.out.println("Jogo " + jogo.getGameCode() + " encerrado.");

        jogosAtivos.remove(jogo.getGameCode());
        
        
        String gameCode = jogo.getGameCode();
        java.util.Set<String> jogadoresParaRemover = new java.util.HashSet<>();
        
        for (java.util.Map.Entry<String, String> entry : jogadoresGlobais.entrySet()) {
            if (entry.getValue().equals(gameCode)) {
                jogadoresParaRemover.add(entry.getKey());
            }
        }
        
        for (String username : jogadoresParaRemover) {
            jogadoresGlobais.remove(username);
        }
        
        System.out.println("DEBUG: Jogo " + gameCode + " e seus jogadores removidos dos registos.");
    }
    public void removeJogador(String username) {
        if (username == null) return;
        String gameCode = jogadoresGlobais.remove(username);
        
        if (gameCode != null) {
            System.out.println("DEBUG: Jogador " + username + " desconectou. Removido do Jogo " + gameCode);
            
            
        }
    }
    
    

}