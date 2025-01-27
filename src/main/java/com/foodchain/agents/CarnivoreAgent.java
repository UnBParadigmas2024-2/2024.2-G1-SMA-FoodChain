package com.foodchain.agents;

import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.foodchain.SimulationLauncher;
import java.util.logging.Logger;

public class CarnivoreAgent extends Agent {
    private Position position;
    private static final int MAX_ENERGY = 100;
    private int energy = MAX_ENERGY;
    private static final Logger logger = Logger.getLogger(CarnivoreAgent.class.getName());
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

    // Constantes para reprodução
    private static final int REPRODUCTION_COOLDOWN = 150; // Tempo de espera maior que os herbívoros
    private static final int REPRODUCTION_ENERGY_COST = 50; // Custo de energia maior que os herbívoros
    private static final int MIN_AGE_FOR_REPRODUCTION = 65; // Tempo de maturidade maior que os herbívoros
    private int reproductionCooldown = REPRODUCTION_COOLDOWN;
    private int age = 0;

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
            logger.info(String.format(
                    "Carnívoro %s detectou alvo no raio de percepção, virando para encará-lo",
                    getLocalName()));
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
        logger.info(String.format("Carnívoro %s inicializado na posição (%.2f, %.2f) com energia %d",
                getLocalName(), position.x, position.y, energy));

        // Registra no Facilitador de Diretório
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("carnivore");
        sd.setName(getLocalName());
        dfd.addServices(sd);

        try {
            DFService.register(this, dfd);
            logger.info(String.format("Carnívoro %s registrado no Facilitador de Diretório", getLocalName()));
        } catch (FIPAException e) {
            logger.severe(String.format("Carnívoro %s falhou ao registrar no Facilitador de Diretório: %s",
                    getLocalName(), e.getMessage()));
        }

        // Adiciona comportamento para mover, caçar e consumir energia
        addBehaviour(new TickerBehaviour(this, 1000) {
            protected void onTick() {
                age++;
                if (reproductionCooldown > 0) {
                    reproductionCooldown--;
                }

                Position oldPos = new Position(position.x, position.y);
                int oldEnergy = energy;

                // Tenta encontrar e comer herbívoros apenas quando a energia está abaixo do
                // limite
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
                            logger.info(String.format(
                                    "Carnívoro %s encontrou herbívoro em (%.2f, %.2f), distância: %.2f",
                                    getLocalName(), closestHerbivorePos.x, closestHerbivorePos.y, closestDistance));

                            // Consome apenas se estiver ao alcance de ataque
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
                                        logger.info(String.format(
                                                "Carnívoro %s consumiu %d de energia do herbívoro, energia recarregada para o máximo: %d",
                                                getLocalName(), energyToConsume, energy));

                                        // Mata o herbívoro
                                        ACLMessage killMessage = new ACLMessage(ACLMessage.REQUEST);
                                        killMessage.addReceiver(closestHerbivoreAID);
                                        killMessage.setContent("die");
                                        send(killMessage);
                                        ticksWithoutFood = 0;
                                        logger.info(String.format("Carnívoro %s matou herbívoro em (%.2f, %.2f)",
                                                getLocalName(),
                                                position.x, position.y));

                                        // Reproduz após caçada bem-sucedida se as condições forem atendidas
                                        if (reproductionCooldown <= 0 &&
                                                age >= MIN_AGE_FOR_REPRODUCTION &&
                                                energy >= REPRODUCTION_ENERGY_COST) {
                                            energy -= REPRODUCTION_ENERGY_COST; // Deduz custo de energia
                                            reproductionCooldown = REPRODUCTION_COOLDOWN; // Reinicia tempo de espera
                                            Position newPosition = new Position(
                                                    position.x + (Math.random() * 20 - 10),
                                                    position.y + (Math.random() * 20 - 10));
                                            SimulationLauncher.createNewAgent("Carnivore", newPosition);
                                            logger.info(String.format("Carnívoro %s se reproduziu em (%.2f, %.2f)",
                                                    getLocalName(),
                                                    newPosition.x, newPosition.y));
                                        }
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

                                logger.info(String.format(
                                        "Carnívoro %s perseguindo herbívoro em (%.2f, %.2f), distância: %.2f",
                                        getLocalName(), closestHerbivorePos.x, closestHerbivorePos.y, closestDistance));

                                // Reinicia contagem sem comida já que está perseguindo ativamente
                                ticksWithoutFood = 0;

                                // Atualiza GUI imediatamente para mostrar perseguição suave
                                SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
                                return;
                            }
                        }
                    } catch (Exception e) {
                        logger.warning("Erro durante a caça: " + e.getMessage());
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
                    logger.info(String.format("Carnívoro %s mudando direção de busca por falta de comida",
                            getLocalName()));
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
                    logger.info(String.format("Carnívoro %s bateu na borda, mudando direção", getLocalName()));
                }

                // Mantém dentro dos limites
                newX = Math.max(5, Math.min(95, newX));
                newY = Math.max(5, Math.min(95, newY));

                position = new Position(newX, newY);

                // Aplica consumo de energia tanto do movimento quanto passivo
                energy -= (ENERGY_CONSUMPTION + PASSIVE_ENERGY_DECAY);
                if (energy <= 0) {
                    energy = 0;
                    logger.warning(String.format("Carnívoro %s morreu de fome!", myAgent.getLocalName()));
                    // Atualiza GUI uma última vez antes de morrer
                    SimulationLauncher.updateAgentInfo(myAgent.getLocalName(), position, energy, facingDirection);
                    try {
                        DFService.deregister(myAgent);
                    } catch (FIPAException e) {
                        logger.severe("Erro ao remover registro do carnívoro: " + e.getMessage());
                    }
                    myAgent.doDelete();
                    return;
                }

                logger.info(String.format("Carnívoro %s moveu de (%.2f, %.2f) para (%.2f, %.2f), energia: %d -> %d",
                        getLocalName(), oldPos.x, oldPos.y, position.x, position.y, oldEnergy, energy));

                if (energy <= 30) {
                    logger.warning(String.format("Carnívoro %s está criticamente baixo de energia!", getLocalName()));
                }

                // Atualiza GUI com nova posição e energia
                SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
            }
        });
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        Position oldPos = this.position;
        this.position = position;
        logger.info(String.format("Carnívoro %s posição atualizada: (%.2f, %.2f) -> (%.2f, %.2f)",
                getLocalName(), oldPos.x, oldPos.y, position.x, position.y));
        // Atualiza GUI quando a posição muda
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
    }

    public int getEnergy() {
        return energy;
    }

    public void setEnergy(int energy) {
        int oldEnergy = this.energy;
        this.energy = energy;
        logger.info(String.format("Carnívoro %s energia alterada: %d -> %d",
                getLocalName(), oldEnergy, energy));
        // Atualiza GUI quando a energia muda
        SimulationLauncher.updateAgentInfo(getLocalName(), position, energy, facingDirection);
    }
}