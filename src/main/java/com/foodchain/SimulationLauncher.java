package com.foodchain;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class SimulationLauncher {

    private static AgentContainer mainContainer;

    public static void main(String[] args) {
        // Get a JADE runtime instance
        Runtime runtime = Runtime.instance();

        // Create a Profile
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "true");

        // Create a main container
        mainContainer = runtime.createMainContainer(profile);

        try {
            // Create at least one agent to test
            AgentController testAgent = mainContainer.createNewAgent(
                    "TestAgent",
                    "com.foodchain.agents.PlantAgent",
                    new Object[] {});
            testAgent.start();
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }

    public static AgentContainer getMainContainer() {
        return mainContainer;
    }
}