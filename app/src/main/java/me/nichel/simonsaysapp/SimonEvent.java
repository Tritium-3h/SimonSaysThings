package me.nichel.simonsaysapp;

public class SimonEvent {
    public String color;
    public String counter;

    public SimonEvent(final String color, final int counter) {
        this.color = color;
        this.counter = String.valueOf(counter);
    }
}