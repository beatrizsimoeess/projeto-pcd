package com.example;
import java.util.List;

public class Quiz {
    private String name;
    private List<Pergunta> questions;

    public String getName(){
        return name;
    }

    public void setName( String name){
        this.name = name;
    }

    public List<Pergunta> getQuestions(){
        return questions;
    }

    public void setQuestions(List<Pergunta> questions){
        this.questions = questions;
    }
}
