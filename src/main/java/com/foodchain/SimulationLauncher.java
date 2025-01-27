package com.foodchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.foodchain.agents.Position;
import com.foodchain.gui.SimulationGUI;
import com.foodchain.gui.SimulationGUI.AgentInfo;
import com.foodchain.gui.SimulationGUI.AgentInfo.AgentType;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class SimulationLauncher {
    private static SimulationGUI gui;
    private static final Random random = new Random();
    private static List<AgentInfo> agentInfos = new CopyOnWriteArrayList<>();
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
            // Cria os agentes
            List<Position> occupiedPositions = new ArrayList<>();
            double MIN_SPAWN_DISTANCE = 20.0; // Distância mínima entre agentes ao nascer

            // Plantas - espalhadas pelo espaço
            for (int i = 0; i < 15; i++) {
                Position pos;
                boolean validPosition;
                int attempts = 0;
                do {
                    pos = new Position(
                            5 + random.nextDouble() * 90,
                            5 + random.nextDouble() * 90);
                    validPosition = true;
                    // Verifica distância de outros agentes
                    for (Position occupied : occupiedPositions) {
                        if (pos.distanceTo(occupied) < MIN_SPAWN_DISTANCE) {
                            validPosition = false;
                            break;
                        }
                    }
                    attempts++;
                } while (!validPosition && attempts < 50);

                occupiedPositions.add(pos);
                AgentInfo agentInfo = new AgentInfo("Plant" + i, pos, AgentType.PLANT, 100);
                agentInfos.add(agentInfo);

                AgentController plantAgent = mainContainer.createNewAgent(
                        "Plant" + i,
                        "com.foodchain.agents.PlantAgent",
                        new Object[] { pos });
                plantAgent.start();
            }

            // Inicia thread de atualização da interface gráfica
            new Thread(() -> {
                while (true) {
                    try {
                        // Atualiza a interface com as posições atuais dos agentes
                        gui.updateAgentPositions(agentInfos);
                        Thread.sleep(100); // Atualiza a cada 100ms
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

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