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
            doConnection();
            
            out.println("Bem-vindo! Por favor, preencha o registo REGISTO <Codigo> <Equipa> <Username>");
            String clientMessage = in.readLine();
            
            if (clientMessage != null && clientMessage.startsWith("REGISTO")) {
                processarRegisto(clientMessage);
            }
            
            if (jogoAtual == null) {
                out.println("FALHA: Registo inválido ou falhou. A fechar conexão.");
                return;
            }
            
            out.println("SUCESSO: Registado no Jogo " + jogoAtual.getGameCode());
            System.out.println("Cliente '" + username + "' registado e à espera do início do jogo.");

            while (!socket.isClosed() && !isInterrupted()) {
                
                String input = in.readLine();
                if (input == null) break; 
                
                if (input.startsWith("RESPOSTA")) {
                    //  Lógica para processar a resposta, interagir com Latch/Barrier
                }
                
                
            }

        } catch (IOException e) {
            System.out.println("Cliente " + (username != null ? username : "desconhecido") + " desconectado.");
        } finally {
            fecharConexao();
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

   
    private void fecharConexao() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão.");
        }
    }
}