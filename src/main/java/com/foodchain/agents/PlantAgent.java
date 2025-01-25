package com.foodchain.agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import com.foodchain.SimulationLauncher;

public class PlantAgent extends Agent {
    private Position position;
    private int energy = 100;
    private static final int ENERGY_GENERATION_RATE = 5;
    private static final int MAX_ENERGY = 100;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            position = (Position) args[0];
        }

        // Atualiza posição e energia iniciais
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);

        // Registra no Facilitador de Diretório
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("plant");
        sd.setName(getLocalName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Adiciona comportamento para gerar energia ao longo do tempo
        addBehaviour(new TickerBehaviour(this, 1000) {
            protected void onTick() {
                if (energy < MAX_ENERGY) {
                    energy = Math.min(MAX_ENERGY, energy + ENERGY_GENERATION_RATE);
                    SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
                }
            }
        });

        // Adiciona comportamento para lidar com solicitações de posição e energia
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);

                    switch (msg.getContent()) {
                        case "getPosition":
                            reply.setContent(position.x + "," + position.y);
                            send(reply);
                            break;
                        case "getEnergy":
                            reply.setContent(String.valueOf(energy));
                            send(reply);
                            break;
                        default:
                            if (msg.getContent().startsWith("consume,")) {
                                String[] parts = msg.getContent().split(",");
                                int energyToConsume = Integer.parseInt(parts[1]);
                                energy = Math.max(0, energy - energyToConsume);
                                SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
                            }
                            break;
                    }
                } else {
                    block();
                }
            }
        });
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
    }
}