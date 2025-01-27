package com.foodchain.agents;

import com.foodchain.SimulationLauncher;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class CarnivoreAgent extends Agent {
    private Position position;
    private static final int MAX_ENERGY = 100;
    private int energy = MAX_ENERGY;
    private static final int ENERGY_CONSUMPTION = 2;
    private static final int PASSIVE_ENERGY_DECAY = 0;
    private static final double HUNTING_RADIUS = 17.5;
    private static final double FOV_ANGLE = Math.PI / 1.5;
    private static final double FOV_RANGE = HUNTING_RADIUS;
    private static final double SPATIAL_AWARENESS_RADIUS = 5.0;

    // Variáveis de comportamento de busca
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