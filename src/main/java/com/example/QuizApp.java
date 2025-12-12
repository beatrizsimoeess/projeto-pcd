package com.example;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Arrays;

public class QuizApp {

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread clientListenerThread; 
    
    private JFrame frame;
    private String currentPlayer;
    private String currentGameCode;
    private JLabel timerLabel;
    private JLabel roundLabel;
    
    private JLabel statusLabel;
    private JButton[] optionButtons; 
    
    private final int answerTime = 30; 
    private Thread timerThread; 
    private volatile boolean isQuestionActive = false;
    private int currentRemainingTime;
    private volatile boolean hasResponded = false;

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        SwingUtilities.invokeLater(() -> new QuizApp().showHomePage());
    }

    private void showHomePage() {
        frame = new JFrame("IsKahoot - Cliente");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new GridLayout(4, 1)); 
        frame.setLocationRelativeTo(null); 

        JLabel titulo = new JLabel("IsKahoot!", JLabel.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 64));
        titulo.setForeground(Color.WHITE);
        frame.getContentPane().setBackground(new Color(91, 72, 181));
        frame.add(titulo);

        JPanel inputPanel = new JPanel(new GridLayout(5,1,10,10)); 
        inputPanel.setBackground(new Color(91,72,181));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0,100,0,100));

        JTextField gameField = criarCampoComPlaceholder("Game PIN");
        JTextField teamField = criarCampoComPlaceholder("Team PIN");
        JTextField playerField = criarCampoComPlaceholder("Player ID");

        JButton enterBtn = new JButton("Enter");
        enterBtn.setFont(new Font("SansSerif", Font.BOLD, 18));
        enterBtn.setBackground(Color.BLACK);
        enterBtn.setForeground(Color.WHITE);
        enterBtn.setEnabled(false); 

        Runnable verificarCampos = () -> {
            boolean habilitar = isIntField(gameField) && isIntField(teamField) && isIntField(playerField);
            enterBtn.setEnabled(habilitar);
        };

        gameField.getDocument().addDocumentListener(new SimpleDocumentListener(verificarCampos));
        teamField.getDocument().addDocumentListener(new SimpleDocumentListener(verificarCampos));
        playerField.getDocument().addDocumentListener(new SimpleDocumentListener(verificarCampos));

        enterBtn.addActionListener(e -> {
            String game = gameField.getText().trim();
            String team = teamField.getText().trim();
            String player = playerField.getText().trim();
            
            
            String host = "localhost";
            int port = 12345;
            
            new Thread(() -> connectAndRegister( host, port, game, team, player)).start();
        });

        inputPanel.add(gameField);
        inputPanel.add(teamField);
        inputPanel.add(playerField);
        inputPanel.add(enterBtn);
        frame.add(inputPanel);
        frame.setVisible(true);
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
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, 
                "Erro ao conectar ao Servidor: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE));
            closeConnection();
        }
    }

    private void listenToServer() {
        String serverMessage;
        try {
            while ((serverMessage = in.readLine()) != null) {
                System.out.println("Servidor: " + serverMessage);
                processServerMessage(serverMessage);
            }
        } catch (IOException e) {
            if (clientSocket != null && !clientSocket.isClosed()) {
                System.err.println("Conexão perdida com o Servidor: " + e.getMessage());
            }
        } finally {
            closeConnection();
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame, "Sessão encerrada ou perdida.", "Fim", JOptionPane.INFORMATION_MESSAGE);
                showHomePage(); 
            });
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
                    JOptionPane.showMessageDialog(frame, "Registo com sucesso! Aguarda o início.");
                    break;
                case "ERROR":
                    JOptionPane.showMessageDialog(frame, payload, "Erro", JOptionPane.ERROR_MESSAGE);
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
                    hasResponded = false; 
                    showQuestion(p, roundNow, roundTotal);
                    
                    } catch (Exception e) {
                        System.err.println("Erro ao mostrar pergunta: " + e.getMessage());
                    }
                    break;

                case "TIMER":
                    try {
                        currentRemainingTime = Integer.parseInt(payload);
                        if (timerLabel != null) timerLabel.setText(String.valueOf(currentRemainingTime));
                    } catch (NumberFormatException ignored) {}
                    break;

                case "RESULT":
                    if (payload.startsWith("CORRETO")) {
                        if (statusLabel != null) statusLabel.setText("CORRETO! " + payload);
                    } else if (payload.startsWith("ERRADO")) {
                        if (statusLabel != null) statusLabel.setText("ERRADO! " + payload); 
                    }
                    break;
                    
                case "LEADERBOARD":
                    JOptionPane.showMessageDialog(frame, "Fim da Ronda!\n\n" + payload.replace(";", "\n"));
                    
                    hasResponded = false;
                    if (statusLabel != null) statusLabel.setText("");
                    
                    break;
                case "END_GAME":
                    JOptionPane.showMessageDialog(frame, "Jogo Terminado!");
                    closeConnection();
                    if (statusLabel != null) statusLabel.setText("");
                    break;
            }
        });
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (clientSocket != null) clientSocket.close();
        } catch (IOException ignored) {}
    }
    
    private void showQuestion(Pergunta pergunta, String roundNow, String roundTotal) {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(new Color(237,237,237));

        isQuestionActive = true;
        hasResponded = false;
        currentRemainingTime = answerTime; 

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        timerLabel = new JLabel(String.valueOf(answerTime), JLabel.CENTER);
        timerLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        timerLabel.setForeground(Color.BLACK);
        timerLabel.setPreferredSize(new Dimension(50,50));

        JPanel timerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        timerPanel.setOpaque(false);
        timerPanel.add(timerLabel);
        topPanel.add(timerPanel, BorderLayout.EAST);

        roundLabel = new JLabel("Rodada " + roundNow + " / " + roundTotal);
        roundLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        roundLabel.setForeground(Color.BLACK);
        topPanel.add(roundLabel, BorderLayout.WEST);

        JLabel questionLabel = new JLabel(
                "<html><body style='text-align:center;width:400px;'>"
                        + pergunta.getQuestion()
                        + "</body></html>",
                JLabel.CENTER
        );
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        topPanel.add(questionLabel, BorderLayout.CENTER);

        frame.add(topPanel, BorderLayout.NORTH);

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        optionsPanel.setBackground(new Color(237,237,237));
        frame.add(optionsPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("", JLabel.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        frame.add(statusLabel, BorderLayout.SOUTH);

        String[] opcoes = pergunta.getOptions(); 
        Cores[] coresEnum = Cores.values();
        
        optionButtons = new JButton[opcoes.length]; 

        for (int i = 0; i < opcoes.length; i++) {
            String textoBotao = "<html><body style='text-align:center;width:200px;'>"
                    + opcoes[i]
                    + "</body></html>";

            JButton botao = new JButton(textoBotao);
            botao.setBackground(coresEnum[i % coresEnum.length].getColor());
            botao.setForeground(Color.WHITE);
            botao.setFont(new Font("SansSerif", Font.BOLD, 18));
            
            optionButtons[i] = botao;

            int index = i;
            botao.addActionListener(e -> {
                if (!isQuestionActive || hasResponded) return;
                
                hasResponded = true;
                isQuestionActive = false;
                
                new Thread(() -> {
                    out.println("RESPONSE " + currentGameCode + " " + currentPlayer + " " + index);
                }).start();

                SwingUtilities.invokeLater(() -> {
                    setOptionsEnabled(false); 
                    if (statusLabel != null) {
                        statusLabel.setText("Resposta enviada. Aguardando resultado do Servidor...");
                    }
                });
            });

            optionsPanel.add(botao);
        }

        if (timerThread != null && timerThread.isAlive()) {
            timerThread.interrupt(); 
        }
        timerThread = new Thread(() -> {
            while (isQuestionActive && currentRemainingTime > 0) {
                try {
                    Thread.sleep(1000); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                currentRemainingTime--;

                SwingUtilities.invokeLater(() ->
                        timerLabel.setText(String.valueOf(currentRemainingTime))
                );
            }

            if (isQuestionActive && !hasResponded) {
                isQuestionActive = false;
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Tempo esgotado! Resposta não enviada.");
                    setOptionsEnabled(false);
                    new Thread(() -> out.println("RESPONSE " + currentGameCode + " " + currentPlayer + " -1")).start();
                });
            }
        });

        timerThread.start();

        frame.revalidate();
        frame.repaint();
    }
    
    private void setOptionsEnabled(boolean enabled) {
        if (optionButtons != null) {
            for (JButton button : optionButtons) {
                button.setEnabled(enabled);
            }
        }
    }
    

    private JTextField criarCampoComPlaceholder(String texto) {
        JTextField campo = new JTextField(texto, JTextField.CENTER);
        campo.setFont(new Font("SansSerif", Font.PLAIN, 18));
        campo.setForeground(Color.GRAY);

        campo.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if(campo.getText().equals(texto)){
                    campo.setText(""); campo.setForeground(Color.BLACK);
                }
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e){
                if(campo.getText().isEmpty()){
                    campo.setText(texto); campo.setForeground(Color.GRAY);
                }
            }
        });
        return campo;
    }

    private boolean isIntField(JTextField field) {
        String text = field.getText().trim();
        if(text.isEmpty() || text.matches(".*PIN") || text.matches(".*ID")) return false;
        try { Integer.parseInt(text); return true; } 
        catch(NumberFormatException e) { return false; }
    }
    
    private boolean isHostPort(JTextField field) {
        String text = field.getText().trim();
        if(text.isEmpty() || text.contains("Host:Port")) return false;
        
        String[] parts = text.split(":");
        if (parts.length != 2) return false;
        try { Integer.parseInt(parts[1]); return true; } 
        catch(NumberFormatException e) { return false; }
    }


}