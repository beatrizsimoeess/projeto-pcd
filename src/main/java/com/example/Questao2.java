package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.Gson;

public class Questao2 {

    private String questao;
    private String[] opcoes;
    private int points;
    private int correct;

    public String getQuestao() {
        return questao;
    }

    public void setQuestao(String questao) {
        this.questao = questao;
    }

    public String[] getOpcoes() {
        return opcoes;
    }

    public void setOpcoes(String[] opcoes) {
        this.opcoes = opcoes;
    }

    public int getNum_opcoes() {
        return (opcoes != null) ? opcoes.length : 0;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getCorrect() {
        return correct;
    }

    public void setCorrect(int correct) {
        this.correct = correct;
    }

    public static Questao2 readFromFile(String caminhoArquivo) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(caminhoArquivo)));
            Gson gson = new Gson();
            return gson.fromJson(content, Questao2.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
