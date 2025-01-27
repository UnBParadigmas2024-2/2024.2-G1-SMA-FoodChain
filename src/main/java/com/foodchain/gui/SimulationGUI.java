package com.foodchain.gui;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import com.foodchain.agents.Position;

public class SimulationGUI extends JFrame {
    private static final int WINDOW_WIDTH = 1024;
    private static final int WINDOW_HEIGHT = 768;
    private static final int AGENT_SIZE = 20;
    private static final int ENERGY_BAR_HEIGHT = 4;
    private static final int ENERGY_BAR_WIDTH = 30;
    private static final int gridSize = 50;
    private static final Font LABEL_FONT = new Font("Arial", Font.BOLD, 12);

    private SimulationPanel simulationPanel;
    private JPanel controlPanel;
    private JLabel herbivoreStatsLabel;
    private JLabel carnivoreStatsLabel;
    private JLabel tickCountLabel;
    private JLabel timeLabel;
    private JLabel simulationStatusLabel;
    private int totalHerbivoreDeaths = 0;
    private int totalCarnivoreDeaths = 0;
    private static int totalHerbivoreReproductions = 0;
    private static int totalCarnivoreReproductions = 0;
    private Set<String> deadHerbivores = new HashSet<>();
    private Set<String> deadCarnivores = new HashSet<>();
    private long tickCount = 0;
    private boolean simulationFrozen = false;
    private long startTime = System.currentTimeMillis();

    public static class AgentInfo {
        public enum AgentType {
            PLANT, HERBIVORE, CARNIVORE
        }

        public final String name;
        public final Position position;
        public final AgentType type;
        public final int energy;
        public final double facingDirection;

        public AgentInfo(String name, Position position, AgentType type, int energy) {
            this(name, position, type, energy, 0.0);
        }

        public AgentInfo(String name, Position position, AgentType type, int energy, double facingDirection) {
            this.name = name;
            this.position = position;
            this.type = type;
            this.energy = energy;
            this.facingDirection = facingDirection;
        }
    }

    public SimulationGUI() {
        setTitle("Simulação da Cadeia Alimentar");
        setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Cria layout principal
        setLayout(new BorderLayout());

        // Cria painel de simulação
        simulationPanel = new SimulationPanel();
        add(simulationPanel, BorderLayout.CENTER);

        // Cria painel de controle
        createControlPanel();
        add(controlPanel, BorderLayout.EAST);

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void createControlPanel() {
        controlPanel = new JPanel();
        controlPanel.setPreferredSize(new Dimension(200, WINDOW_HEIGHT));
        controlPanel.setBorder(BorderFactory.createTitledBorder("Estatísticas & Legenda"));
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));

        // Container para todos os painéis de estatísticas
        JPanel statsContainer = new JPanel();
        statsContainer.setLayout(new BoxLayout(statsContainer, BoxLayout.Y_AXIS));
        statsContainer.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Adiciona status da simulação
        JPanel statusPanel = new JPanel();
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBorder(BorderFactory.createTitledBorder("Status da Simulação"));
        statusPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        simulationStatusLabel = new JLabel("<html><b>Status:</b> Em Execução</html>");
        simulationStatusLabel.setForeground(new Color(0, 128, 0)); // Cor verde para status em execução
        simulationStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusPanel.add(simulationStatusLabel);
        statsContainer.add(statusPanel);
        statsContainer.add(Box.createVerticalStrut(10));

        // Adiciona contador de ticks e tempo
        JPanel timePanel = new JPanel();
        timePanel.setLayout(new BoxLayout(timePanel, BoxLayout.Y_AXIS));
        timePanel.setBorder(BorderFactory.createTitledBorder("Tempo de Simulação"));
        timePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tickCountLabel = new JLabel("<html><b>Ticks:</b> 0</html>");
        timeLabel = new JLabel("<html><b>Tempo:</b> 00:00:00</html>");
        tickCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        timePanel.add(tickCountLabel);
        timePanel.add(Box.createVerticalStrut(5));
        timePanel.add(timeLabel);
        statsContainer.add(timePanel);
        statsContainer.add(Box.createVerticalStrut(10));

        // Adiciona estatísticas da população
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Estatísticas da População"));
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        herbivoreStatsLabel = new JLabel(
                "<html><b>Herbívoros</b><br>Vivos: 0<br>Total de Mortes: 0<br>Reproduções: 0</html>");
        carnivoreStatsLabel = new JLabel(
                "<html><b>Carnívoros</b><br>Vivos: 0<br>Total de Mortes: 0<br>Reproduções: 0</html>");
        herbivoreStatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        carnivoreStatsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        statsPanel.add(herbivoreStatsLabel);
        statsPanel.add(Box.createVerticalStrut(10));
        statsPanel.add(carnivoreStatsLabel);
        statsContainer.add(statsPanel);
        statsContainer.add(Box.createVerticalStrut(20));

        // Adiciona o container de estatísticas ao painel de controle
        controlPanel.add(statsContainer);

        // Adiciona itens da legenda
        JPanel legendPanel = new JPanel();
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.setBorder(BorderFactory.createTitledBorder("Legenda"));
        legendPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        addLegendItem(legendPanel, "Plantas", new Color(34, 139, 34), "Estáticas, geram energia");
        addLegendItem(legendPanel, "Herbívoros", new Color(30, 144, 255), "Caçam plantas");
        addLegendItem(legendPanel, "Carnívoros", new Color(220, 20, 60), "Caçam herbívoros");

        controlPanel.add(legendPanel);
    }

    private void addLegendItem(JPanel panel, String name, Color color, String description) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Cria o quadrado colorido
        JPanel colorSquare = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(color);
                g.fillOval(0, 0, 15, 15);
            }
        };
        colorSquare.setPreferredSize(new Dimension(15, 15));

        item.add(colorSquare);
        item.add(new JLabel("<html><b>" + name + "</b><br>" + description + "</html>"));
        panel.add(item);
    }

    public static void incrementReproductionCount(String agentType) {
        if (agentType.equals("Herbivore")) {
            totalHerbivoreReproductions++;
        } else if (agentType.equals("Carnivore")) {
            totalCarnivoreReproductions++;
        }
    }

    public void updateAgentPositions(List<AgentInfo> agents) {
        // Primeiro conta a população atual
        int currentHerbivores = 0;
        int currentCarnivores = 0;

        for (AgentInfo agent : agents) {
            if (agent.type == AgentInfo.AgentType.HERBIVORE && agent.energy > 0) {
                currentHerbivores++;
            } else if (agent.type == AgentInfo.AgentType.CARNIVORE && agent.energy > 0) {
                currentCarnivores++;
            }
        }

        // Verifica se a simulação deve ser congelada
        if (!simulationFrozen && (currentHerbivores == 0 || currentCarnivores == 0)) {
            simulationFrozen = true;
            String reason = currentHerbivores == 0 ? "Extinção dos Herbívoros" : "Extinção dos Carnívoros";
            simulationStatusLabel
                    .setText(String.format("<html><b>Status:</b> Simulação Congelada<br>Motivo: %s</html>", reason));
            simulationStatusLabel.setForeground(new Color(128, 0, 0)); // Cor vermelha para status congelado
        }

        // Só incrementa o tick se a simulação não estiver congelada
        if (!simulationFrozen) {
            tickCount++;
            tickCountLabel.setText(String.format("<html><b>Ticks:</b> %d</html>", tickCount));

            // Atualiza o display de tempo
            long currentTime = System.currentTimeMillis();
            long elapsedTime = (currentTime - startTime) / 1000; // Converte para segundos

            long hours = elapsedTime / 3600;
            long minutes = (elapsedTime % 3600) / 60;
            long seconds = elapsedTime % 60;

            timeLabel.setText(String.format("<html><b>Tempo:</b> %02d:%02d:%02d</html>",
                    hours, minutes, seconds));
        }

        simulationPanel.setAgents(agents);

        // Atualiza contagem de mortes e rótulos
        for (AgentInfo agent : agents) {
            if (agent.type == AgentInfo.AgentType.HERBIVORE) {
                if (agent.energy <= 0 && !deadHerbivores.contains(agent.name)) {
                    totalHerbivoreDeaths++;
                    deadHerbivores.add(agent.name);
                    System.out.println(
                            "Morte de herbívoro registrada: " + agent.name + " - Total de mortes: "
                                    + totalHerbivoreDeaths);
                }
            } else if (agent.type == AgentInfo.AgentType.CARNIVORE) {
                if (agent.energy <= 0 && !deadCarnivores.contains(agent.name)) {
                    totalCarnivoreDeaths++;
                    deadCarnivores.add(agent.name);
                    System.out.println(
                            "Morte de carnívoro registrada: " + agent.name + " - Total de mortes: "
                                    + totalCarnivoreDeaths);
                }
            }
        }

        // Atualiza rótulos
        herbivoreStatsLabel.setText(
                String.format("<html><b>Herbívoros</b><br>Vivos: %d<br>Total de Mortes: %d<br>Reproduções: %d</html>",
                        currentHerbivores, totalHerbivoreDeaths, totalHerbivoreReproductions));
        carnivoreStatsLabel.setText(
                String.format("<html><b>Carnívoros</b><br>Vivos: %d<br>Total de Mortes: %d<br>Reproduções: %d</html>",
                        currentCarnivores, totalCarnivoreDeaths, totalCarnivoreReproductions));

        simulationPanel.repaint();
    }

    public boolean isSimulationFrozen() {
        return simulationFrozen;
    }

    private class SimulationPanel extends JPanel {
        private List<AgentInfo> agents = new ArrayList<>();

        public SimulationPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(WINDOW_WIDTH - 200, WINDOW_HEIGHT));
        }

        public void setAgents(List<AgentInfo> agents) {
            this.agents = agents;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Ativa suavização de bordas
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Desenha grade de fundo
            g2d.setColor(new Color(230, 230, 230));
            for (int x = 0; x < getWidth(); x += gridSize) {
                g2d.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += gridSize) {
                g2d.drawLine(0, y, getWidth(), y);
            }

            // Desenha os agentes
            for (AgentInfo agent : agents) {
                // Converte coordenadas do agente para coordenadas da tela
                // Mapeia do espaço virtual (5-95) para o espaço da tela
                int x = (int) ((agent.position.x - 5) / 90.0 * (getWidth() - AGENT_SIZE));
                int y = (int) ((agent.position.y - 5) / 90.0 * (getHeight() - AGENT_SIZE));

                if (agent.energy <= 0) {
                    // Desenha X para agentes mortos
                    g2d.setColor(Color.DARK_GRAY);
                    g2d.setStroke(new BasicStroke(3));
                    g2d.drawLine(x, y, x + AGENT_SIZE, y + AGENT_SIZE);
                    g2d.drawLine(x + AGENT_SIZE, y, x, y + AGENT_SIZE);
                } else {
                    // Desenha círculo do agente com gradiente
                    Color baseColor = getAgentColor(agent.type);
                    RadialGradientPaint gradient = new RadialGradientPaint(
                            new Point(x + AGENT_SIZE / 2, y + AGENT_SIZE / 2),
                            AGENT_SIZE / 2,
                            new float[] { 0.0f, 1.0f },
                            new Color[] { baseColor, baseColor.darker().darker() });

                    g2d.setPaint(gradient);
                    g2d.fillOval(x, y, AGENT_SIZE, AGENT_SIZE);

                    // Desenha borda do agente
                    g2d.setColor(baseColor.darker());
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawOval(x, y, AGENT_SIZE, AGENT_SIZE);

                    // Desenha campo de visão para carnívoros e herbívoros
                    if (agent.type == AgentInfo.AgentType.CARNIVORE || agent.type == AgentInfo.AgentType.HERBIVORE) {
                        // Desenha raio de percepção
                        double SPATIAL_AWARENESS_RADIUS = agent.type == AgentInfo.AgentType.CARNIVORE ? 5.0 : 7.5;
                        int screenRadius = (int) (SPATIAL_AWARENESS_RADIUS / 200.0 * getWidth());
                        int centerX = x + AGENT_SIZE / 2;
                        int centerY = y + AGENT_SIZE / 2;

                        // Desenha círculo de percepção com transparência
                        Color awarenessColor = agent.type == AgentInfo.AgentType.CARNIVORE ? new Color(255, 0, 0, 15) : // Vermelho
                                                                                                                        // transparente
                                                                                                                        // para
                                                                                                                        // carnívoros
                                new Color(0, 0, 255, 15); // Azul transparente para herbívoros
                        g2d.setColor(awarenessColor);
                        g2d.fillOval(centerX - screenRadius, centerY - screenRadius,
                                screenRadius * 2, screenRadius * 2);

                        // Desenha contorno do círculo de percepção
                        Color outlineColor = agent.type == AgentInfo.AgentType.CARNIVORE ? new Color(255, 0, 0, 50) : // Vermelho
                                                                                                                      // semi-transparente
                                                                                                                      // para
                                                                                                                      // carnívoros
                                new Color(0, 0, 255, 50); // Azul semi-transparente para herbívoros
                        g2d.setColor(outlineColor);
                        g2d.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
                                0, new float[] { 5 }, 0)); // Linha pontilhada
                        g2d.drawOval(centerX - screenRadius, centerY - screenRadius,
                                screenRadius * 2, screenRadius * 2);

                        // Configuração do campo de visão
                        double FOV_ANGLE = agent.type == AgentInfo.AgentType.CARNIVORE ? Math.PI / 1.5 : // 120 graus
                                                                                                         // para
                                                                                                         // carnívoros
                                Math.PI / 2; // 90 graus para herbívoros
                        double FOV_RANGE = agent.type == AgentInfo.AgentType.CARNIVORE ? 17.5 : 10.0;

                        // Converte alcance do campo de visão para coordenadas da tela
                        int fovRange = (int) (FOV_RANGE / 90.0 * getWidth());

                        // Calcula pontos do cone de visão
                        double leftAngle = agent.facingDirection - FOV_ANGLE / 2;
                        double rightAngle = agent.facingDirection + FOV_ANGLE / 2;

                        int[] xPoints = new int[3];
                        int[] yPoints = new int[3];

                        // Ponto central do cone
                        xPoints[0] = centerX;
                        yPoints[0] = centerY;

                        // Ponto esquerdo do cone
                        xPoints[1] = centerX + (int) (Math.cos(leftAngle) * fovRange);
                        yPoints[1] = centerY + (int) (Math.sin(leftAngle) * fovRange);

                        // Ponto direito do cone
                        xPoints[2] = centerX + (int) (Math.cos(rightAngle) * fovRange);
                        yPoints[2] = centerY + (int) (Math.sin(rightAngle) * fovRange);

                        // Desenha área do campo de visão
                        Color fovColor = agent.type == AgentInfo.AgentType.CARNIVORE ? new Color(255, 0, 0, 30) : // Vermelho
                                                                                                                  // transparente
                                                                                                                  // para
                                                                                                                  // carnívoros
                                new Color(0, 0, 255, 30); // Azul transparente para herbívoros
                        g2d.setColor(fovColor);
                        g2d.fillPolygon(xPoints, yPoints, 3);

                        // Desenha contorno do campo de visão
                        g2d.setColor(agent.type == AgentInfo.AgentType.CARNIVORE ? new Color(255, 0, 0, 100) : // Vermelho
                                                                                                               // mais
                                                                                                               // opaco
                                                                                                               // para
                                                                                                               // carnívoros
                                new Color(0, 0, 255, 100)); // Azul mais opaco para herbívoros
                        g2d.setStroke(new BasicStroke(1.0f));
                        g2d.drawPolygon(xPoints, yPoints, 3);

                        // Desenha indicador de direção do agente
                        g2d.setColor(agent.type == AgentInfo.AgentType.CARNIVORE ? Color.RED : Color.BLUE);
                        g2d.setStroke(new BasicStroke(2.0f));
                        int directionLength = AGENT_SIZE / 2;
                        int dirX = centerX + (int) (Math.cos(agent.facingDirection) * directionLength);
                        int dirY = centerY + (int) (Math.sin(agent.facingDirection) * directionLength);
                        g2d.drawLine(centerX, centerY, dirX, dirY);
                    }

                    // Desenha barra de energia
                    int barX = x + AGENT_SIZE / 2 - ENERGY_BAR_WIDTH / 2;
                    int barY = y + AGENT_SIZE + 5;
                    drawEnergyBar(g2d, barX, barY, agent.energy);
                }

                // Desenha identificação do agente
                g2d.setFont(LABEL_FONT);
                FontMetrics fm = g2d.getFontMetrics();
                String label = agent.name;
                int labelWidth = fm.stringWidth(label);
                g2d.setColor(Color.BLACK);
                g2d.drawString(label, x + AGENT_SIZE / 2 - labelWidth / 2, y - 5);
            }
        }

        private Color getAgentColor(AgentInfo.AgentType type) {
            switch (type) {
                case PLANT:
                    return new Color(0, 150, 0); // Verde para plantas
                case HERBIVORE:
                    return new Color(0, 100, 255); // Azul para herbívoros
                case CARNIVORE:
                    return new Color(255, 50, 50); // Vermelho para carnívoros
                default:
                    return Color.GRAY; // Cinza para outros tipos
            }
        }

        private void drawEnergyBar(Graphics2D g2d, int x, int y, int energy) {
            // Desenha fundo da barra de energia
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x, y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT);

            // Desenha nível atual de energia
            Color energyColor = energy > 50 ? Color.GREEN : energy > 25 ? Color.YELLOW : Color.RED;
            g2d.setColor(energyColor);
            int energyWidth = (int) ((energy / 100.0) * ENERGY_BAR_WIDTH);
            g2d.fillRect(x, y, energyWidth, ENERGY_BAR_HEIGHT);
        }
    }
}