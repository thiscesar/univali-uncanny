package visual;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Janela genérica que mostra a evolução de qualquer problema.
 * O que desenhar no meio vem do Desenho<T>; a janela cuida do resto:
 * texto da geração, gráfico do fitness e botões.
 */
public class Visualizador<T> extends JPanel {
    private static final int MARGEM = 30;
    private static final int ALTURA_TEXTO = 40;
    private static final int ALTURA_GRAFICO = 150;

    static final Color FUNDO = new Color(24, 24, 32);
    private static final Color COR_GRAFICO = new Color(120, 220, 140);
    private static final Color COR_TEXTO = new Color(230, 230, 240);
    private static final Color COR_BORDA = new Color(60, 60, 75);

    private final Desenho<T> desenho;
    private final int pausaMs;
    private final Controle controle = new Controle();
    private final List<Double> historico = new ArrayList<>();
    private T melhor;
    private int geracao;

    private Visualizador(Desenho<T> desenho, int pausaMs) {
        this.desenho = desenho;
        this.pausaMs = pausaMs;
        setPreferredSize(new Dimension(800, 900));
        setBackground(FUNDO);
    }

    public static <T> Visualizador<T> abrir(String titulo, int pausaMs, Desenho<T> desenho) {
        Visualizador<T> visualizador = new Visualizador<>(desenho, pausaMs);
        SwingUtilities.invokeLater(() -> {
            JFrame janela = new JFrame(titulo);
            janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            janela.setLayout(new BorderLayout());
            janela.add(visualizador, BorderLayout.CENTER);
            janela.add(visualizador.criarBotoes(), BorderLayout.SOUTH);
            janela.pack();
            janela.setLocationRelativeTo(null);
            janela.setVisible(true);
        });
        return visualizador;
    }

    /** Mesma assinatura do OuvinteGeracao, para poder usar tela::atualizar. */
    public void atualizar(int geracao, T melhor, double fitness) {
        SwingUtilities.invokeLater(() -> {
            this.geracao = geracao;
            this.melhor = melhor;
            this.historico.add(fitness);
            repaint();
        });
        pausar();
        controle.aguardarVez();
    }

    private void pausar() {
        try {
            Thread.sleep(pausaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JPanel criarBotoes() {
        JButton start = new JButton("Start");
        JButton parar = new JButton("Parar");
        JButton avancar = new JButton("Avançar");
        parar.setEnabled(false);

        start.addActionListener(e -> {
            controle.iniciar();
            start.setEnabled(false);
            parar.setEnabled(true);
            avancar.setEnabled(false);
        });
        parar.addActionListener(e -> {
            controle.parar();
            start.setEnabled(true);
            parar.setEnabled(false);
            avancar.setEnabled(true);
        });
        avancar.addActionListener(e -> controle.avancar());

        JPanel painel = new JPanel();
        painel.setBackground(FUNDO);
        painel.add(start);
        painel.add(parar);
        painel.add(avancar);
        return painel;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (melhor == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(COR_TEXTO);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        g2.drawString("Geração " + geracao + "    " + desenho.descrever(melhor), MARGEM, MARGEM + 10);

        int topo = MARGEM + ALTURA_TEXTO;
        int alturaArea = getHeight() - topo - ALTURA_GRAFICO - 2 * MARGEM;
        desenho.desenhar(g2, melhor, MARGEM, topo, getWidth() - 2 * MARGEM, alturaArea);

        desenharGrafico(g2);
    }

    private void desenharGrafico(Graphics2D g2) {
        int x0 = MARGEM;
        int y0 = getHeight() - ALTURA_GRAFICO - MARGEM;
        int largura = getWidth() - 2 * MARGEM;
        int altura = ALTURA_GRAFICO;

        g2.setStroke(new BasicStroke(1f));
        g2.setColor(COR_BORDA);
        g2.drawRect(x0, y0, largura, altura);
        g2.setColor(COR_TEXTO);
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g2.drawString("Fitness ao longo das gerações", x0 + 8, y0 + 16);

        int n = historico.size();
        if (n < 2) {
            return;
        }
        double min = Collections.min(historico);
        double max = Collections.max(historico);
        double faixa = Math.max(max - min, 1e-12);

        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = x0 + (int) ((double) i / (n - 1) * largura);
            ys[i] = y0 + altura - (int) ((historico.get(i) - min) / faixa * (altura - 25));
        }
        g2.setColor(COR_GRAFICO);
        g2.setStroke(new BasicStroke(2f));
        g2.drawPolyline(xs, ys, n);
    }
}
