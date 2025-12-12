package com.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class DealWithClient extends Thread {

	private final Socket socket;
	private final Servidor servidor; 

	private BufferedReader in;
	private PrintWriter out;

	private String username;
	private GameState jogoAtual;

	public DealWithClient (Socket socket, Servidor servidor){
		this.socket = socket;
		this.servidor = servidor;
	}


	@Override
	public void run() {
		try {
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);

			String request = in.readLine();
			if (request != null && request.startsWith("REGISTER")) {
				processarRegisto(request);
                if (jogoAtual == null) return; 
			} else {
				out.println("ERROR Comando inesperado. Esperado: REGISTER ...");
				return;
			}

			while (true) {
				String msg = in.readLine();
				if (msg == null) break;

				if (msg.startsWith("RESPONSE")) {
					String[] partes = msg.split("\\s+");
					if (partes.length == 4) {
						try {
							int answerIndex = Integer.parseInt(partes[3]);

							if (jogoAtual != null) {
								boolean registered = jogoAtual.registerResponse(this.username, answerIndex);

								if (registered) {
									System.out.println("DEBUG: Resposta de " + username + " registada: " + answerIndex);
								} else {
									System.out.println("AVISO: " + username + " tentou responder novamente.");
								}
							}
						} catch (NumberFormatException e) { 
							System.err.println("ERRO: Resposta inválida de " + username + ". Índice não é número: " + partes[3]);
						}
					} else {
						System.err.println("ERRO: Formato de RESPONSE inválido de " + username + ": " + msg);
					}
				}
			}
		} catch (IOException e) {
			System.out.println("Conexão perdida com " + username);
		} finally {
			try { socket.close(); } catch (IOException e) {}
		}
	}


	private void processarRegisto(String message) {
		String[] partes = message.split("\\s+");
		if (partes.length == 4) {
			String gameCode = partes[1];
			String teamName = partes[2];
			this.username = partes[3];

			this.jogoAtual = servidor.registarJogador(gameCode, teamName, username, this);
            
            if (this.jogoAtual != null) {
                sendMessage("SUCCESS Registo completo.");
            } else {
                sendMessage("ERROR Falha no registo. Verifique o código, equipa e o nome de utilizador.");
            }
		} else {
			out.println("ERRO_FORMATO: Formato esperado: REGISTER <Codigo> <Equipa> <Username>");
		}
	}


	public void sendMessage(String message) {
		if (out != null) {
			out.println(message);
		}
	}
}