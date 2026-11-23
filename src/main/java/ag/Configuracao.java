package ag;

/** Os parâmetros do algoritmo genético, independentes do problema. */
public record Configuracao(
        int tamanhoPopulacao,
        int geracoes,
        double taxaMutacao,
        int tamanhoTorneio
) {}
