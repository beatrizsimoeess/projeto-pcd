package com.example;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

public class Painel {
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

		// caixa para os inputs
		JPanel caixa = new JPanel();
		caixa.setBackground(roxo);
		caixa.setLayout(new GridLayout(4, 1, 10, 10));
		caixa.setBounds(LARGURA / 2 - 150, 250, 300, 180);

		// campos de texto
		JTextField campoGame = new JTextField();
		campoGame.setHorizontalAlignment(JTextField.CENTER);
		campoGame.setFont(new Font("SansSerif", Font.PLAIN, 18));
		campoGame.setToolTipText("Game PIN");
		campoGame.setText("Game PIN");

		JTextField campoTeam = new JTextField();
		campoTeam.setHorizontalAlignment(JTextField.CENTER);
		campoTeam.setFont(new Font("SansSerif", Font.PLAIN, 18));
		campoTeam.setToolTipText("Team PIN");
		campoTeam.setText("Team PIN");

		JTextField campoPlayer = new JTextField();
		campoPlayer.setHorizontalAlignment(JTextField.CENTER);
		campoPlayer.setFont(new Font("SansSerif", Font.PLAIN, 18));
		campoPlayer.setToolTipText("Player ID");
		campoPlayer.setText("Player ID");

		JButton enter = new JButton("Enter");
		enter.setBackground(Color.BLACK);
		enter.setForeground(Color.WHITE);
		enter.setFont(new Font("SansSerif", Font.BOLD, 18));

		// adiciona

		caixa.add(campoGame);
		caixa.add(campoTeam);
		caixa.add(campoPlayer);
		caixa.add(enter);
		frame.add(caixa);

		frame.revalidate();
		frame.repaint();
	}

	public void uploadQuestion(String question) {
		clearFrame();
		frame.getContentPane().setBackground(cinza);
		frame.setLayout(new GridLayout(1, 1));
		
		JLabel pergunta = new JLabel( question, JLabel.CENTER);
		pergunta.setFont(new Font("SansSerif", Font.BOLD, 28));
		pergunta.setForeground(Color.BLACK);
		frame.add(pergunta);
		
		frame.revalidate();
		frame.repaint();
	}
	
	public void uploadQuestionOptions(String question) {
		clearFrame();
		frame.getContentPane().setBackground(cinza);
		frame.setLayout(new GridLayout(2, 1));
		
		JLabel pergunta = new JLabel( question, JLabel.CENTER);
		pergunta.setFont(new Font("SansSerif", Font.BOLD, 24));
		pergunta.setForeground(Color.BLACK);
		frame.add(pergunta);
		
		JPanel optionsPainel = new JPanel(new GridLayout (2,2));
		optionsPainel.setBackground(new Color(237, 237, 237));
		
		
		Cores[] cores = Cores.values();

		for (int i= 0; i < 4; i++) {
			JButton botao = new JButton();
			botao.setBackground(cores[i].getColor());
			botao.setForeground(Color.WHITE);
			botao.setFont(new Font("SansSerif", Font.BOLD, 16));
			optionsPainel.add(botao);
		}
		frame.add(optionsPainel);
		
		frame.revalidate();
		frame.repaint();
	}
	

	// pequeno teste
	public static void main(String[] args) {
		Painel p = new Painel();
		//p.uploadHomePage();
		//p.uploadQuestion("Qual é o meu nome?");
		p.uploadQuestionOptions("Qual é o meu nome?");
		// p.open();
	}
}
