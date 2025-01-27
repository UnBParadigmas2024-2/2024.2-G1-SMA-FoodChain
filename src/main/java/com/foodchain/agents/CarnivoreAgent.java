package com.foodchain.agents;

import com.foodchain.SimulationLauncher;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

public class CarnivoreAgent extends Agent {
    private Position position;
    private static final int MAX_ENERGY = 100;
    private int energy = MAX_ENERGY;
    private static final double MOVEMENT_RANGE = 5.0;
    private static final int ENERGY_CONSUMPTION = 2;
    private static final int PASSIVE_ENERGY_DECAY = 0;
    private static final double HUNTING_RADIUS = 17.5;
    private static final double MELEE_RANGE = 12.0;
    private static final int ENERGY_FROM_HERBIVORE = 70;
    private static final int HUNTING_THRESHOLD = 60;
    private static final int DIRECTION_CHANGE_THRESHOLD = 5;
    private static final double FOV_ANGLE = Math.PI / 1.5;
    private static final double FOV_RANGE = HUNTING_RADIUS;
    private static final double SPATIAL_AWARENESS_RADIUS = 5.0;

    // Variáveis de comportamento de busca
    private int ticksWithoutFood = 0;
    private double facingDirection = Math.random() * 2 * Math.PI; // Direção para onde o carnívoro está olhando

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

        // Normaliza os ângulos para [0, 2π]
        double normalizedFacing = (facingDirection + 2 * Math.PI) % (2 * Math.PI);
        double normalizedTarget = (angleToTarget + 2 * Math.PI) % (2 * Math.PI);

        // Calcula o menor ângulo entre as duas direções
        double angleDiff = Math.abs(normalizedFacing - normalizedTarget);
        if (angleDiff > Math.PI) {
            angleDiff = 2 * Math.PI - angleDiff;
        }

        return angleDiff <= FOV_ANGLE / 2;
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
        sd.setType("carnivore");
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
                if (energy < HUNTING_THRESHOLD) {
                    try {
                        // Procura por herbívoros
                        DFAgentDescription template = new DFAgentDescription();
                        ServiceDescription sd = new ServiceDescription();
                        sd.setType("herbivore");
                        template.addServices(sd);
                        DFAgentDescription[] result = DFService.search(myAgent, template);

                        // Encontra o herbívoro mais próximo dentro do campo de visão
                        AID closestHerbivoreAID = null;
                        double closestDistance = Double.MAX_VALUE;
                        Position closestHerbivorePos = null;
                        for (DFAgentDescription herbivore : result) {
                            AID herbivoreAID = herbivore.getName();
                            ACLMessage posRequest = new ACLMessage(ACLMessage.REQUEST);
                            posRequest.addReceiver(herbivoreAID);
                            posRequest.setContent("getPosition");
                            send(posRequest);

                            // Aguarda resposta com timeout
                            ACLMessage reply = blockingReceive(1000);
                            if (reply != null) {
                                String[] coords = reply.getContent().split(",");
                                Position herbivorePos = new Position(
                                        Double.parseDouble(coords[0]),
                                        Double.parseDouble(coords[1]));
                                double distance = position.distanceTo(herbivorePos);
                                // Considera apenas herbívoros dentro do campo de visão
                                if (distance < closestDistance && isInFieldOfView(herbivorePos)) {
                                    closestDistance = distance;
                                    closestHerbivoreAID = herbivoreAID;
                                    closestHerbivorePos = herbivorePos;
                                }
                            }
                        }

                        // Se encontrou um herbívoro dentro do alcance, verifica se está ao alcance de
                        // ataque
                        if (closestHerbivoreAID != null) {
                            if (closestDistance <= MELEE_RANGE) {
                                // Solicita energia do herbívoro
                                ACLMessage energyRequest = new ACLMessage(ACLMessage.REQUEST);
                                energyRequest.addReceiver(closestHerbivoreAID);
                                energyRequest.setContent("getEnergy");
                                send(energyRequest);

                                // Aguarda resposta de energia
                                ACLMessage energyReply = blockingReceive(1000);
                                if (energyReply != null) {
                                    int herbivoreEnergy = Integer.parseInt(energyReply.getContent());
                                    if (herbivoreEnergy > 0) {
                                        int energyToConsume = Math.min(herbivoreEnergy, ENERGY_FROM_HERBIVORE);

                                        // Move para a posição do herbívoro antes de consumir
                                        position = closestHerbivorePos;
                                        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy,
                                                facingDirection);

                                        // Envia pedido de consumo
                                        ACLMessage consumeRequest = new ACLMessage(ACLMessage.REQUEST);
                                        consumeRequest.addReceiver(closestHerbivoreAID);
                                        consumeRequest.setContent("consume," + energyToConsume);
                                        send(consumeRequest);

                                        // Atualiza energia para o máximo
                                        energy = MAX_ENERGY;

                                        // Mata o herbívoro
                                        ACLMessage killMessage = new ACLMessage(ACLMessage.REQUEST);
                                        killMessage.addReceiver(closestHerbivoreAID);
                                        killMessage.setContent("die");
                                        send(killMessage);
                                        ticksWithoutFood = 0;
                                    }
                                }
                            } else {
                                // Se não estiver ao alcance de ataque, ajusta direção para o herbívoro e move
                                // mais rápido
                                double dx = closestHerbivorePos.x - position.x;
                                double dy = closestHerbivorePos.y - position.y;
                                facingDirection = Math.atan2(dy, dx);

                                // Calcula velocidade base de caça
                                double huntingSpeed = MOVEMENT_RANGE * 2.0; // Velocidade base dobrada

                                // Ajusta velocidade baseado na distância até a presa
                                if (closestDistance < HUNTING_RADIUS / 2) {
                                    // Diminui velocidade ao se aproximar para melhor precisão
                                    huntingSpeed *= 0.8;
                                } else if (closestDistance > HUNTING_RADIUS * 0.8) {
                                    // Aumenta velocidade quando longe para alcançar
                                    huntingSpeed *= 1.5;
                                }

                                // Calcula movimento com melhor precisão
                                double moveX = dx * (huntingSpeed / closestDistance);
                                double moveY = dy * (huntingSpeed / closestDistance);

                                // Aplica movimento com verificação de limites
                                position = new Position(
                                        Math.max(5, Math.min(95, position.x + moveX)),
                                        Math.max(5, Math.min(95, position.y + moveY)));

                                // Reinicia contagem sem comida já que está perseguindo ativamente
                                ticksWithoutFood = 0;

                                // Atualiza GUI imediatamente para mostrar perseguição suave
                                SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                // Sempre move se não estiver perseguindo presa
                ticksWithoutFood++;

                // Muda direção se ficar muito tempo sem encontrar comida
                if (ticksWithoutFood >= DIRECTION_CHANGE_THRESHOLD) {
                    // Quando a energia está baixa, faz mudanças menores de direção para manter
                    // perseguição mais consistente
                    if (energy < 30) {
                        facingDirection += (Math.random() - 0.5) * Math.PI / 2; // Muda até ±45 graus
                    } else {
                        facingDirection = Math.random() * 2 * Math.PI; // Direção completamente aleatória
                    }
                    facingDirection = facingDirection % (2 * Math.PI);
                    ticksWithoutFood = 0;
                }

                // Move na direção que está olhando
                double moveDistance = MOVEMENT_RANGE;
                // Move mais rápido quando a energia está baixa
                if (energy < 30) {
                    moveDistance *= 1.5; // 50% mais rápido quando desesperado por comida
                }

                // Calcula nova posição
                double newX = position.x + (moveDistance * Math.cos(facingDirection));
                double newY = position.y + (moveDistance * Math.sin(facingDirection));

                // Adiciona aleatoriedade apenas quando não está perseguindo presa
                if (ticksWithoutFood > 0) {
                    double randomAngle = (Math.random() - 0.5) * (FOV_ANGLE / 4);
                    double adjustedDirection = facingDirection + randomAngle;
                    newX += (Math.random() * MOVEMENT_RANGE / 6) * Math.cos(adjustedDirection);
                    newY += (Math.random() * MOVEMENT_RANGE / 6) * Math.sin(adjustedDirection);
                }

                // Verifica se vai bater em uma borda e muda direção se necessário
                if (newX <= 5 || newX >= 95 || newY <= 5 || newY >= 95) {
                    // Rotaciona direção em 90-180 graus ao bater na borda
                    facingDirection += Math.PI * (0.5 + Math.random() * 0.5);
                    facingDirection = facingDirection % (2 * Math.PI);

                    // Recalcula posição com nova direção
                    newX = position.x + (moveDistance * Math.cos(facingDirection));
                    newY = position.y + (moveDistance * Math.sin(facingDirection));
                }

                // Mantém dentro dos limites
                newX = Math.max(5, Math.min(95, newX));
                newY = Math.max(5, Math.min(95, newY));

                position = new Position(newX, newY);

                // Aplica consumo de energia tanto do movimento quanto passivo
                energy -= (ENERGY_CONSUMPTION + PASSIVE_ENERGY_DECAY);
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

                if (energy <= 30) {
                    SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
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