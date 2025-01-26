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
import jade.lang.acl.ACLMessage;

public class HerbivoreAgent extends Agent {
    private Position position;
    private int energy = 100;
    private static final double MOVEMENT_RANGE = 5.0;
    private static final int ENERGY_CONSUMPTION = 3;
    private static final double HUNTING_RADIUS = 10.0;
    private static final int DIRECTION_CHANGE_THRESHOLD = 5; // Ticks sem encontrar comida antes de mudar direção
    private static final double FOV_ANGLE = Math.PI / 2; // Campo de visão de 90 graus
    private static final double FOV_RANGE = HUNTING_RADIUS; // Mesmo alcance que o raio de caça
    private static final double SPATIAL_AWARENESS_RADIUS = 7.5; // Reduzido de 15.0
    private static final int FEEDING_COOLDOWN = 5; // Aumentado de 3 para 5 ticks
    private static final double MIN_DISTANCE_TO_LAST_PLANT = 15.0; // Distância mínima antes de se alimentar da mesma
                                                                   // planta novamente

    // Variáveis de comportamento de busca
    private int ticksWithoutFood = 0;
    private double facingDirection = Math.random() * 2 * Math.PI; // Direção para onde o herbívoro está olhando
    private int feedingCooldown = 0; // Contador de tempo de espera para alimentação
    private Position lastFeedingPosition = null; // Última posição onde se alimentou

    // Método auxiliar para verificar se um ponto está dentro do alcance de detecção
    // (cone de visão ou raio de percepção)
    private boolean isInFieldOfView(Position target) {
        double distance = position.distanceTo(target);

        // Primeiro verifica se o alvo está dentro do raio de percepção
        if (distance <= SPATIAL_AWARENESS_RADIUS) {
            // Se o alvo estiver muito próximo, vira para encará-lo
            double dx = target.x - position.x;
            double dy = target.y - position.y;
            facingDirection = Math.atan2(dy, dx);
            return true;
        }

        // Se não estiver no raio de percepção, verifica se está no cone de visão
        if (distance > FOV_RANGE) {
            return false;
        }

        // Calcula o ângulo entre a direção atual e o alvo
        double dx = target.x - position.x;
        double dy = target.y - position.y;
        double angleToTarget = Math.atan2(dy, dx);

        return angleToTarget <= FOV_ANGLE / 2;
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

                // Tenta encontrar e comer plantas
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