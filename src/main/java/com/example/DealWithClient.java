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

    // Método doConnection() removido pois o in/out é inicializado no run()

	@Override
	public void run() {
        // O bloco 'try' engloba toda a comunicação que pode falhar
		try {
            // Inicialização (Repetida, mas funcional)
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream(), true);

			// 1. Espera pela mensagem de REGISTO
			String request = in.readLine();
			if (request != null && request.startsWith("REGISTER")) {
				processarRegisto(request);
                // Se o processamento falhar (e.g., username repetido), jogoAtual será null e esta thread terminará
                if (jogoAtual == null) return; 
			} else {
				out.println("ERROR Comando inesperado. Esperado: REGISTER ...");
				return;
			}

            // 2. Loop principal de escuta (apenas corre se o registo foi bem sucedido)
			while (true) {
				String msg = in.readLine();
				if (msg == null) break; // Conexão fechada

				if (msg.startsWith("RESPONSE")) {
					String[] partes = msg.split("\\s+");
                    // Formato esperado: RESPONSE <GameCode> <Username> <RespostaIndex>
					if (partes.length == 4) {
						try {
							int answerIndex = Integer.parseInt(partes[3]);

							if (jogoAtual != null) {
                                // CHAVE: Chama o GameState para registar a resposta e potencialmente fazer notifyAll()
								boolean registered = jogoAtual.registerResponse(this.username, answerIndex);

								if (registered) {
									System.out.println("DEBUG: Resposta de " + username + " registada: " + answerIndex);
								} else {
									System.out.println("AVISO: " + username + " tentou responder novamente.");
								}
							}
                        // O NumberFormatException deve apanhar APENAS o erro de parseInt
						} catch (NumberFormatException e) { 
							System.err.println("ERRO: Resposta inválida de " + username + ". Índice não é número: " + partes[3]);
						}
					} else {
						System.err.println("ERRO: Formato de RESPONSE inválido de " + username + ": " + msg);
					}
				}
			} // Fim do while(true)
		} catch (IOException e) {
            // Captura falha de rede/socket
			System.out.println("Conexão perdida com " + username);
		} finally {
            // Garante que o socket é fechado, independentemente do que aconteceu
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
            
            // Adiciona feedback ao cliente após registo
            if (this.jogoAtual != null) {
                sendMessage("SUCCESS Registo completo.");
            } else {
                sendMessage("ERROR Falha no registo. Verifique o código, equipa e o nome de utilizador.");
            }
		} else {
            // Corrigido para REGISTER (consistente com o if no run())
			out.println("ERRO_FORMATO: Formato esperado: REGISTER <Codigo> <Equipa> <Username>");
		}
	}


	public void sendMessage(String message) {
		if (out != null) {
			out.println(message);
		}
	}
}