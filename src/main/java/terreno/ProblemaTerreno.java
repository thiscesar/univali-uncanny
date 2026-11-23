package terreno;

import ag.Problema;

import java.util.Arrays;
import java.util.Random;

/**
 * Ensina o algoritmo genético a lidar com terrenos.
 *
 * Indivíduo: um mapa de alturas.
 * Mutação: adiciona um morro (ou uma depressão) suave em algum lugar.
 * Cruzamento: pega uma região circular de um pai e mistura suavemente no outro.
 * Fitness: o quão perto o terreno está das características desejadas
 *          (porcentagem de água, de montanha e o quão acidentado ele é) e, se pedido,
 *          uma penalidade para lagos grandes demais (para a água vir em vários lagos pequenos).
 */
public class ProblemaTerreno implements Problema<Terreno> {
    private final int tamanho;
    private final double alvoAgua;
    private final double alvoMontanha;
    private final double alvoRugosidade;
    /** Maior fração do mapa que um único lago pode ocupar sem penalidade (1 = sem limite). */
    private final double maiorLago;

    public ProblemaTerreno(int tamanho, double alvoAgua, double alvoMontanha, double alvoRugosidade) {
        this(tamanho, alvoAgua, alvoMontanha, alvoRugosidade, 1.0);
    }

    public ProblemaTerreno(int tamanho, double alvoAgua, double alvoMontanha, double alvoRugosidade,
            double maiorLago) {
        this.tamanho = tamanho;
        this.alvoAgua = alvoAgua;
        this.alvoMontanha = alvoMontanha;
        this.alvoRugosidade = alvoRugosidade;
        this.maiorLago = maiorLago;
    }

    @Override
    public Terreno criarAleatorio(Random random) {
        float[] alturas = new float[tamanho * tamanho];
        Arrays.fill(alturas, 0.45f);
        int morros = 20 + random.nextInt(20);
        for (int i = 0; i < morros; i++) {
            adicionarMorro(alturas, random);
        }
        return new Terreno(tamanho, tamanho, alturas);
    }

    @Override
    public double fitness(Terreno t) {
        double erroAgua = Math.abs(t.fracaoAbaixo(Terreno.NIVEL_AGUA) - alvoAgua);
        double erroMontanha = Math.abs(t.fracaoAcima(Terreno.NIVEL_MONTANHA) - alvoMontanha);
        double erroRugosidade = Math.abs(t.rugosidade() - alvoRugosidade) / alvoRugosidade;

        double erro = erroAgua + erroMontanha + 0.2 * erroRugosidade;
        if (maiorLago < 1.0) {
            erro += 20 * Math.max(0, t.maiorRegiaoAbaixo(Terreno.NIVEL_AGUA) - maiorLago);
        }
        return 1.0 / (1.0 + 10 * erro); // erro zero = fitness 1
    }

    @Override
    public Terreno cruzar(Terreno pai1, Terreno pai2, Random random) {
        float[] filho = new float[tamanho * tamanho];
        float cx = random.nextFloat() * tamanho;
        float cy = random.nextFloat() * tamanho;
        float raio = tamanho * random.nextFloat(0.2f, 0.5f);
        float borda = raio * 0.3f; // transição suave, sem "costura" visível

        for (int y = 0; y < tamanho; y++) {
            for (int x = 0; x < tamanho; x++) {
                double d = Math.hypot(x - cx, y - cy);
                float peso = (float) (1 / (1 + Math.exp((d - raio) / borda))); // ~1 dentro, ~0 fora
                filho[y * tamanho + x] = peso * pai2.alturaEm(x, y) + (1 - peso) * pai1.alturaEm(x, y);
            }
        }
        return new Terreno(tamanho, tamanho, filho);
    }

    @Override
    public Terreno mutar(Terreno terreno, Random random) {
        float[] alturas = terreno.copiarAlturas();
        adicionarMorro(alturas, random);
        return new Terreno(tamanho, tamanho, alturas);
    }

    /** Soma uma "colina" em forma de sino (gaussiana). Amplitude negativa cria um vale. */
    private void adicionarMorro(float[] alturas, Random random) {
        float cx = random.nextFloat() * tamanho;
        float cy = random.nextFloat() * tamanho;
        float raio = tamanho * random.nextFloat(0.03f, 0.25f);
        float amplitude = random.nextFloat(-0.25f, 0.3f);
        double doisRaio2 = 2.0 * raio * raio;

        for (int y = 0; y < tamanho; y++) {
            for (int x = 0; x < tamanho; x++) {
                double d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                int i = y * tamanho + x;
                float nova = alturas[i] + (float) (amplitude * Math.exp(-d2 / doisRaio2));
                alturas[i] = Math.max(0f, Math.min(1f, nova));
            }
        }
    }
}
