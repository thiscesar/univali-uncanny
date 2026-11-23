package terreno;

import ag.AlgoritmoGenetico;
import ag.Configuracao;
import ag.OuvinteGeracao;

import java.util.Random;

/**
 * Ponte entre o algoritmo genético e o projeto de OpenGL.
 *
 * Roda o AG sem janela e devolve as alturas no mesmo formato que o ObjHTGsrtm
 * usa para o arquivo .hgt: int[linha][coluna], em metros, com a água em altura <= 0.
 */
public final class GeradorTerreno {
    /** Quantas gerações o AG roda (usado também para calcular o progresso do carregamento). */
    public static final int GERACOES = 300;

    private GeradorTerreno() {}

    /**
     * Terreno do perfil ILHA (o original).
     *
     * @param seed         mesma seed = mesmo terreno
     * @param tamanhoFinal tamanho da grade entregue ao OpenGL (ex.: 512 = 512 x 512 vértices)
     */
    public static int[][] gerar(long seed, int tamanhoFinal) {
        return gerar(seed, tamanhoFinal, (geracao, terreno, fitness) -> {});
    }

    /** Terreno do perfil ILHA, avisando o ouvinte ao fim de cada geração. */
    public static int[][] gerar(long seed, int tamanhoFinal, OuvinteGeracao<Terreno> ouvinte) {
        return gerar(PerfilTerreno.ILHA.comTamanho(tamanhoFinal), seed, ouvinte);
    }

    /**
     * Gera o terreno de um perfil. Devolve as alturas no formato que o ObjHTGsrtm usa:
     * int[linha][coluna], com a água em altura <= 0, na unidade do perfil (metros ou centímetros).
     *
     * Pode rodar fora da thread principal: não usa OpenGL. Para cancelar, o ouvinte pode
     * lançar uma exceção (por exemplo, CancellationException), que interrompe o AG.
     */
    public static int[][] gerar(PerfilTerreno perfil, long seed, OuvinteGeracao<Terreno> ouvinte) {
        Random random = new Random(seed); // criado aqui dentro: a mesma seed dá sempre o mesmo terreno

        ProblemaTerreno problema = new ProblemaTerreno(
                perfil.tamanhoEvolucao(), perfil.alvoAgua(), perfil.alvoMontanha(), perfil.alvoRugosidade(),
                perfil.maiorLago());
        Configuracao config = new Configuracao(60, GERACOES, 0.5, 3);
        AlgoritmoGenetico<Terreno> ag = new AlgoritmoGenetico<>(problema, config, random);

        Terreno melhor = ag.executar((geracao, terreno, fitness) -> {
            if (geracao % 50 == 0) {
                System.out.printf("AG terreno (%s): geração %d, fitness %.4f%n", perfil.nome(), geracao, fitness);
            }
            ouvinte.aoFimDaGeracao(geracao, terreno, fitness);
        });

        return paraAlturas(melhor, perfil, new RuidoFractal(seed));
    }

    /**
     * Converte as alturas 0..1 do AG para a unidade do perfil (o nível da água vira 0),
     * amplia a grade com interpolação bilinear, acrescenta detalhe fractal por cima da
     * forma que o AG gerou e, se o perfil pedir, aplaina a faixa da borda.
     */
    public static int[][] paraAlturas(Terreno t, PerfilTerreno perfil, RuidoFractal ruido) {
        int tamanhoFinal = perfil.tamanhoFinal();
        int[][] saida = new int[tamanhoFinal][tamanhoFinal];
        float escalaX = (float) (t.largura() - 1) / (tamanhoFinal - 1);
        float escalaY = (float) (t.comprimento() - 1) / (tamanhoFinal - 1);
        double alturaBorda = Terreno.NIVEL_AGUA + perfil.alturaBorda() / perfil.escalaVertical();

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
                double alturaFinal = a + detalhe(a, u, v, ruido, perfil);

                if (perfil.larguraBorda() > 0) {
                    // borda: plana na faixa externa, com uma rampa suave (o dobro da largura) para dentro
                    int distancia = Math.min(Math.min(i, j), Math.min(tamanhoFinal - 1 - i, tamanhoFinal - 1 - j));
                    double pesoBorda = 1 - suave(perfil.larguraBorda(), 3.0 * perfil.larguraBorda(), distancia);
                    alturaFinal = alturaFinal * (1 - pesoBorda) + alturaBorda * pesoBorda;
                }

                double metros = (alturaFinal - Terreno.NIVEL_AGUA) * perfil.escalaVertical();
                saida[j][i] = (int) Math.round(metros * perfil.unidadesPorMetro());
            }
        }
        return saida;
    }

    /**
     * Quanto somar à altura de um ponto. A quantidade depende de onde ele está:
     * a costa fica suave, as planícies ganham colinas e as regiões altas ganham cristas.
     */
    private static double detalhe(float a, double u, double v, RuidoFractal ruido, PerfilTerreno perfil) {
        double terra = suave(Terreno.NIVEL_AGUA, Terreno.NIVEL_AGUA + 0.06, a);   // 0 na água, 1 em terra firme
        double montanha = suave(Terreno.NIVEL_AGUA + 0.10, Terreno.NIVEL_MONTANHA, a); // 0 na planície, 1 na montanha

        double colinas = ruido.fbm(u * 6, v * 6, 5) * perfil.forcaColinas() * terra;
        double cristas = perfil.forcaCristas() == 0 ? 0
                : (ruido.cristas(u * 4 + 17.3, v * 4 + 5.1, 5) - 0.35) * perfil.forcaCristas() * Math.pow(montanha, 1.5);
        double fundo = ruido.fbm(u * 8 + 31.7, v * 8, 3) * 0.02 * (1 - terra); // leve variação no fundo da água

        return colinas + cristas + fundo;
    }

    private static double suave(double inicio, double fim, double x) {
        double t = Math.max(0, Math.min(1, (x - inicio) / (fim - inicio)));
        return t * t * (3 - 2 * t);
    }
}
