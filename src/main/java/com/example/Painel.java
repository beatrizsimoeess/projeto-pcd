package com.example;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import javax.swing.UIManager;
import javax.swing.SwingUtilities;

public class Painel {

	private static List<Pergunta> perguntas; 
    private static int indiceAtual = 0;   


	private JFrame frame;
	private static final int LARGURA = 600;
	private static final int ALTURA = 400;
    private final Color roxo = new Color (91, 72, 181);
    private final Color cinza = new Color(237,237,237);

	public Painel() {
		frame = new JFrame("IsKahoot");
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setSize(LARGURA, ALTURA);

		Dimension dimension = Toolkit.getDefaultToolkit().getScreenSize();
		int x = (dimension.width - LARGURA) / 2;
		int y = (dimension.height - ALTURA) / 2;
		frame.setLocation(x, y);
		frame.setVisible(true);


	}

	public void open() {
		frame.setVisible(true);
	}

	private void clearFrame() {
		frame.getContentPane().removeAll();
		frame.revalidate();
		frame.repaint();
	}

	public void uploadHomePage() {
		clearFrame();
		frame.getContentPane().setBackground(roxo);
		frame.setLayout(new GridLayout(3, 1));

		JLabel titulo = new JLabel("IsKahoot!", JLabel.CENTER);
		titulo.setForeground(Color.WHITE);
		titulo.setFont(new Font("Arial", Font.BOLD, 64));
		frame.add(titulo);

		JPanel caixa = new JPanel();
		caixa.setBackground(roxo);
		caixa.setLayout(new GridLayout(4, 1, 10, 10));
		caixa.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 100, 0, 100));
		frame.add(caixa);

		JTextField campoGame = criarCampoComPlaceholder("Game PIN");
		JTextField campoTeam = criarCampoComPlaceholder("Team PIN");
		JTextField campoPlayer = criarCampoComPlaceholder("Player ID");

		JButton enter = new JButton("Enter");
		enter.setBackground(Color.BLACK);
		enter.setForeground(Color.WHITE);
		enter.setFont(new Font("SansSerif", Font.BOLD, 18));

        enter.addActionListener(e -> {
			String game = campoGame.getText().trim();
			String team = campoTeam.getText().trim();
			String player = campoPlayer.getText().trim();

			boolean vazio = game.isEmpty() || team.isEmpty() || player.isEmpty();
			boolean placeholder = 
			game.equals("Game PIN") || 
			team.equals("Team PIN") || 
			player.equals("Player ID");

		if (vazio || placeholder) {
			javax.swing.JOptionPane.showMessageDialog(
				frame,
				"É necessário preencher todos os campos para proceder.",
				"Atenção",
				javax.swing.JOptionPane.WARNING_MESSAGE
			);
		} else {
			perguntas = Pergunta.readAllFromFile("src/main/resources/quizzes.json");

            if (perguntas == null || perguntas.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(frame, "Erro: ficheiro de perguntas vazio ou não encontrado.");
            return;
            }
System.out.println("Perguntas lidas: " + (perguntas != null ? perguntas.size() : "null"));

			uploadQuestion(perguntas.get(indiceAtual).getQuestion());
			}
		});

		caixa.add(campoGame);
		caixa.add(campoTeam);
		caixa.add(campoPlayer);
		caixa.add(enter);
		frame.add(caixa);

		frame.revalidate();
		frame.repaint();
	}

	private JTextField criarCampoComPlaceholder(String texto) {
	JTextField campo = new JTextField(texto, JTextField.CENTER);
	campo.setFont(new Font("SansSerif", Font.PLAIN, 18));
	campo.setForeground(Color.GRAY);
	campo.addFocusListener(new java.awt.event.FocusAdapter() {
		@Override
		public void focusGained(java.awt.event.FocusEvent e) {
			if (campo.getText().equals(texto) && campo.getForeground().equals(Color.GRAY)) {
				campo.setText("");
				campo.setForeground(Color.BLACK);
			}
		}

		@Override
		public void focusLost(java.awt.event.FocusEvent e) {
			if (campo.getText().isEmpty()) {
				campo.setText(texto);
				campo.setForeground(Color.GRAY);
			}
		}
	});
	return campo;
}


	public void uploadQuestion(String question) {
		clearFrame();
		frame.getContentPane().setBackground(cinza);
		frame.setLayout(new GridLayout(2, 1));
		
		JLabel pergunta = new JLabel( question, JLabel.CENTER);
		pergunta.setFont(new Font("SansSerif", Font.BOLD, 24));
		pergunta.setForeground(Color.BLACK);
		frame.add(pergunta);
		
		JLabel countdown = new JLabel("5", JLabel.CENTER);
		countdown.setFont(new Font("SansSerif", Font.BOLD, 24));
		countdown.setForeground(Color.DARK_GRAY);
		frame.add(countdown);

		frame.revalidate();
		frame.repaint();

		CountDownLatch latch = new CountDownLatch(1);

		AtomicInteger tempoRestante = new AtomicInteger(5);


		javax.swing.Timer timer = new javax.swing.Timer(1000, e -> {
		int restante = tempoRestante.decrementAndGet();
        countdown.setText(String.valueOf(restante));

        if (restante <= 0) {
        ((javax.swing.Timer) e.getSource()).stop();
        latch.countDown();
        }
		});
		timer.start();


	new Thread(() -> {
		try {
			latch.await();
			SwingUtilities.invokeLater(() -> uploadQuestionOptions(perguntas.get(indiceAtual)));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}).start();
	}
	
	public void uploadQuestionOptions(Pergunta pergunta) {
		clearFrame();
		frame.getContentPane().setBackground(cinza);
		frame.setLayout(new GridLayout(2, 1));
		
		JLabel question = new JLabel( pergunta.getQuestion(), JLabel.CENTER);
		question.setFont(new Font("SansSerif", Font.BOLD, 24));
		question.setForeground(Color.BLACK);
		frame.add(question);
		
		JPanel optionsPainel = new JPanel(new GridLayout (2,2));
		optionsPainel.setBackground(new Color(237, 237, 237));
		
		String[] opcoes = pergunta.getOptions();
		Cores[] cores = Cores.values();

		for (int i= 0; i < opcoes.length; i++) {
			JButton botao = new JButton(opcoes[i]);
			botao.setBackground(cores[i].getColor());
			botao.setForeground(Color.WHITE);
			botao.setFont(new Font("SansSerif", Font.BOLD, 16));

			int index = i;
			botao.addActionListener(e -> {
            if (index == pergunta.getCorrect()) {
                javax.swing.JOptionPane.showMessageDialog(frame, "Correto! +" + pergunta.getPoints() + " pontos.");
            } else {
                javax.swing.JOptionPane.showMessageDialog(frame, 
                    "Errado! Resposta certa: " + opcoes[pergunta.getCorrect()]);
            }

            indiceAtual++;
            if (indiceAtual < perguntas.size()) {
                uploadQuestion(perguntas.get(indiceAtual).getQuestion());
            } else {
                javax.swing.JOptionPane.showMessageDialog(frame, "Fim do quiz!");
                frame.dispose();
            }
        });
			optionsPainel.add(botao);
		}
		frame.add(optionsPainel);
		
		frame.revalidate();
		frame.repaint();
	}
	



	public static void main(String[] args) {
	    try {
	        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
	    } catch (Exception ignored) {}

	    SwingUtilities.invokeLater(() -> {
	        Painel p = new Painel();
	        p.uploadHomePage();
	    });
	}

}
