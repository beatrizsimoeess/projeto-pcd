package com.example;

import javax.swing.*;
import java.awt.*;
import java.util.List;


public class QuizApp {

    private JFrame frame;           
    private Gamestate gamestate;   
    private String currentPlayer;  
    private JLabel timerLabel;     
    private JLabel roundLabel;   
    private final String questionFilePath="src/main/resources/quizzes.json";
    private final int answerTime= 30;
    private Thread timerThread; 
    private volatile boolean isQuestionActive = false; 
    private int currentRemainingTime; 
    private boolean hasResponded = false;

    public static void main(String[] args) {
        try {
//cena para ver no mac
        	UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        
        SwingUtilities.invokeLater(() -> new QuizApp().showHomePage());
    }

  
    private void showHomePage() {
        frame = new JFrame("IsKahoot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new GridLayout(3, 1));
        frame.setLocationRelativeTo(null); 

        JLabel titulo = new JLabel("IsKahoot!", JLabel.CENTER);
        titulo.setFont(new Font("Arial", Font.BOLD, 64));
        titulo.setForeground(Color.WHITE);
        frame.getContentPane().setBackground(new Color(91, 72, 181));
        frame.add(titulo);

        JPanel inputPanel = new JPanel(new GridLayout(4,1,10,10));
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
            currentPlayer = player;

            List<Pergunta> perguntas = Pergunta.readAllFromFile(questionFilePath);
            if (perguntas == null || perguntas.isEmpty()) {
                JOptionPane.showMessageDialog(frame,"Erro: perguntas não encontradas.");
                return;
            }
//pode mudar os números 1,1 tratar quando for preciso
            gamestate = new Gamestate(game, perguntas, 1,1);
            gamestate.assignPlayerToTeam(player, team);

            showQuestion(gamestate.getCurrentQuestion());
        });

        inputPanel.add(gameField);
        inputPanel.add(teamField);
        inputPanel.add(playerField);
        inputPanel.add(enterBtn);
        frame.add(inputPanel);
        frame.setVisible(true);
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
        if(text.isEmpty() || text.equals("Game PIN") || text.equals("Team PIN") || text.equals("Player ID"))
            return false;
        try { 
            Integer.parseInt(text); 
            return true; 
        } catch(NumberFormatException e) { 
            return false; 
        }
    }

   
    private void showQuestion(Pergunta pergunta) {
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

        String roundText = "     "
                + (gamestate.getCurrentQuestionIndex() + 1)
                + "/"
                + gamestate.getQuestionts().size();

        roundLabel = new JLabel(roundText);
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

        String[] opcoes = pergunta.getOptions();
        Cores[] coresEnum = Cores.values();

        for (int i = 0; i < opcoes.length; i++) {
            String textoBotao = "<html><body style='text-align:center;width:200px;'>"
                    + opcoes[i]
                    + "</body></html>";

            JButton botao = new JButton(textoBotao);
            botao.setBackground(coresEnum[i % coresEnum.length].getColor());
            botao.setForeground(Color.WHITE);
            botao.setFont(new Font("SansSerif", Font.BOLD, 18));

            int index = i;
            botao.addActionListener(e -> {
                if (!isQuestionActive || hasResponded) return;
                hasResponded = true;
                isQuestionActive = false;

                gamestate.registerResponse(currentPlayer, index);

                if (index == pergunta.getCorrect()) {
                    gamestate.addPointsToPlayer(currentPlayer, pergunta.getPoints());
                    JOptionPane.showMessageDialog(frame,
                            "Correto! +" + pergunta.getPoints() + " pontos.");
                } else {
                    JOptionPane.showMessageDialog(frame,
                            "Errado! Resposta certa: " + opcoes[pergunta.getCorrect()]);
                }

                gamestate.nextQuestion();
                nextQuestionOrEnd();
            });

            optionsPanel.add(botao);
        }

        // timmer
        timerThread = new Thread(() -> {
            while (isQuestionActive && currentRemainingTime > 0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}

                currentRemainingTime--;

                SwingUtilities.invokeLater(() ->
                        timerLabel.setText(String.valueOf(currentRemainingTime))
                );
            }

            if (isQuestionActive && !hasResponded) {
                isQuestionActive = false;
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, "Tempo esgotado!");
                    gamestate.registerResponse(currentPlayer, null);
                    gamestate.nextQuestion();
                    nextQuestionOrEnd();
                });
            }
        });

        timerThread.start();

        frame.revalidate();
        frame.repaint();
    }


    
    private void nextQuestionOrEnd(){
        if(gamestate.hasMoreQuestions()){
            showQuestion(gamestate.getCurrentQuestion());
        } else {
            JOptionPane.showMessageDialog(frame,"Fim do quiz!");
            frame.dispose();
        }
    }
}
