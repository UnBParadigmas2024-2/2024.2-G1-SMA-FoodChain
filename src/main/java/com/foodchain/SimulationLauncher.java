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
        // Cria a interface gráfica
        gui = new SimulationGUI();

        // Obtém uma instância do JADE runtime
        Runtime runtime = Runtime.instance();

        // Cria um perfil
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "true");

        // Cria o container principal
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

    public static void createNewAgent(String agentType, Position position) {
        try {
            String agentName = agentType + System.currentTimeMillis();
            AgentType type = AgentType.valueOf(agentType.toUpperCase());

            AgentInfo agentInfo = new AgentInfo(agentName, position, type, 100);
            agentInfos.add(agentInfo);

            AgentController agent = mainContainer.createNewAgent(
                    agentName,
                    "com.foodchain.agents." + agentType + "Agent",
                    new Object[] { position });
            agent.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Método para atualizar posição e energia do agente (será chamado pelos
    // agentes)
    public static void updateAgentInfo(String name, Position position, int energy) {
        updateAgentInfo(name, position, energy, 0.0); // Direção padrão
    }

    // Método sobrecarregado que inclui direção
    public static void updateAgentInfo(String name, Position position, int energy, double facingDirection) {
        for (int i = 0; i < agentInfos.size(); i++) {
            AgentInfo info = agentInfos.get(i);
            if (info.name.equals(name)) {
                AgentInfo updatedInfo = new AgentInfo(name, position, info.type, energy, facingDirection);
                agentInfos.set(i, updatedInfo);
                break;
            }
        }
    }
}