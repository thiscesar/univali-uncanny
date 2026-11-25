package ag;

import java.util.Random;

/**
 * O "contrato" que qualquer problema precisa cumprir para ser resolvido pelo algoritmo genético.
 * O algoritmo não sabe o que é T (uma rota, um terreno...): ele só usa estes quatro métodos.
 *
 * @param <T> o tipo do indivíduo
 */
public interface Problema<T> {

    /** Cria um indivíduo aleatório para a população inicial. */
    T criarAleatorio(Random random);

    /** Quanto maior, melhor o indivíduo. */
    double fitness(T individuo);

    /** Combina dois pais e gera um filho. */
    T cruzar(T pai1, T pai2, Random random);

    /** Devolve uma versão levemente modificada do indivíduo. */
    T mutar(T individuo, Random random);

    /**
     * O fitness pode ser calculado em várias threads ao mesmo tempo? Só se ele não sortear nada
     * nem mexer em estado compartilhado. Por padrão, não (o da fase guarda caminhos num cache).
     */
    default boolean avaliarEmParalelo() {
        return false;
    }
}
