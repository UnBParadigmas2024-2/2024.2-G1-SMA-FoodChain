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
                        double closestDistance = Double.MAX_VALUE;
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
                                }
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