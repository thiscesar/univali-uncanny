package fase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import ag.Problema;
import grafos.AEstrela;
import grafos.GradeCaminhada;
import jogo.Regras;
import mundo.ConsultaAltura;

/**
 * O problema que o AG da fase resolve: onde pôr início, Univali, provas e caixas para a
 * partida ter um bom percurso. O mesmo motor do AG do terreno resolve este problema: só
 * mudam o indivíduo (um Layout) e os quatro métodos abaixo.
 *
 * O fitness é uma soma ponderada de termos de 0 a 1 (pesos em Regras):
 *  - caminho do início à Univali longo (medido pelo A*, não em linha reta);
 *  - provas espalhadas (longe entre si, do início e da Univali);
 *  - progressão: uma prova perto, uma no meio, uma longe (em distância de caminho);
 *  - Univali longe da borda;
 *  - caixas de munição a 20–50 m de alguma prova.
 * Um layout com ponto inválido (fora da grade, na água, num pedaço isolado do mapa) leva uma
 * penalidade forte. Só dados (dá para testar).
 */
public class ProblemaLayout implements Problema<Layout> {

	private final GradeCaminhada grade;
	private final ConsultaAltura chao;
	/** Centros das células da maior componente: todo ponto sorteado sai daqui (já ligado ao resto). */
	private final List<Ponto> caminhaveis = new ArrayList<>();
	/** Lugares bons para o prédio: planos, longe da borda e na maior componente. */
	private final List<Ponto> lugaresUnivali = new ArrayList<>();
	/** Comprimentos de caminho já calculados, por par de células (o A* é a parte cara do fitness). */
	private final Map<Long, Float> cache = new HashMap<>();

	public ProblemaLayout(GradeCaminhada grade, ConsultaAltura chao) {
		this.grade = grade;
		this.chao = chao;
		int maior = maiorComponente();
		for (int j = 0; j < grade.lado; j++) {
			for (int i = 0; i < grade.lado; i++) {
				if (grade.componente(i, j) != maior) continue;
				Ponto p = new Ponto(grade.centro(i), grade.centro(j));
				caminhaveis.add(p);
				if (distanciaBorda(p) >= Regras.UNIVALI_BORDA && desnivelEmVolta(p, 15) < 1.5f) {
					lugaresUnivali.add(p);
				}
			}
		}
		if (lugaresUnivali.isEmpty()) lugaresUnivali.addAll(caminhaveis); // mapa sem lugar plano: o menos ruim
	}

	private int maiorComponente() {
		Map<Integer, Integer> tamanhos = new HashMap<>();
		for (int j = 0; j < grade.lado; j++) {
			for (int i = 0; i < grade.lado; i++) {
				if (grade.componente(i, j) >= 0) tamanhos.merge(grade.componente(i, j), 1, Integer::sum);
			}
		}
		return tamanhos.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(-1);
	}

	public boolean temLugar() {
		return !caminhaveis.isEmpty();
	}

	// ---------- os quatro métodos do AG ----------

	@Override
	public Layout criarAleatorio(Random random) {
		Ponto[] p = new Ponto[Layout.PONTOS];
		for (int i = 0; i < Layout.PONTOS; i++) {
			p[i] = sortear(i, random);
		}
		return Layout.deVetor(p);
	}

	/** Um ponto novo qualquer para o gene i (a Univali só em lugar bom para o prédio). */
	private Ponto sortear(int i, Random random) {
		List<Ponto> lista = i == 1 ? lugaresUnivali : caminhaveis;
		return lista.get(random.nextInt(lista.size()));
	}

	/** Cada ponto do filho vem de um dos pais, por sorteio. */
	@Override
	public Layout cruzar(Layout pai1, Layout pai2, Random random) {
		Ponto[] p = new Ponto[Layout.PONTOS];
		for (int i = 0; i < Layout.PONTOS; i++) {
			p[i] = random.nextBoolean() ? pai1.ponto(i) : pai2.ponto(i);
		}
		return Layout.deVetor(p);
	}

	/**
	 * Mexe num ponto: na maioria das vezes, um passo de 10 a 30 m numa direção qualquer (para o
	 * caminhável mais próximo); às vezes, um lugar novo em qualquer parte do mapa.
	 */
	@Override
	public Layout mutar(Layout layout, Random random) {
		int i = random.nextInt(Layout.PONTOS);
		if (random.nextDouble() < 0.3) {
			return layout.com(i, sortear(i, random));
		}
		Ponto atual = layout.ponto(i);
		float passo = 10 + random.nextFloat() * 20;
		double angulo = random.nextDouble() * 2 * Math.PI;
		Ponto alvo = new Ponto(atual.x() + passo * (float) Math.cos(angulo), atual.z() + passo * (float) Math.sin(angulo));
		return layout.com(i, maisProximo(i == 1 ? lugaresUnivali : caminhaveis, alvo));
	}

	private static Ponto maisProximo(List<Ponto> lista, Ponto alvo) {
		Ponto melhor = lista.get(0);
		float menor = Float.MAX_VALUE;
		for (Ponto p : lista) {
			float d = (p.x() - alvo.x()) * (p.x() - alvo.x()) + (p.z() - alvo.z()) * (p.z() - alvo.z());
			if (d < menor) {
				menor = d;
				melhor = p;
			}
		}
		return melhor;
	}

	@Override
	public double fitness(Layout l) {
		// penalidade forte: algum ponto fora do caminhável ou sem caminho até o início
		int validos = 0;
		for (int i = 0; i < Layout.PONTOS; i++) {
			Ponto p = l.ponto(i);
			if (grade.ligados(l.inicio().x(), l.inicio().z(), p.x(), p.z())) validos++;
		}
		if (validos < Layout.PONTOS) {
			return 0.1 * validos / Layout.PONTOS;
		}
		return Regras.PESO_CAMINHO_UNIVALI * termoCaminhoUnivali(l)
				+ Regras.PESO_PROVAS_ESPALHADAS * termoProvasEspalhadas(l)
				+ Regras.PESO_PROGRESSAO * termoProgressao(l)
				+ Regras.PESO_UNIVALI_BORDA * Math.min(1, distanciaBorda(l.univali()) / Regras.UNIVALI_BORDA)
				+ Regras.PESO_CAIXAS * termoCaixas(l);
	}

	// ---------- os termos do fitness, cada um de 0 a 1 ----------

	/** O caminho (A*) do início à Univali, em relação à meta de 180 m. */
	double termoCaminhoUnivali(Layout l) {
		return Math.min(1, caminho(l.inicio(), l.univali()) / Regras.META_CAMINHO_UNIVALI);
	}

	/** A menor das distâncias entre provas e início (meta 60 m) e entre provas e Univali (meta 40 m). */
	double termoProvasEspalhadas(Layout l) {
		double pior = 1;
		List<Ponto> pontos = new ArrayList<>(l.provas());
		pontos.add(l.inicio());
		for (int a = 0; a < pontos.size(); a++) {
			for (int b = a + 1; b < pontos.size(); b++) {
				pior = Math.min(pior, pontos.get(a).distancia(pontos.get(b)) / Regras.DISTANCIA_PROVAS);
			}
		}
		for (Ponto p : l.provas()) {
			pior = Math.min(pior, p.distancia(l.univali()) / Regras.DISTANCIA_PROVA_UNIVALI);
		}
		return pior;
	}

	/** Distâncias de caminho até as provas, em ordem, perto das metas (70, 130 e 190 m). */
	double termoProgressao(Layout l) {
		float[] d = new float[3];
		for (int i = 0; i < 3; i++) d[i] = caminho(l.inicio(), l.provas().get(i));
		java.util.Arrays.sort(d);
		double soma = 0;
		for (int i = 0; i < 3; i++) {
			float alvo = Regras.PROGRESSAO_PROVAS[i];
			soma += Math.max(0, 1 - Math.abs(d[i] - alvo) / alvo);
		}
		return soma / 3;
	}

	/** Cada caixa: perfeita entre 20 e 50 m de alguma prova; ruim em cima dela ou muito longe. */
	double termoCaixas(Layout l) {
		double soma = 0;
		for (Ponto c : l.caixas()) {
			float d = Float.MAX_VALUE;
			for (Ponto p : l.provas()) d = Math.min(d, c.distancia(p));
			if (d < 10) soma += 0;
			else if (d < Regras.CAIXA_MINIMA) soma += (d - 10) / (Regras.CAIXA_MINIMA - 10);
			else if (d <= Regras.CAIXA_MAXIMA) soma += 1;
			else soma += Math.max(0, 1 - (d - Regras.CAIXA_MAXIMA) / 50);
		}
		return soma / l.caixas().size();
	}

	/** Comprimento do caminho A* entre dois pontos, em metros (guardado no cache por par de células). */
	float caminho(Ponto a, Ponto b) {
		int ia = grade.celulaDe(a.x()), ja = grade.celulaDe(a.z()), ib = grade.celulaDe(b.x()), jb = grade.celulaDe(b.z());
		long ka = (long) ja * grade.lado + ia, kb = (long) jb * grade.lado + ib;
		long chave = Math.min(ka, kb) * grade.lado * grade.lado + Math.max(ka, kb);
		return cache.computeIfAbsent(chave, k -> {
			AEstrela.Caminho c = AEstrela.buscar(grade, ia, ja, ib, jb, Regras.CUSTO_INCLINACAO);
			return c == null ? 0f : c.comprimento();
		});
	}

	private float distanciaBorda(Ponto p) {
		float t = chao.tamanho();
		return Math.min(Math.min(p.x(), p.z()), Math.min(t - p.x(), t - p.z()));
	}

	private float desnivelEmVolta(Ponto p, float r) {
		float centro = chao.alturaEm(p.x(), p.z()), maior = 0;
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			maior = Math.max(maior, Math.abs(chao.alturaEm(p.x() + r * (float) Math.cos(a), p.z() + r * (float) Math.sin(a)) - centro));
		}
		return maior;
	}
}
