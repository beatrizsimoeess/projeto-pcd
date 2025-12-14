package com.example.controller;

import com.example.model.Pergunta; // Assumindo que Pergunta é acessível
import com.example.view.QuizView;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class QuizApp implements QuizControllerListener {

    private final QuizView view;

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread clientListenerThread; 
    
    private String currentPlayer;
    private String currentGameCode;
    
    private final int answerTime = 30; 
    private Thread timerThread; 
    private volatile boolean isQuestionActive = false;
    private int currentRemainingTime;
    private volatile boolean hasResponded = false;
    private volatile boolean registrationFailed = false; 


    public QuizApp() {
        this.view = new QuizView(this);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        SwingUtilities.invokeLater(() -> new QuizApp().view.showHomePage());
    }
    
   
    @Override
    public void onRegisterAttempt(String host, int port, String gameCode, String team, String player) {
        new Thread(() -> connectAndRegister(host, port, gameCode, team, player)).start();
    }

    @Override
    public void onAnswerSubmitted(int answerIndex) {
        if (!isQuestionActive || hasResponded) return;
        
        hasResponded = true;
        isQuestionActive = false; 
        
        SwingUtilities.invokeLater(() -> view.setOptionsEnabled(false));
        
        new Thread(() -> {
            int finalAnswer = isQuestionActive ? -1 : answerIndex; 
            out.println("RESPONSE " + currentGameCode + " " + currentPlayer + " " + finalAnswer);
        }).start();
    }
    
    @Override
    public void onExitGame() {
        closeConnection();
        System.exit(0);
    }

   
    private void connectAndRegister(String host, int port, String gameCode, String team, String player) {
        try {
            clientSocket = new Socket(host, port);
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            out.println("REGISTER " + gameCode + " " + team + " " + player);
            
            this.currentPlayer = player;
            this.currentGameCode = gameCode;
            
            clientListenerThread = new Thread(this::listenToServer);
            clientListenerThread.start();

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null, "Erro ao conectar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                view.setEnterButtonEnabled(true);
            });
            closeConnection();
        }
    }

    private void listenToServer() {
        String serverMessage;
        try {
            registrationFailed = false; 
            while ((serverMessage = in.readLine()) != null) {
                System.out.println("Servidor: " + serverMessage);
                processServerMessage(serverMessage);
            }
        } catch (IOException e) {
            if (clientSocket != null && !clientSocket.isClosed()) {
                System.err.println("Conexão perdida: " + e.getMessage());
            }
        } finally {
            closeConnection();
            if (!registrationFailed) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(null, "Sessão encerrada.", "Fim", JOptionPane.INFORMATION_MESSAGE);
                    view.showHomePage(); 
                });
            }
        }
    }

    private void processServerMessage(String message) {
        if (message == null || message.trim().isEmpty()) return;

        String[] parts = message.split(" ", 2);
        String command = parts[0].toUpperCase();
        String payload = parts.length > 1 ? parts[1] : "";

        SwingUtilities.invokeLater(() -> {
            switch (command) {
                case "SUCCESS":
                    view.showLobbyScreen();
                    break;
                case "ERROR":
                    registrationFailed = true; 
                    JOptionPane.showMessageDialog(null, payload, "Erro", JOptionPane.ERROR_MESSAGE);
                    view.setEnterButtonEnabled(true);
                    closeConnection();
                    break;
                case "QUESTION":
                    try {
                        String[] firstSplit = payload.split(" ", 3);
                        String roundNow = firstSplit[0];
                        String roundTotal = firstSplit[1];
                        String content = firstSplit[2];

                        String[] contentParts = content.split(" ", 2);
                        String questionText = contentParts[0].replace("_", " ");
                        String[] options = contentParts[1].split(";");

                        Pergunta p = new Pergunta();
                        p.setQuestion(questionText);
                        p.setOptions(options);
                        
                        view.showQuestion(p, roundNow, roundTotal, answerTime);
                        startLocalTimer(); 
                    } catch (Exception e) {
                        System.err.println("Erro parsing: " + e.getMessage());
                    }
                    break;

                case "TIMER":
                    try {
                        currentRemainingTime = Integer.parseInt(payload);
                        view.updateTimer(currentRemainingTime);
                    } catch (NumberFormatException ignored) {}
                    break;

                case "RESULT":
                    view.updateStatus(payload);
                    break;
                    
                case "LEADERBOARD":
                    view.showLeaderboardScreen(payload, false);
                    break;
                    
                case "END_GAME":
                    closeConnection(); 
                    registrationFailed = true; 
                    view.showLeaderboardScreen(payload.isEmpty() ? "Fim do Jogo" : payload, true);
                    break;
            }
        });
    }

    private void closeConnection() {
        if (timerThread != null) timerThread.interrupt();
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {}
    }
    
    private void startLocalTimer() {
        isQuestionActive = true;
        hasResponded = false;
        currentRemainingTime = answerTime; 

        if (timerThread != null && timerThread.isAlive()) timerThread.interrupt();
        
        timerThread = new Thread(() -> {
            while (isQuestionActive && currentRemainingTime > 0) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                currentRemainingTime--;
            }
            if (isQuestionActive && !hasResponded) {
                SwingUtilities.invokeLater(() -> {
                    view.updateStatus("Tempo esgotado!");
                    view.setOptionsEnabled(false);
                });
                
                onAnswerSubmitted(-1);
            }
        });
        timerThread.start();
    }
}