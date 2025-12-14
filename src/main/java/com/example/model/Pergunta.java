package com.example.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import com.google.gson.Gson;

public class Pergunta {

    private String question;
    private String[] options;
    private int points;
    private int correct;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String[] getOptions() {
        return options;
    }

    public void setOptions(String[] options) {
        this.options = options;
    }

    public int getNum_options() {
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

            Quiz quiz =gson.fromJson(content, Quiz.class);
        if (quiz != null && quiz.getQuestions() != null && !quiz.getQuestions().isEmpty()) {
                return quiz.getQuestions();
            } else {
                System.err.println("Nenhuma pergunta encontrada no ficheiro JSON");
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
