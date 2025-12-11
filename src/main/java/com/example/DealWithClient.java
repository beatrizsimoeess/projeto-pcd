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
	

	private void doConnection() throws IOException {
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        // 'true' ativa o autoFlush, enviando a mensagem imediatamente.
        this.out = new PrintWriter(socket.getOutputStream(), true); 
	}

	@Override
	public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // 1. Espera pela mensagem de REGISTO
            String request = in.readLine();
            if (request != null && request.startsWith("REGISTER")) {
                processarRegisto(request);
            } else {
                out.println("ERROR Comando inesperado. Esperado: REGISTER ...");
                return; // Encerra se não começar com registo
            }

            // 2. Loop principal de escuta (ficará ativo durante o jogo)
            while (true) {
                String msg = in.readLine();
                if (msg == null) break; // Conexão fechada

                // Na Fase 4 ainda não processamos respostas, mas a estrutura fica pronta
                if (msg.startsWith("RESPONSE")) {
                    // Futuro: processar resposta
                    System.out.println("Resposta recebida de " + username + ": " + msg);
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
        } else {
            out.println("ERRO_FORMATO: Formato esperado: REGISTO <Codigo> <Equipa> <Username>");
        }
    }
    

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }


}