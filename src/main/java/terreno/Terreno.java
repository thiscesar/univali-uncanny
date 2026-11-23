package terreno;

/**
 * Um mapa de alturas (heightmap): uma grade onde cada célula tem uma altura entre 0 e 1.
 * É o indivíduo do problema de terreno. Imutável.
 */
public final class Terreno {
    public static final float NIVEL_AGUA = 0.35f;
    public static final float NIVEL_MONTANHA = 0.75f;

    private final int largura;
    private final int comprimento;
    private final float[] alturas; // linha por linha: índice = y * largura + x

    public Terreno(int largura, int comprimento, float[] alturas) {
        if (alturas.length != largura * comprimento) {
            throw new IllegalArgumentException("Quantidade de alturas não bate com o tamanho");
        }
        this.largura = largura;
        this.comprimento = comprimento;
        this.alturas = alturas.clone();
    }

    public int largura() { return largura; }
    public int comprimento() { return comprimento; }

    public float alturaEm(int x, int y) {
        return alturas[y * largura + x];
    }

    /** Uma cópia das alturas, para quem quiser criar um terreno modificado. */
    public float[] copiarAlturas() {
        return alturas.clone();
    }

    // ---------- estatísticas, usadas pelo fitness e pela tela ----------

    public double fracaoAbaixo(float nivel) {
        int conta = 0;
        for (float a : alturas) {
            if (a < nivel) conta++;
        }
        return (double) conta / alturas.length;
    }

    public double fracaoAcima(float nivel) {
        int conta = 0;
        for (float a : alturas) {
            if (a > nivel) conta++;
        }
        return (double) conta / alturas.length;
    }

    /**
     * Fração do mapa ocupada pela maior região contínua abaixo do nível (o maior lago).
     * Busca em largura (flood fill) com vizinhança de 4 direções.
     */
    public double maiorRegiaoAbaixo(float nivel) {
        boolean[] visitado = new boolean[alturas.length];
        int[] fila = new int[alturas.length];
        int maior = 0;
        for (int inicio = 0; inicio < alturas.length; inicio++) {
            if (visitado[inicio] || alturas[inicio] >= nivel) continue;
            int frente = 0, fim = 0, tamanho = 0;
            fila[fim++] = inicio;
            visitado[inicio] = true;
            while (frente < fim) {
                int p = fila[frente++];
                tamanho++;
                int x = p % largura, y = p / largura;
                int[] vizinhos = { x > 0 ? p - 1 : -1, x < largura - 1 ? p + 1 : -1,
                        y > 0 ? p - largura : -1, y < comprimento - 1 ? p + largura : -1 };
                for (int v : vizinhos) {
                    if (v >= 0 && !visitado[v] && alturas[v] < nivel) {
                        visitado[v] = true;
                        fila[fim++] = v;
                    }
                }
            }
            maior = Math.max(maior, tamanho);
        }
        return (double) maior / alturas.length;
    }

    /** Diferença média de altura entre vizinhos: quanto maior, mais "acidentado". */
    public double rugosidade() {
        double soma = 0;
        int conta = 0;
        for (int y = 0; y < comprimento; y++) {
            for (int x = 0; x < largura; x++) {
                float a = alturaEm(x, y);
                if (x + 1 < largura) { soma += Math.abs(a - alturaEm(x + 1, y)); conta++; }
                if (y + 1 < comprimento) { soma += Math.abs(a - alturaEm(x, y + 1)); conta++; }
            }
        }
        return soma / conta;
    }
}
