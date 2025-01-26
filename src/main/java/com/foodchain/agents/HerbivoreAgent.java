package com.foodchain.agents;

import com.foodchain.SimulationLauncher;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.foodchain.SimulationLauncher;
import jade.core.behaviours.CyclicBehaviour;

public class HerbivoreAgent extends Agent {
    private Position position;
    private int energy = 100;
    private static final double MOVEMENT_RANGE = 5.0;
    private static final int ENERGY_CONSUMPTION = 3;
    private static final double HUNTING_RADIUS = 10.0;
    private static final int DIRECTION_CHANGE_THRESHOLD = 5; // Ticks sem encontrar comida antes de mudar direção

    // Variáveis de comportamento de busca
    private int ticksWithoutFood = 0;
    private double facingDirection = Math.random() * 2 * Math.PI; // Direção para onde o herbívoro está olhando

    private boolean isInHuntingRadius(Position target) {
        return position.distanceTo(target) <= HUNTING_RADIUS;
    }

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            position = (Position) args[0];
        }

        // Atualiza posição e energia iniciais
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);

        // Registra no Facilitador de Diretório
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("herbivore");
        sd.setName(getLocalName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        // Adiciona comportamento para mover, caçar e consumir energia
        addBehaviour(new TickerBehaviour(this, 1000) {
            protected void onTick() {
                boolean foundFood = false;

                // Tenta encontrar plantas
                try {
                    // Procura por plantas
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("plant");
                    template.addServices(sd);
                    DFAgentDescription[] result = DFService.search(myAgent, template);

                    // Encontra a planta mais próxima dentro do campo de visão
                    AID closestPlantAID = null;
                    double closestDistance = Double.MAX_VALUE;
                    Position closestPlantPos = null;
                    for (DFAgentDescription plant : result) {
                        AID plantAID = plant.getName();
                        ACLMessage posRequest = new ACLMessage(ACLMessage.REQUEST);
                        posRequest.addReceiver(plantAID);
                        posRequest.setContent("getPosition");
                        send(posRequest);

                        // Aguarda resposta
                        ACLMessage reply = blockingReceive();
                        if (reply != null && reply.getContent() != null) {
                            try {
                                String[] coords = reply.getContent().split(",");
                                if (coords.length == 2) {
                                    Position plantPos = new Position(
                                            Double.parseDouble(coords[0].trim()),
                                            Double.parseDouble(coords[1].trim()));
                                    double distance = position.distanceTo(plantPos);
                                    // Considera apenas plantas dentro do campo de visão
                                    if (distance < closestDistance && isInFieldOfView(plantPos)) {
                                        closestDistance = distance;
                                        closestPlantAID = plantAID;
                                        closestPlantPos = plantPos;
                                    }
                                }
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // TODO: Implementar consumo de plantas
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Diminui tempo de espera para alimentação
                if (feedingCooldown > 0) {
                    feedingCooldown--;
                }

                // Se não encontrou planta ou não está com fome, move na direção atual
                if (!foundFood) {
                    ticksWithoutFood++;

                    // Muda direção se ficar muito tempo sem encontrar comida
                    if (ticksWithoutFood >= DIRECTION_CHANGE_THRESHOLD) {
                        // Quando a energia está baixa, faz mudanças menores de direção
                        if (energy < 30) {
                            facingDirection += (Math.random() - 0.5) * Math.PI / 2; // Muda até ±45 graus
                        } else {
                            facingDirection = Math.random() * 2 * Math.PI; // Direção completamente aleatória
                        }
                        ticksWithoutFood = 0;
                    }

                    // Move na direção que está olhando
                    double moveDistance = MOVEMENT_RANGE;
                    // Move mais rápido quando a energia está baixa
                    if (energy < 30) {
                        moveDistance *= 1.3; // 30% mais rápido quando desesperado por comida
                    }

                    double newX = position.x + (moveDistance * Math.cos(facingDirection));
                    double newY = position.y + (moveDistance * Math.sin(facingDirection));

                    // Adiciona aleatoriedade apenas quando não está perseguindo comida
                    if (ticksWithoutFood > 0) {
                        double randomAngle = (Math.random() - 0.5) * (FOV_ANGLE / 2);
                        double adjustedDirection = facingDirection + randomAngle;
                        newX += (Math.random() * MOVEMENT_RANGE / 4) * Math.cos(adjustedDirection);
                        newY += (Math.random() * MOVEMENT_RANGE / 4) * Math.sin(adjustedDirection);
                    }

                    // Verifica se vai bater em uma borda e muda direção se necessário
                    if (newX <= 5 || newX >= 95 || newY <= 5 || newY >= 95) {
                        // Rotaciona direção em 90-180 graus ao bater na borda
                        facingDirection += Math.PI * (0.5 + Math.random() * 0.5);
                        facingDirection = facingDirection % (2 * Math.PI);

                        newX = position.x + (moveDistance * Math.cos(facingDirection));
                        newY = position.y + (moveDistance * Math.sin(facingDirection));
                    }

                    // Mantém dentro dos limites
                    newX = Math.max(5, Math.min(95, newX));
                    newY = Math.max(5, Math.min(95, newY));

                    position = new Position(newX, newY);
                }

                // Consome energia
                energy -= ENERGY_CONSUMPTION;
                if (energy <= 0) {
                    energy = 0;
                    try {
                        DFService.deregister(myAgent);
                    } catch (FIPAException e) {
                        e.printStackTrace();
                    }
                    myAgent.doDelete();
                    return;
                }

                // Atualiza GUI com nova posição e energia
                SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
            }
        });

        // Adiciona comportamento para lidar com solicitações de posição após o
        // comportamento de movimento
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
                                energy -= energyToConsume;
                                if (energy <= 0) {
                                    energy = 0;
                                    SimulationLauncher.updateAgentInfo(myAgent.getLocalName(), position, energy,
                                            facingDirection);
                                    try {
                                        DFService.deregister(myAgent);
                                    } catch (FIPAException e) {
                                        e.printStackTrace();
                                    }
                                    myAgent.doDelete();
                                } else {
                                    SimulationLauncher.updateAgentInfo(myAgent.getLocalName(), position, energy,
                                            facingDirection);
                                }
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
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        this.energy = energy;
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
    }
}