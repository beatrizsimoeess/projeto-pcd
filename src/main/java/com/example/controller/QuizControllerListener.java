package com.example.controller;

public interface QuizControllerListener {
  
    void onRegisterAttempt(String host, int port, String gameCode, String team, String player);
    
    void onAnswerSubmitted(int answerIndex);
    
    void onExitGame();
}