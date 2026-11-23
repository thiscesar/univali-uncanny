package terreno;

import java.util.Random;

/**
 * Ruído de Perlin 2D e suas variações fractais, usados para dar detalhe ao relevo.
 *
 * - perlin(): um "relevo ondulado" suave, valores entre -1 e 1;
 * - fbm(): soma várias camadas (oitavas) de Perlin, cada uma com o dobro da frequência
 *   e metade da força. As camadas grandes dão a forma, as pequenas dão a textura;
 * - cristas(): versão "ridged", que inverte o valor absoluto do Perlin e cria linhas
 *   afiadas, parecidas com cumes de montanha. Retorna valores entre 0 e 1.
 */
public final class RuidoFractal {
    private final int[] perm = new int[512];

    public RuidoFractal(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) { // embaralha (Fisher-Yates!)
            int j = random.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    public double perlin(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = suavizar(xf);
        double v = suavizar(yf);

        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        double x1 = lerp(gradiente(aa, xf, yf), gradiente(ba, xf - 1, yf), u);
        double x2 = lerp(gradiente(ab, xf, yf - 1), gradiente(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    public double fbm(double x, double y, int oitavas) {
        double soma = 0, amplitude = 1, frequencia = 1, total = 0;
        for (int i = 0; i < oitavas; i++) {
            soma += perlin(x * frequencia, y * frequencia) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequencia *= 2.0;
        }
        return soma / total;
    }

    public double cristas(double x, double y, int oitavas) {
        double soma = 0, amplitude = 1, frequencia = 1, total = 0;
        for (int i = 0; i < oitavas; i++) {
            double r = 1.0 - Math.abs(perlin(x * frequencia, y * frequencia));
            soma += r * r * amplitude; // ao quadrado: deixa o cume mais afiado
            total += amplitude;
            amplitude *= 0.5;
            frequencia *= 2.0;
        }
        return soma / total;
    }

    private static double suavizar(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double gradiente(int hash, double x, double y) {
        switch (hash & 7) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }
}
