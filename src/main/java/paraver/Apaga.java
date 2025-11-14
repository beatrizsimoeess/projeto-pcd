package com.example; 
import javax.swing.*; 
import java.awt.*; 
import java.util.List; 
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger; 

public class Apaga {

	Gamestate gamestate;
    private static List<Pergunta> perguntas; 
    Pergunta perguntaAtual = gamestate.getCurrentQuestion();
    private static int indiceAtual = 0;     

    private static JFrame frame;             
    private static JLabel countdownLabel;     
    private static javax.swing.Timer timer;  
    private static AtomicInteger tempoRestante; 
    private static CountDownLatch latch;       

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Apaga::iniciarQuiz); 
    }

    private static void iniciarQuiz() {
        perguntas = Pergunta.readAllFromFile("src/main/resources/quizzes.json");

        if (perguntas == null || perguntas.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Nenhuma pergunta encontrada");
            return;
        }

        frame = new JFrame("Quiz com Timer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
        frame.setSize(400, 250); 
        frame.setLayout(new BorderLayout()); 
        frame.setVisible(true); 

        mostrarPerguntaAtual();
    }

    private static void mostrarPerguntaAtual() {
        if (indiceAtual >= perguntas.size()) {
            JOptionPane.showMessageDialog(frame, "Fim do quiz");
            frame.dispose(); 
            return;
        }
        Pergunta pergunta = perguntas.get(indiceAtual);

        frame.getContentPane().removeAll();

        JLabel label = new JLabel(pergunta.getQuestion(), JLabel.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 18)); 
        frame.add(label, BorderLayout.NORTH);
        JPanel panel = new JPanel(new GridLayout(0, 1));

        // Loop correto com índice para verificar resposta correta
        for (int i = 0; i < pergunta.getOptions().length; i++) {
            String opcao = pergunta.getOptions()[i];
            int indiceOpcao = i; 
            JButton botao = new JButton(opcao);
            botao.addActionListener(e -> {
                // Para o timer se o utilizador responder antes de o tempo acabar
                if (timer.isRunning()) {
                    timer.stop();
                    latch.countDown();
                }
                // Verifica se a resposta escolhida é a correta
                if (indiceOpcao == pergunta.getCorrect()) {
                    JOptionPane.showMessageDialog(frame, "Correto +" + pergunta.getPoints() + " pontos");
                } else {
                    String respostaCerta = pergunta.getOptions()[pergunta.getCorrect()];
                    JOptionPane.showMessageDialog(frame, "Errado. A resposta correta era: " + respostaCerta);
                }
                indiceAtual++;
                mostrarPerguntaAtual();
            });

            panel.add(botao);
        }
        frame.add(panel, BorderLayout.CENTER);

        countdownLabel = new JLabel("5", JLabel.CENTER);
        countdownLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        countdownLabel.setForeground(Color.DARK_GRAY);
        frame.add(countdownLabel, BorderLayout.SOUTH); 

        frame.revalidate();
        frame.repaint();

        iniciarTimer();
    }
    private static void iniciarTimer() {
        latch = new CountDownLatch(1);
        tempoRestante = new AtomicInteger(5);
        AtomicBoolean respondeu = new AtomicBoolean(false);

        timer = new javax.swing.Timer(1000, e -> {
            int restante = tempoRestante.decrementAndGet();
            countdownLabel.setText(String.valueOf(restante));

            if (restante <= 0) {
                ((javax.swing.Timer) e.getSource()).stop();
                latch.countDown();
            }
        });
        timer.start();

        new Thread(() -> {
            try {
                latch.await();
                SwingUtilities.invokeLater(() -> {
                    if (!respondeu.get()) {
                        JOptionPane.showMessageDialog(frame, "Tempo esgotado!");
                        indiceAtual++;
                        mostrarPerguntaAtual();
                    }
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }).start();

        for (Component comp : ((JPanel) frame.getContentPane()
                .getComponent(1)).getComponents()) {
            if (comp instanceof JButton) {
                ((JButton) comp).addActionListener(e -> respondeu.set(true));
            }
        }
    }
}


