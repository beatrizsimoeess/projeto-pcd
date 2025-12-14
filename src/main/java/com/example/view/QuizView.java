package com.example.view;

import com.example.controller.QuizControllerListener;
import com.example.model.Pergunta; 

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.concurrent.atomic.AtomicInteger;

public class QuizView {

    private final QuizControllerListener controller;
    
    private JFrame frame;
    private JButton enterBtn; 
    private JLabel timerLabel;
    private JLabel roundLabel;
    private JLabel statusLabel;
    private JButton[] optionButtons; 
    private Timer leaderboardTimer;
    
    private final Color ROXO = new Color (91, 72, 181);
    private final Color CINZA = new Color(237, 237, 237);
    private static final int LARGURA = 600;
    private static final int ALTURA = 500;


    public QuizView(QuizControllerListener controller) {
        this.controller = controller;
        frame = new JFrame("IsKahoot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(LARGURA, ALTURA);
        Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
        int x = (dimension.width - LARGURA) / 2;
        int y = (dimension.height - ALTURA) / 2;
        frame.setLocation(x, y);
    }
  
    public void showHomePage() {
        stopAnyRunningTimer();
        
        frame.getContentPane().removeAll();
        frame.setLayout(new GridLayout(3, 1)); 
        frame.setVisible(true);

        JLabel titulo = new JLabel("IsKahoot!", JLabel.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 64));
        titulo.setForeground(Color.WHITE);
        frame.getContentPane().setBackground(ROXO);
        frame.add(titulo);

        JPanel inputPanel = new JPanel(new GridLayout(4,1,10,10)); 
        inputPanel.setBackground(ROXO);
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
            
            enterBtn.setEnabled(false); 
            
            String host = "localhost";
            int port = 12345;
            
            controller.onRegisterAttempt(host, port, game, team, player);
        });

        inputPanel.add(gameField);
        inputPanel.add(teamField);
        inputPanel.add(playerField);
        inputPanel.add(enterBtn);
        
        frame.add(inputPanel);
        frame.add(new JLabel("")); 
        
        frame.revalidate();
        frame.repaint();
    }
    
    public void showLobbyScreen() {
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(ROXO); 

        JLabel loadingLabel = new JLabel("<html><div style='text-align: center;'>Registado com sucesso!<br><br>A aguardar pelos outros jogadores...</div></html>", JLabel.CENTER);
        loadingLabel.setFont(new Font("Arial", Font.BOLD, 28));
        loadingLabel.setForeground(Color.WHITE);

        frame.add(loadingLabel, BorderLayout.CENTER);
        
        frame.revalidate();
        frame.repaint();
    }

    public void showQuestion(Pergunta pergunta, String roundNow, String roundTotal, int answerTime) {
        stopAnyRunningTimer();
        
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(CINZA); 

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
        optionsPanel.setBackground(CINZA);
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
                setOptionsEnabled(false); 
                if (statusLabel != null) statusLabel.setText("Resposta enviada...");
                
                controller.onAnswerSubmitted(index);
            });
            optionsPanel.add(botao);
        }

        frame.revalidate();
        frame.repaint();
    }
    
    public void showLeaderboardScreen(String data, boolean isFinal) {
        stopAnyRunningTimer();
        
        frame.getContentPane().removeAll();
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(isFinal ? new Color(40,40,40) : ROXO); 

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
            exitBtn.addActionListener(e -> controller.onExitGame());
            frame.add(exitBtn, BorderLayout.SOUTH);
        }

        frame.revalidate();
        frame.repaint();
    }
    
  
    public void updateTimer(int time) {
        if (timerLabel != null) {
            timerLabel.setText(String.valueOf(time));
        }
    }
    
    public void updateStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    public void setOptionsEnabled(boolean enabled) {
        if (optionButtons != null) {
            for (JButton button : optionButtons) {
                button.setEnabled(enabled);
            }
        }
    }
    
    public void setEnterButtonEnabled(boolean enabled) {
        if (enterBtn != null) {
            enterBtn.setEnabled(enabled);
        }
    }

    public void stopAnyRunningTimer() {
        if (leaderboardTimer != null && leaderboardTimer.isRunning()) {
            leaderboardTimer.stop();
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