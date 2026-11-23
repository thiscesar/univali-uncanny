package ag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * O motor genérico: seleção, elitismo e o laço das gerações.
 * Não sabe nada sobre rotas, terrenos ou qualquer problema específico.
 * O "como cruzar" e "como mutar" vêm do Problema.
 */
public class AlgoritmoGenetico<T> {

    /** Guarda o indivíduo junto com o fitness, para calcular só uma vez. */
    private record Avaliado<I>(I individuo, double fitness) {}

    private final Problema<T> problema;
    private final Configuracao config;
    private final Random random;

    public AlgoritmoGenetico(Problema<T> problema, Configuracao config, Random random) {
        this.problema = problema;
        this.config = config;
        this.random = random;
    }

    /** Roda o algoritmo e devolve o melhor indivíduo encontrado. */
    public T executar(OuvinteGeracao<T> ouvinte) {
        List<Avaliado<T>> populacao = populacaoInicial();
        Avaliado<T> melhor = melhorDe(populacao);
        ouvinte.aoFimDaGeracao(0, melhor.individuo(), melhor.fitness());

        for (int g = 1; g <= config.geracoes(); g++) {
            populacao = proximaGeracao(populacao);
            melhor = melhorDe(populacao);
            ouvinte.aoFimDaGeracao(g, melhor.individuo(), melhor.fitness());
        }
        return melhor.individuo();
    }

    private Avaliado<T> avaliar(T individuo) {
        return new Avaliado<>(individuo, problema.fitness(individuo));
    }

    private List<Avaliado<T>> populacaoInicial() {
        List<Avaliado<T>> populacao = new ArrayList<>();
        for (int i = 0; i < config.tamanhoPopulacao(); i++) {
            populacao.add(avaliar(problema.criarAleatorio(random)));
        }
        return populacao;
    }

    private List<Avaliado<T>> proximaGeracao(List<Avaliado<T>> atual) {
        List<Avaliado<T>> nova = new ArrayList<>();
        nova.add(melhorDe(atual)); // elitismo: o melhor passa direto

        while (nova.size() < config.tamanhoPopulacao()) {
            T pai1 = torneio(atual).individuo();
            T pai2 = torneio(atual).individuo();
            T filho = problema.cruzar(pai1, pai2, random);
            if (random.nextDouble() < config.taxaMutacao()) {
                filho = problema.mutar(filho, random);
            }
            nova.add(avaliar(filho));
        }
        return nova;
    }

    private Avaliado<T> torneio(List<Avaliado<T>> populacao) {
        Avaliado<T> melhor = null;
        for (int i = 0; i < config.tamanhoTorneio(); i++) {
            Avaliado<T> candidato = populacao.get(random.nextInt(populacao.size()));
            if (melhor == null || candidato.fitness() > melhor.fitness()) {
                melhor = candidato;
            }
        }
        return melhor;
    }

    private Avaliado<T> melhorDe(List<Avaliado<T>> populacao) {
        return Collections.max(populacao, Comparator.comparingDouble(Avaliado::fitness));
    }
}
