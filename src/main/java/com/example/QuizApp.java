package com.example;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class QuizApp {

    // --- Lógica de Rede ---
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread clientListenerThread; 
    
    // --- Dados do Jogo ---
    private String currentPlayer;
    private String currentGameCode;
    
    // --- Interface Gráfica ---
    private JFrame frame;
    private JButton enterBtn; // Variável global para reativar em caso de erro
    
    private JLabel timerLabel;
    private JLabel roundLabel;
    private JLabel statusLabel;
    private JButton[] optionButtons; 
    
    // --- Cores e Constantes ---
    private final int answerTime = 30; 
    private Thread timerThread; 
    private volatile boolean isQuestionActive = false;
    private int currentRemainingTime;
    private volatile boolean hasResponded = false;
    
    // Flag de controlo de erro no registo
    private volatile boolean registrationFailed = false; 
    
    private final Color roxo = new Color (91, 72, 181);
    private final Color cinza = new Color(237, 237, 237);
    private static final int LARGURA = 600;
    private static final int ALTURA = 500;
    
    // Timer para o ecrã de leaderboard
    private Timer leaderboardTimer;


    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        SwingUtilities.invokeLater(() -> new QuizApp().showHomePage());
    }

    private void showHomePage() {
        stopAnyRunningTimer();
        
        if (frame != null) {
            frame.getContentPane().removeAll();
        } else {
            frame = new JFrame("IsKahoot");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(LARGURA, ALTURA);
            Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (dimension.width - LARGURA) / 2;
            int y = (dimension.height - ALTURA) / 2;
            frame.setLocation(x, y);
        }
        
        frame.setLayout(new GridLayout(3, 1)); 
        frame.setVisible(true);

        JLabel titulo = new JLabel("IsKahoot!", JLabel.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 64));
        titulo.setForeground(Color.WHITE);
        frame.getContentPane().setBackground(roxo);
        frame.add(titulo);

        JPanel inputPanel = new JPanel(new GridLayout(4,1,10,10)); 
        inputPanel.setBackground(roxo);
        inputPanel.setBorder(BorderFactory.createEmptyBorder(0,100,0,100));

        JTextField gameField = criarCampoComPlaceholder("Game PIN");
        JTextField teamField = criarCampoComPlaceholder("Team PIN");
        JTextField playerField = criarCampoComPlaceholder("Player ID");

        enterBtn = new JButton("Enter");
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
            
            enterBtn.setEnabled(false); // Desativar para evitar duplo clique
            
            String host = "localhost";
            int port = 12345;
            
            new Thread(() -> connectAndRegister(host, port, game, team, player)).start();
        });

        inputPanel.add(gameField);
        inputPanel.add(teamField);
        inputPanel.add(playerField);
        inputPanel.add(enterBtn);
        
        frame.add(inputPanel);
        frame.add(new JLabel("")); // Espaço extra
        
        frame.revalidate();
        frame.repaint();
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
                JOptionPane.showMessageDialog(frame, "Erro ao conectar: " + e.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                enterBtn.setEnabled(true);
            });
            closeConnection();
        }
    }

    private void listenToServer() {
        String serverMessage;
        try {
            registrationFailed = false; // Reset da flag
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
                    JOptionPane.showMessageDialog(frame, "Sessão encerrada.", "Fim", JOptionPane.INFORMATION_MESSAGE);
                    showHomePage(); 
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
                    showLobbyScreen();
                    break;
                case "ERROR":
                    registrationFailed = true; 
                    JOptionPane.showMessageDialog(frame, payload, "Erro", JOptionPane.ERROR_MESSAGE);
                    enterBtn.setEnabled(true);
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
                        
                        showQuestion(p, roundNow, roundTotal);
                    } catch (Exception e) {
                        System.err.println("Erro parsing: " + e.getMessage());
                    }
                    break;

                case "TIMER":
                    try {
                        currentRemainingTime = Integer.parseInt(payload);
                        if (timerLabel != null) timerLabel.setText(String.valueOf(currentRemainingTime));
                    } catch (NumberFormatException ignored) {}
                    break;

                case "RESULT":
                    if (statusLabel != null) statusLabel.setText(payload);
                    break;
                    
                case "LEADERBOARD":
                    showLeaderboardScreen(payload, false);
                    break;
                    
                case "END_GAME":
                    closeConnection();
                    registrationFailed = true; 
                    showLeaderboardScreen(payload.isEmpty() ? "Fim do Jogo" : payload, true);
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
    
    // --- ECRÃ DE PERGUNTA (Layout Original Preservado) ---
    private void showQuestion(Pergunta pergunta, String roundNow, String roundTotal) {
        stopAnyRunningTimer();
        
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(cinza); // Fundo cinza

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
                "<html><body style='text-align:center;width:400px;'>" + pergunta.getQuestion() + "</body></html>",
                JLabel.CENTER
        );
        questionLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        topPanel.add(questionLabel, BorderLayout.CENTER);

        frame.add(topPanel, BorderLayout.NORTH);

        JPanel optionsPanel = new JPanel(new GridLayout(2, 2, 10, 10));
        optionsPanel.setBackground(cinza);
        frame.add(optionsPanel, BorderLayout.CENTER);

        statusLabel = new JLabel("", JLabel.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        frame.add(statusLabel, BorderLayout.SOUTH);

        String[] opcoes = pergunta.getOptions(); 
        Cores[] coresEnum = Cores.values();
        optionButtons = new JButton[opcoes.length]; 

        for (int i = 0; i < opcoes.length; i++) {
            String textoBotao = "<html><body style='text-align:center;width:200px;'>" + opcoes[i] + "</body></html>";
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
                
                new Thread(() -> out.println("RESPONSE " + currentGameCode + " " + currentPlayer + " " + index)).start();

                SwingUtilities.invokeLater(() -> {
                    setOptionsEnabled(false); 
                    if (statusLabel != null) statusLabel.setText("Resposta enviada...");
                });
            });
            optionsPanel.add(botao);
        }

        if (timerThread != null && timerThread.isAlive()) timerThread.interrupt();
        timerThread = new Thread(() -> {
            while (isQuestionActive && currentRemainingTime > 0) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
                currentRemainingTime--;
                SwingUtilities.invokeLater(() -> {
                    if(timerLabel!=null) timerLabel.setText(String.valueOf(currentRemainingTime));
                });
            }
            if (isQuestionActive && !hasResponded) {
                SwingUtilities.invokeLater(() -> {
                    if (statusLabel != null) statusLabel.setText("Tempo esgotado!");
                    setOptionsEnabled(false);
                });
                new Thread(() -> out.println("RESPONSE " + currentGameCode + " " + currentPlayer + " -1")).start();
            }
        });
        timerThread.start();

        frame.revalidate();
        frame.repaint();
    }

    private void showLobbyScreen() {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(roxo); // Mantém o tema

        JLabel loadingLabel = new JLabel("<html><div style='text-align: center;'>Registado com sucesso!<br><br>A aguardar pelos outros jogadores...</div></html>", JLabel.CENTER);
        loadingLabel.setFont(new Font("Arial", Font.BOLD, 28));
        loadingLabel.setForeground(Color.WHITE);

        frame.add(loadingLabel, BorderLayout.CENTER);
        
        frame.revalidate();
        frame.repaint();
    }
    
    // --- NOVO ECRÃ DE LEADERBOARD ---
    private void showLeaderboardScreen(String data, boolean isFinal) {
        stopAnyRunningTimer();
        
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(isFinal ? new Color(40,40,40) : roxo); 

        String titleText = isFinal ? "RESULTADO FINAL" : "Classificação";
        JLabel title = new JLabel(titleText, JLabel.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 48));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        frame.add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        JPanel listPanel = new JPanel(new GridLayout(0, 1, 10, 10)); 
        listPanel.setOpaque(false);
        
        String[] equipas = data.split(";");
        for (String eq : equipas) {
            if (eq.trim().isEmpty()) continue;
            JLabel lbl = new JLabel(eq.trim(), JLabel.CENTER);
            lbl.setFont(new Font("SansSerif", Font.BOLD, 20));
            lbl.setForeground(Color.WHITE);
            lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.WHITE, 2),
                BorderFactory.createEmptyBorder(10, 40, 10, 40)
            ));
            listPanel.add(lbl);
        }
        centerPanel.add(listPanel);
        
        JScrollPane scroll = new JScrollPane(centerPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        frame.add(scroll, BorderLayout.CENTER);

        JLabel footerLabel = new JLabel("", JLabel.CENTER);
        footerLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        footerLabel.setForeground(isFinal ? Color.YELLOW : Color.LIGHT_GRAY);
        footerLabel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));
        frame.add(footerLabel, BorderLayout.SOUTH);

        if (!isFinal) {
            // Timer visual de 10s
            AtomicInteger countdown = new AtomicInteger(5);
            footerLabel.setText("Próxima ronda em " + countdown.get() + "...");
            
            leaderboardTimer = new Timer(1000, e -> {
                int rest = countdown.decrementAndGet();
                if (rest > 0) {
                    footerLabel.setText("Próxima ronda em " + rest + "...");
                } else {
                    footerLabel.setText("A carregar...");
                    ((Timer)e.getSource()).stop();
                }
            });
            leaderboardTimer.start();
        } else {
            footerLabel.setText("Obrigado por jogar!");
            JButton exitBtn = new JButton("Sair");
            exitBtn.addActionListener(e -> System.exit(0));
            frame.add(exitBtn, BorderLayout.SOUTH);
        }

        frame.revalidate();
        frame.repaint();
    }

    private void stopAnyRunningTimer() {
        if (leaderboardTimer != null && leaderboardTimer.isRunning()) {
            leaderboardTimer.stop();
        }
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

        campo.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if(campo.getText().equals(texto)){
                    campo.setText(""); campo.setForeground(Color.BLACK);
                }
            }
            @Override
            public void focusLost(FocusEvent e){
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
}

class SimpleDocumentListener implements DocumentListener {
    private final Runnable r;
    public SimpleDocumentListener(Runnable r) { this.r = r; }
    public void insertUpdate(DocumentEvent e) { r.run(); }
    public void removeUpdate(DocumentEvent e) { r.run(); }
    public void changedUpdate(DocumentEvent e) { r.run(); }
}