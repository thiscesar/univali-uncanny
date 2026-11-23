package ag;

/** Quem quiser acompanhar a evolução (uma tela, um log...) implementa isto. */
@FunctionalInterface
public interface OuvinteGeracao<T> {
    void aoFimDaGeracao(int geracao, T melhor, double fitness);
}
