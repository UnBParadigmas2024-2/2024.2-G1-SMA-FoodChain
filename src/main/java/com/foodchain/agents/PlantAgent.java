package com.foodchain.agents;

import jade.core.Agent;

public class PlantAgent extends Agent {
    @Override
    protected void setup() {
        System.out.println("Plant agent " + getAID().getName() + " is ready.");
    }
}