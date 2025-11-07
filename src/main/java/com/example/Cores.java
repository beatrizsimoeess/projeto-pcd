package com.example;

import java.awt.Color;

public enum Cores {
    VERMELHO(new Color(222, 73, 73)),
    AZUL(new Color(36, 118, 224)),
    AMARELO(new Color(235, 201, 35)),
    VERDE(new Color(15, 168, 36));

    private final Color color;

    Cores(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }
}
