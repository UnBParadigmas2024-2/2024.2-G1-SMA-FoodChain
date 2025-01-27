package com.foodchain.agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import com.foodchain.SimulationLauncher;
import java.util.logging.Logger;

public class PlantAgent extends Agent {
    private Position position;
    private int energy = 100;
    private static final Logger logger = Logger.getLogger(PlantAgent.class.getName());
    private static final int ENERGY_GENERATION = 10;
    private static final int CRITICAL_ENERGY = 20;

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            position = (Position) args[0];
        }

        // Update initial position and energy
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
        logger.info(String.format("Plant %s initialized at position (%.2f, %.2f) with energy %d",
                getLocalName(), position.x, position.y, energy));

        // Register in Directory Facilitator
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("plant");
        sd.setName(getLocalName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            logger.info(String.format("Plant %s registered in Directory Facilitator", getLocalName()));
        } catch (FIPAException e) {
            logger.severe(String.format("Plant %s failed to register in Directory Facilitator: %s",
                    getLocalName(), e.getMessage()));
        }

        // Add behavior to generate energy
        addBehaviour(new TickerBehaviour(this, 1000) {
            protected void onTick() {
                if (energy < 100) {
                    int oldEnergy = energy;
                    energy += ENERGY_GENERATION;
                    if (energy > 100)
                        energy = 100;
                    logger.info(String.format("Plant %s generated energy: %d -> %d at position (%.2f, %.2f)",
                            getLocalName(), oldEnergy, energy, position.x, position.y));
                    // Update GUI when energy changes
                    SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
                }
            }
        });

        // Add behavior to handle position requests
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
                            logger.fine(String.format("Plant %s sent position (%.2f, %.2f) to %s",
                                    getLocalName(), position.x, position.y, msg.getSender().getLocalName()));
                            break;
                        case "getEnergy":
                            reply.setContent(String.valueOf(energy));
                            send(reply);
                            logger.fine(String.format("Plant %s sent energy %d to %s",
                                    getLocalName(), energy, msg.getSender().getLocalName()));
                            break;
                        default:
                            if (msg.getContent().startsWith("consume,")) {
                                String[] parts = msg.getContent().split(",");
                                int energyToConsume = Integer.parseInt(parts[1]);

                                // Only allow consumption if not critically low
                                if (energy > CRITICAL_ENERGY) {
                                    energy -= energyToConsume;
                                    if (energy < CRITICAL_ENERGY) {
                                        energy = CRITICAL_ENERGY;
                                    }
                                    logger.info(String.format("Plant %s energy consumed: %d, remaining: %d",
                                            getLocalName(), energyToConsume, energy));
                                    SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);

                                    // Send success response
                                    reply.setContent("success," + energyToConsume);
                                } else {
                                    // Send failure response
                                    reply.setContent("failed,critically_low");
                                }
                                send(reply);
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
        // Update GUI when position changes
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        int oldEnergy = this.energy;
        this.energy = energy;
        logger.info(String.format("Plant %s energy changed: %d -> %d (consumed by herbivore)",
                getLocalName(), oldEnergy, energy));
        // Update GUI when energy changes
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy);
    }
}