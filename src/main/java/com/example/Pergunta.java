package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Pergunta {

    private String question;
    private String[] options;
    private int points;
    private int correct;

    public String getQuestao() {
        return question;
    }

    public void setQuestao(String questao) {
        this.question = questao;
    }

    public String[] getOpcoes() {
        return options;
    }

    public void setOpcoes(String[] opcoes) {
        this.options = opcoes;
    }

    public int getNum_opcoes() {
        return (options != null) ? options.length : 0;
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

    public static List<Pergunta> readAllFromFile(String caminhoArquivo) {
        try {
            String content = Files.readString(Paths.get(caminhoArquivo));
            Gson gson = new Gson();
            return gson.fromJson(content, new TypeToken<List<Pergunta>>(){}.getType());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
