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

        // Adiciona estatísticas da população
        JPanel statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Estatísticas da População"));

        herbivoreStatsLabel = new JLabel("<html><b>Herbívoros</b><br>Vivos: 0<br>Total de Mortes: 0</html>");
        carnivoreStatsLabel = new JLabel("<html><b>Carnívoros</b><br>Vivos: 0<br>Total de Mortes: 0</html>");

        statsPanel.add(herbivoreStatsLabel);
        statsPanel.add(Box.createVerticalStrut(10));
        statsPanel.add(carnivoreStatsLabel);

        controlPanel.add(statsPanel);
        controlPanel.add(Box.createVerticalStrut(20));

        // Adiciona itens da legenda
        JPanel legendPanel = new JPanel();
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.setBorder(BorderFactory.createTitledBorder("Legenda"));

        addLegendItem(legendPanel, "Plantas", new Color(34, 139, 34), "Estáticas, geram energia");
        addLegendItem(legendPanel, "Herbívoros", new Color(30, 144, 255), "Caçam plantas");
        addLegendItem(legendPanel, "Carnívoros", new Color(220, 20, 60), "Caçam herbívoros");

        controlPanel.add(legendPanel);
    }

    private void addLegendItem(JPanel panel, String name, Color color, String description) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Quadrado colorido
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

    public void updateAgentPositions(List<AgentInfo> agents) {
        simulationPanel.setAgents(agents);

        // Conta populações atuais e novas mortes
        int currentHerbivores = 0;
        int currentCarnivores = 0;

        for (AgentInfo agent : agents) {
            if (agent.type == AgentInfo.AgentType.HERBIVORE) {
                if (agent.energy > 0) {
                    currentHerbivores++;
                } else if (!deadHerbivores.contains(agent.name)) {
                    totalHerbivoreDeaths++;
                    deadHerbivores.add(agent.name);
                }
            } else if (agent.type == AgentInfo.AgentType.CARNIVORE) {
                if (agent.energy > 0) {
                    currentCarnivores++;
                } else if (!deadCarnivores.contains(agent.name)) {
                    totalCarnivoreDeaths++;
                    deadCarnivores.add(agent.name);
                }
            }
        }

        // Atualiza rótulos
        herbivoreStatsLabel.setText(String.format("<html><b>Herbívoros</b><br>Vivos: %d<br>Total de Mortes: %d</html>",
                currentHerbivores, totalHerbivoreDeaths));
        carnivoreStatsLabel.setText(String.format("<html><b>Carnívoros</b><br>Vivos: %d<br>Total de Mortes: %d</html>",
                currentCarnivores, totalCarnivoreDeaths));

        simulationPanel.repaint();
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

            // Ativa antialiasing
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Desenha grade
            g2d.setColor(new Color(230, 230, 230));
            for (int x = 0; x < getWidth(); x += gridSize) {
                g2d.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += gridSize) {
                g2d.drawLine(0, y, getWidth(), y);
            }

            // Desenha agentes
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

                    // Desenha borda
                    g2d.setColor(baseColor.darker());
                    g2d.setStroke(new BasicStroke(2));
                    g2d.drawOval(x, y, AGENT_SIZE, AGENT_SIZE);

                    // Desenha barra de energia
                    int barX = x + AGENT_SIZE / 2 - ENERGY_BAR_WIDTH / 2;
                    int barY = y + AGENT_SIZE + 5;
                    
                }

                // Desenha nome do agente
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
                    return new Color(0, 150, 0); // Verde mais brilhante
                case HERBIVORE:
                    return new Color(0, 100, 255); // Azul mais brilhante
                case CARNIVORE:
                    return new Color(255, 50, 50); // Vermelho mais brilhante
                default:
                    return Color.GRAY;
            }
        }
    
    }
}