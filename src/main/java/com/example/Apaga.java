package com.example; 
import javax.swing.*; 
import java.awt.*; 
import java.util.List; 
import java.util.concurrent.CountDownLatch; 
import java.util.concurrent.atomic.AtomicInteger; 

public class Apaga {

    private static List<Pergunta> perguntas; 
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
        perguntas = Pergunta.readAllFromFile("perguntas.json");

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

        JLabel label = new JLabel(pergunta.getQuestao(), JLabel.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 18)); 
        frame.add(label, BorderLayout.NORTH);
        JPanel panel = new JPanel(new GridLayout(0, 1));

        // Loop correto com índice para verificar resposta correta
        for (int i = 0; i < pergunta.getOpcoes().length; i++) {
            String opcao = pergunta.getOpcoes()[i];
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
                    String respostaCerta = pergunta.getOpcoes()[pergunta.getCorrect()];
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
        //O int[] é um “truque” para permitir mutabilidade dentro de uma lambda, 
        //mas não é thread-safe. Para quizzes simples funciona,
        // mas não é seguro se houver múltiplas threads a aceder
        tempoRestante = new AtomicInteger(5); 

        // Cria o Timer que decrementa o tempo a cada segundo,
        // na nova sintese é variável de classe, não local, 
        //para que possas pará-lo quando o utilizador clicar numa opção antes de o tempo acabar.
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
                    JOptionPane.showMessageDialog(frame, "Tempo esgotado!");
                    indiceAtual++; 
                    mostrarPerguntaAtual();
                });
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}

