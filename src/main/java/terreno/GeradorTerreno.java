package terreno;

import ag.AlgoritmoGenetico;
import ag.Configuracao;

import java.util.Random;

/**
 * Ponte entre o algoritmo genético e o projeto de OpenGL.
 *
 * Roda o AG sem janela e devolve as alturas no mesmo formato que o ObjHTGsrtm
 * usa para o arquivo .hgt: int[linha][coluna], em metros, com a água em altura <= 0.
 */
public final class GeradorTerreno {
    private static final int TAMANHO_EVOLUCAO = 128;      // o AG evolui um mapa pequeno (rápido)...
    // ...e a altura 0..1 vira metros. Com 750, o NIVEL_MONTANHA (0.75) cai em 300 m,
    // exatamente onde o ObjHTGsrtm do professor começa a textura de pedra.
    private static final float METROS_POR_UNIDADE = 750f;

    private GeradorTerreno() {}

    /**
     * @param seed         mesma seed = mesmo terreno
     * @param tamanhoFinal tamanho da grade entregue ao OpenGL (ex.: 512 = 512 x 512 vértices)
     */
    public static int[][] gerar(long seed, int tamanhoFinal) {
        Random random = new Random(seed);

        ProblemaTerreno problema = new ProblemaTerreno(
                TAMANHO_EVOLUCAO,
                0.30,  // 30% de água
                0.10,  // 10% de montanha
                0.012  // rugosidade desejada
        );
        Configuracao config = new Configuracao(60, 300, 0.5, 3);
        AlgoritmoGenetico<Terreno> ag = new AlgoritmoGenetico<>(problema, config, random);

        Terreno melhor = ag.executar((geracao, terreno, fitness) -> {
            if (geracao % 50 == 0) {
                System.out.printf("AG terreno: geração %d, fitness %.4f%n", geracao, fitness);
            }
        });

        return paraMetros(melhor, tamanhoFinal, new RuidoFractal(seed));
    }

    /**
     * Converte as alturas 0..1 para metros (o nível da água vira 0 metro),
     * amplia a grade para tamanhoFinal com interpolação bilinear
     * e acrescenta detalhe fractal por cima da forma que o AG gerou.
     */
    public static int[][] paraMetros(Terreno t, int tamanhoFinal, RuidoFractal ruido) {
        int[][] metros = new int[tamanhoFinal][tamanhoFinal];
        float escalaX = (float) (t.largura() - 1) / (tamanhoFinal - 1);
        float escalaY = (float) (t.comprimento() - 1) / (tamanhoFinal - 1);

        for (int j = 0; j < tamanhoFinal; j++) {
            for (int i = 0; i < tamanhoFinal; i++) {
                float fx = i * escalaX;
                float fy = j * escalaY;
                int x0 = (int) fx;
                int y0 = (int) fy;
                int x1 = Math.min(x0 + 1, t.largura() - 1);
                int y1 = Math.min(y0 + 1, t.comprimento() - 1);
                float tx = fx - x0;
                float ty = fy - y0;

                float cima = t.alturaEm(x0, y0) * (1 - tx) + t.alturaEm(x1, y0) * tx;
                float baixo = t.alturaEm(x0, y1) * (1 - tx) + t.alturaEm(x1, y1) * tx;
                float a = cima * (1 - ty) + baixo * ty;

                double u = (double) i / tamanhoFinal;
                double v = (double) j / tamanhoFinal;
                double alturaFinal = a + detalhe(a, u, v, ruido);

                metros[j][i] = (int) Math.round((alturaFinal - Terreno.NIVEL_AGUA) * METROS_POR_UNIDADE);
            }
        }
        return metros;
    }

    /**
     * Quanto somar à altura de um ponto. A quantidade depende de onde ele está:
     * a costa fica suave (praias), as planícies ganham colinas leves
     * e as regiões altas ganham cristas afiadas de montanha.
     */
    private static double detalhe(float a, double u, double v, RuidoFractal ruido) {
        double terra = suave(Terreno.NIVEL_AGUA, Terreno.NIVEL_AGUA + 0.06, a);   // 0 na água, 1 em terra firme
        double montanha = suave(Terreno.NIVEL_AGUA + 0.10, Terreno.NIVEL_MONTANHA, a); // 0 na planície, 1 na montanha

        double colinas = ruido.fbm(u * 6, v * 6, 5) * 0.12 * terra;
        double cristas = (ruido.cristas(u * 4 + 17.3, v * 4 + 5.1, 5) - 0.35) * 0.6 * Math.pow(montanha, 1.5);
        double fundo = ruido.fbm(u * 8 + 31.7, v * 8, 3) * 0.02 * (1 - terra); // leve variação no fundo do mar

        return colinas + cristas + fundo;
    }

    private static double suave(double inicio, double fim, double x) {
        double t = Math.max(0, Math.min(1, (x - inicio) / (fim - inicio)));
        return t * t * (3 - 2 * t);
    }
}
