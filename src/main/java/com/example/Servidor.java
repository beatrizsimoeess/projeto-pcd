package com.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

public class Servidor {

    private static final String QUESTION_FILE_PATH = "src/main/resources/quizzes.json";
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
        if (args.length != 1) {
             System.err.println("Uso: java Servidor <PORTA>");
             return;
        }
        int porta;
        try {
            porta = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Erro: A porta deve ser um número válido.");
            return;
        }

        Servidor servidor = new Servidor();
        servidor.iniciarServidor(porta);
    }
  
    public void iniciarServidor(int porta) {
        new Thread(this::tuiLoop, "TUI-Thread").start();
        
        try {
            this.serverSocket = new ServerSocket(porta);
            System.out.println("Servidor IsKahoot a correr na porta " + porta);
            
            while (running) { 
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado: " + clientSocket.getInetAddress());
                
                new DealWithClient(clientSocket, this).start();
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Erro ao iniciar/executar o Servidor: " + e.getMessage());
            } else {
                System.out.println("Servidor encerrado com sucesso.");
            }
        } finally {
            System.exit(0);
        }
    }
    
    private void tuiLoop() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("TUI: Insira comandos (ex: new <equipas> <jogadores> <perguntas> ou list)");
        
        while (true) {
            System.out.print("> ");
            String comando = scanner.nextLine().trim();
            if (comando.toLowerCase().startsWith("new")) {
                processarComandoNew(comando);
            } else if (comando.toLowerCase().equals("list")) {
                listarJogosAtivos();
            } else if (comando.toLowerCase().equals("exit")) {
                encerrarServidor();
                break;
            } else {
                System.out.println("Comando desconhecido.");
            }
        }
        scanner.close();
    }
    
    public void encerrarServidor() {
        this.running = false;
        System.out.println("Sinal de encerramento recebido. A fechar ServerSocket e recursos...");
        try {
            if (serverSocket != null) {
                serverSocket.close(); 
            }
            for (Map.Entry<String, GameState> entry : jogosAtivos.entrySet()) {
                entry.getValue().stopAllClients(); 
            }
        } catch (IOException e) {
            System.err.println("Erro ao fechar o ServerSocket: " + e.getMessage());
        }
    }
    
    private void processarComandoNew(String comando) {
        String[] partes = comando.split("\\s+");
        if (partes.length != 4) {
            System.err.println("Uso: new <num equipas> <num jogadores por equipa> <num perguntas>");
            return;
        }
        try {
            int numEquipas = Integer.parseInt(partes[1]);
            int jogadoresPorEquipa = Integer.parseInt(partes[2]);
            int numPerguntas = Integer.parseInt(partes[3]);
            
            String gameCode = String.valueOf(codigoJogoCounter.getAndIncrement());
            
            List<Pergunta> perguntas = Pergunta.readAllFromFile(QUESTION_FILE_PATH); 
            
            if (perguntas == null || perguntas.isEmpty()) {
                System.err.println("Erro: Não foi possível carregar as perguntas. (Verifique 'Pergunta.java')");
                return;
            }
            if (numPerguntas > perguntas.size()) { numPerguntas = perguntas.size(); }
            Collections.shuffle(perguntas);
            perguntas = perguntas.subList(0, numPerguntas);

            GameState novoJogo = new GameState(gameCode, perguntas, numEquipas, jogadoresPorEquipa);
            jogosAtivos.put(gameCode, novoJogo);
            
            System.out.println(" Novo jogo criado! Código: " + gameCode + " .");
        } catch (NumberFormatException e) {
            System.err.println("Argumentos inválidos. Certifique-se de que são números inteiros.");
        }
    }

    private void listarJogosAtivos() {
        if (jogosAtivos.isEmpty()) {
            System.out.println("Nenhum jogo ativo.");
            return;
        }
        System.out.println("--- Jogos Ativos ---");
        
        for (Map.Entry<String, GameState> entry : jogosAtivos.entrySet()) {
            String code = entry.getKey();
            GameState game = entry.getValue();
            
            int expected = game.getTotalTeams() * game.getPlayersPerTeam();
            int currentPlayers = game.getClientThreadsCount(); 
            
            System.out.println("Código: " + code + 
                               ", Jogadores: " + currentPlayers + "/" + expected +
                               ", Pergunta Atual: " + (game.getCurrentQuestionIndex() + 1) + "/" + game.getTotalQuestions());
        }
        System.out.println("--------------------");
    }
    
    public GameState registarJogador(String gameCode, String teamName, String username, DealWithClient clientThread) {
        if (jogadoresGlobais.get(username) != null) {
            System.err.println("Falha de registo: Username '" + username + "' já em uso.");
            return null;
        }

        GameState jogo = jogosAtivos.get(gameCode);

        if (jogo == null) {
            System.err.println("Falha de registo: Jogo " + gameCode + " não existe.");
            return null;
        }

        synchronized (jogo) {
            int totalExpectedPlayers = jogo.getTotalTeams() * jogo.getPlayersPerTeam();
            
            if (jogo.getClientThreadsCount() >= totalExpectedPlayers) {
                System.err.println("Falha de registo: Jogo " + gameCode + " já está cheio.");
                return null;
            }

            jogo.registerClient(clientThread);
            jogo.assignPlayerToTeam(username, teamName);
            jogadoresGlobais.put(username, gameCode);
            
            System.out.println("Jogador " + username + " registado no Jogo " + gameCode);
            
            if (jogo.getClientThreadsCount() == totalExpectedPlayers) {
                System.out.println("Todos os jogadores ligados. O jogo vai começar!");
                new Thread(() -> iniciarCicloJogo(jogo), "Game-" + gameCode + "-Thread").start();
            }
            
            return jogo;
        }
    }

    public void iniciarCicloJogo(GameState jogo) {
        
        while (jogo.hasMoreQuestions()) {
             try {
                 System.out.println("Jogo " + jogo.getGameCode() + " | A aguardar respostas por 30 segundos...");
                 Thread.sleep(30000); 
             } catch (InterruptedException ignored) {
                 Thread.currentThread().interrupt();
                 break;
             }
             
             jogo.nextQuestion();
        }
        
        System.out.println("🏁 Jogo " + jogo.getGameCode() + " terminado! A fechar conexões.");
        jogo.stopAllClients(); 
        jogosAtivos.remove(jogo.getGameCode());
        
        for (Map.Entry<String, String> entry : jogadoresGlobais.entrySet()) {
             if (entry.getValue().equals(jogo.getGameCode())) {
                 jogadoresGlobais.remove(entry.getKey());
             }
        }
    }
    
     // Lógica de pontuação para perguntas individuais MOCK
    public int calcularBonusIndividual(GameState jogo, String username, int resposta) {
        return 0; 
    }
    
     //Lógica de pontuação para perguntas de equopa MOCK.
    public void calcularPontuacaoEquipa(GameState jogo) {
        // ...
    }
}