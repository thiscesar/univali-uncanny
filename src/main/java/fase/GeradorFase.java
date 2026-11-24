package fase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ag.AlgoritmoGenetico;
import ag.Configuracao;
import ag.OuvinteGeracao;
import grafos.AEstrela;
import grafos.GradeCaminhada;
import jogo.Regras;
import mundo.ConsultaAltura;
import mundo.DistribuidorArvores;

/**
 * Gera a fase: o segundo algoritmo genético do jogo (o primeiro faz o terreno). Com o terreno
 * pronto, o AG procura o melhor Layout (ProblemaLayout); depois o A* refaz, numa grade mais fina
 * (2 m), os caminhos do início até cada prova e até a Univali. Esses caminhos viram as TRILHAS do
 * jogo: terra batida no chão, sem árvores. Para não ficar óbvio:
 *  - a trilha de cada prova termina uns 12 m antes dela (o resto é com o sussurro e o feixe);
 *  - há dois becos sem saída, saindo das trilhas para lugar nenhum.
 * As placas de trilha ficam onde o caminho de uma prova se separa dos outros (a bifurcação) e
 * perto do fim; as do evento, uma perto do início e duas na beira da trilha da Univali.
 *
 * Roda na thread de carregamento: só dados, nada de OpenGL.
 */
public final class GeradorFase {

	public static final int GERACOES = 60, POPULACAO = 40;
	/** Onde a trilha da prova termina, antes dela, em metros. */
	public static final float FIM_ANTES_DA_PROVA = 12f;

	/** A fase pronta. As trilhas são linhas (pontos a cada ~1 m); as clareiras vão para o distribuidor de árvores. */
	public record Fase(Layout layout, double fitness, List<List<Ponto>> trilhas, List<Placa> placas) {

		/** Lugares sem árvores: o início, as provas, a Univali, as caixas, as placas e as trilhas. */
		public List<DistribuidorArvores.Clareira> clareiras() {
			List<DistribuidorArvores.Clareira> c = new ArrayList<>();
			c.add(new DistribuidorArvores.Clareira(layout.inicio().x(), layout.inicio().z(), 6));
			c.add(new DistribuidorArvores.Clareira(layout.univali().x(), layout.univali().z(), 25));
			for (Ponto p : layout.provas()) c.add(new DistribuidorArvores.Clareira(p.x(), p.z(), 4));
			for (Ponto p : layout.caixas()) c.add(new DistribuidorArvores.Clareira(p.x(), p.z(), 1.5f));
			for (Placa p : placas) c.add(new DistribuidorArvores.Clareira(p.x(), p.z(), p.evento() ? 3f : 1.5f));
			for (List<Ponto> trilha : trilhas) {
				for (int i = 0; i < trilha.size(); i += 2) { // um círculo a cada ~2 m: um corredor
					c.add(new DistribuidorArvores.Clareira(trilha.get(i).x(), trilha.get(i).z(), 2.2f));
				}
			}
			return c;
		}
	}

	private GeradorFase() {}

	public static Fase gerar(ConsultaAltura chao, long seed, OuvinteGeracao<Layout> ouvinte) {
		GradeCaminhada grade = new GradeCaminhada(chao, 4);
		ProblemaLayout problema = new ProblemaLayout(grade, chao);
		Random random = new Random(seed * 7 + 11);
		AlgoritmoGenetico<Layout> ag = new AlgoritmoGenetico<>(problema, new Configuracao(POPULACAO, GERACOES, 0.6, 3), random);
		double[] melhorFitness = { 0 };
		Layout layout = ag.executar((g, melhor, fitness) -> {
			melhorFitness[0] = fitness;
			if (ouvinte != null) ouvinte.aoFimDaGeracao(g, melhor, fitness);
		});
		System.out.printf("AG da fase: fitness %.3f, Univali a %.0f m de caminho%n", melhorFitness[0],
				problema.caminho(layout.inicio(), layout.univali()));

		// a validação final, na grade de 2 m: um ponto pode ter caído num pedaço que, visto com
		// mais detalhe, ficou isolado; ele vai para a célula ligada ao início mais próxima
		GradeCaminhada fina = new GradeCaminhada(chao, 2);
		fina.adicionarIrregularidade(seed + 5, 3f); // trilhas com curvas (só nos caminhos finais)
		layout = ajustarNaGradeFina(fina, layout);
		List<List<Ponto>> caminhosProvas = new ArrayList<>();
		for (Ponto p : layout.provas()) {
			caminhosProvas.add(cortarFim(caminho(fina, layout.inicio(), p), FIM_ANTES_DA_PROVA));
		}
		List<Ponto> caminhoUnivali = caminho(fina, layout.inicio(), layout.univali());

		List<List<Ponto>> trilhas = new ArrayList<>(caminhosProvas);
		trilhas.add(caminhoUnivali);
		trilhas.addAll(becos(fina, caminhosProvas, random));
		trilhas.removeIf(t -> t.size() < 2);

		List<Placa> placas = placas(chao, layout, caminhosProvas, caminhoUnivali, random);
		return new Fase(layout, melhorFitness[0], trilhas, placas);
	}

	/** Cada ponto ligado ao início na grade fina (o início, na maior componente dela). */
	static Layout ajustarNaGradeFina(GradeCaminhada g, Layout l) {
		int maior = maiorComponente(g);
		Ponto inicio = l.inicio();
		if (g.componenteEm(inicio.x(), inicio.z()) != maior) {
			inicio = maisProximaNaComponente(g, inicio, maior);
		}
		int alvo = g.componenteEm(inicio.x(), inicio.z());
		Ponto[] p = new Ponto[Layout.PONTOS];
		p[0] = inicio;
		for (int i = 1; i < Layout.PONTOS; i++) {
			Ponto q = l.ponto(i);
			p[i] = g.componenteEm(q.x(), q.z()) == alvo ? q : maisProximaNaComponente(g, q, alvo);
		}
		return Layout.deVetor(p);
	}

	private static int maiorComponente(GradeCaminhada g) {
		java.util.Map<Integer, Integer> tamanhos = new java.util.HashMap<>();
		for (int j = 0; j < g.lado; j++) {
			for (int i = 0; i < g.lado; i++) {
				if (g.componente(i, j) >= 0) tamanhos.merge(g.componente(i, j), 1, Integer::sum);
			}
		}
		return tamanhos.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
				.orElse(-1);
	}

	/** O centro da célula da componente dada mais perto do ponto (procura em anéis cada vez maiores). */
	private static Ponto maisProximaNaComponente(GradeCaminhada g, Ponto p, int componente) {
		int ci = g.celulaDe(p.x()), cj = g.celulaDe(p.z());
		for (int raio = 1; raio < g.lado; raio++) {
			Ponto melhor = null;
			float menor = Float.MAX_VALUE;
			for (int j = cj - raio; j <= cj + raio; j++) {
				for (int i = ci - raio; i <= ci + raio; i++) {
					if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != raio || g.componente(i, j) != componente) continue;
					Ponto q = new Ponto(g.centro(i), g.centro(j));
					if (q.distancia(p) < menor) {
						menor = q.distancia(p);
						melhor = q;
					}
				}
			}
			if (melhor != null) return melhor;
		}
		return p;
	}

	// ---------- caminhos ----------

	/** O caminho A* entre dois pontos, como pontos no chão, suavizado. Vazio se não houver. */
	static List<Ponto> caminho(GradeCaminhada g, Ponto a, Ponto b) {
		AEstrela.Caminho c = AEstrela.buscar(g, a.x(), a.z(), b.x(), b.z(), Regras.CUSTO_INCLINACAO);
		if (c == null) return List.of();
		List<Ponto> pontos = new ArrayList<>();
		for (int[] cel : c.celulas()) pontos.add(new Ponto(g.centro(cel[0]), g.centro(cel[1])));
		return serpentear(suavizar(suavizar(pontos)), 1.4f, 24f);
	}

	/**
	 * Um leve vai e vem para os lados ao longo da trilha (uma senoide de "amplitude" metros a cada
	 * "onda" metros andados), mais fraco perto das pontas: tira o jeito de régua dos trechos retos.
	 */
	static List<Ponto> serpentear(List<Ponto> linha, float amplitude, float onda) {
		if (linha.size() < 3) return linha;
		float total = comprimento(linha), andado = 0;
		List<Ponto> s = new ArrayList<>();
		for (int i = 0; i < linha.size(); i++) {
			if (i > 0) andado += linha.get(i).distancia(linha.get(i - 1));
			Ponto a = linha.get(Math.max(0, i - 1)), b = linha.get(Math.min(linha.size() - 1, i + 1));
			float dx = b.x() - a.x(), dz = b.z() - a.z(), n = (float) Math.hypot(dx, dz);
			float pontas = Math.min(1, Math.min(andado, total - andado) / 8); // 0 nas pontas, 1 a 8 m delas
			float lado = n < 1e-4f ? 0 : amplitude * pontas * (float) Math.sin(andado / onda * 2 * Math.PI);
			Ponto p = linha.get(i);
			s.add(n < 1e-4f ? p : new Ponto(p.x() - dz / n * lado, p.z() + dx / n * lado));
		}
		return s;
	}

	/**
	 * Suavização de Chaikin: cada segmento vira dois pontos, a 1/4 e a 3/4 dele. Os "degraus" da
	 * grade somem e a trilha fica com curvas, sem sair do lugar (as pontas ficam fixas).
	 */
	static List<Ponto> suavizar(List<Ponto> p) {
		if (p.size() < 3) return p;
		List<Ponto> s = new ArrayList<>();
		s.add(p.get(0));
		for (int i = 0; i < p.size() - 1; i++) {
			Ponto a = p.get(i), b = p.get(i + 1);
			s.add(new Ponto(0.75f * a.x() + 0.25f * b.x(), 0.75f * a.z() + 0.25f * b.z()));
			s.add(new Ponto(0.25f * a.x() + 0.75f * b.x(), 0.25f * a.z() + 0.75f * b.z()));
		}
		s.add(p.get(p.size() - 1));
		return s;
	}

	/** Tira os últimos "metros" da linha. */
	static List<Ponto> cortarFim(List<Ponto> linha, float metros) {
		float total = 0;
		for (int i = linha.size() - 1; i > 0; i--) {
			total += linha.get(i).distancia(linha.get(i - 1));
			if (total >= metros) return new ArrayList<>(linha.subList(0, i));
		}
		return linha.isEmpty() ? linha : new ArrayList<>(linha.subList(0, 1));
	}

	/** Dois becos sem saída: de um ponto no meio de uma trilha de prova até um lugar qualquer a 35–60 m. */
	private static List<List<Ponto>> becos(GradeCaminhada g, List<List<Ponto>> trilhas, Random random) {
		List<List<Ponto>> becos = new ArrayList<>();
		for (int tentativa = 0; tentativa < 40 && becos.size() < 2; tentativa++) {
			List<Ponto> base = trilhas.get(random.nextInt(trilhas.size()));
			if (base.size() < 10) continue;
			Ponto saida = base.get(base.size() / 5 + random.nextInt(base.size() * 2 / 5));
			double angulo = random.nextDouble() * 2 * Math.PI;
			float d = 35 + random.nextFloat() * 25;
			Ponto fim = new Ponto(saida.x() + d * (float) Math.cos(angulo), saida.z() + d * (float) Math.sin(angulo));
			if (!g.ligados(saida.x(), saida.z(), fim.x(), fim.z())) continue;
			List<Ponto> beco = caminho(g, saida, fim);
			if (beco.size() > 4 && comprimento(beco) < 90) becos.add(beco);
		}
		return becos;
	}

	static float comprimento(List<Ponto> linha) {
		float total = 0;
		for (int i = 1; i < linha.size(); i++) total += linha.get(i).distancia(linha.get(i - 1));
		return total;
	}

	// ---------- placas ----------

	/**
	 * Placas de trilha: onde o caminho de cada prova se separa dos outros (se for a mais de 15 m do
	 * início) e uns 30 m antes do fim, ao lado da trilha, com a seta apontando para onde ela segue.
	 * Placas do evento: uma a 10 m do início, virada para ele, e duas na beira da trilha da Univali.
	 */
	static List<Placa> placas(ConsultaAltura chao, Layout layout, List<List<Ponto>> provas, List<Ponto> univali,
			Random random) {
		List<Placa> placas = new ArrayList<>();
		Ponto inicio = layout.inicio();
		for (int tentativa = 0; tentativa < 100; tentativa++) {
			double a = random.nextDouble() * 2 * Math.PI;
			Ponto p = new Ponto(inicio.x() + 10 * (float) Math.cos(a), inicio.z() + 10 * (float) Math.sin(a));
			if (bomParaPlaca(chao, p) && longeDasTrilhas(p, provas, univali, 3)) {
				placas.add(new Placa(true, p.x(), p.z(), (float) Math.atan2(inicio.x() - p.x(), inicio.z() - p.z()), "evento"));
				break;
			}
		}
		for (float fracao : new float[] { 0.35f, 0.7f }) {
			if (univali.size() < 10) break;
			int i = Math.round((univali.size() - 1) * fracao);
			Ponto naTrilha = univali.get(i);
			Ponto p = aoLado(univali, i, 3f);
			if (bomParaPlaca(chao, p)) {
				// a frente (+z do modelo) olha para a trilha
				placas.add(new Placa(true, p.x(), p.z(), (float) Math.atan2(naTrilha.x() - p.x(), naTrilha.z() - p.z()), "evento"));
			}
		}
		for (int k = 0; k < provas.size(); k++) {
			List<Ponto> t = provas.get(k);
			if (t.size() < 6) continue;
			String nome = "M" + (k + 1);
			List<List<Ponto>> outras = new ArrayList<>(provas);
			outras.remove(k);
			outras.add(univali);
			int bifurcacao = -1;
			float andado = 0;
			for (int i = 1; i < t.size(); i++) {
				andado += t.get(i).distancia(t.get(i - 1));
				if (andado > 15 && longeDasTrilhas(t.get(i), outras, List.of(), 4)) {
					bifurcacao = i;
					break;
				}
			}
			if (bifurcacao > 0 && comprimento(t.subList(bifurcacao, t.size())) > 20) {
				adicionarPlacaTrilha(placas, chao, t, bifurcacao, nome);
			}
			int antesDoFim = indiceAntesDoFim(t, 30);
			// a segunda placa só se ficar a mais de 15 m depois da primeira
			if (antesDoFim > 0 && (bifurcacao < 0 || (antesDoFim > bifurcacao && comprimento(t.subList(bifurcacao, antesDoFim)) > 15))) {
				adicionarPlacaTrilha(placas, chao, t, antesDoFim, nome);
			}
		}
		return placas;
	}

	/** Placa de trilha ao lado do ponto i, com a seta (o +x do modelo) na direção em que a trilha segue. */
	private static void adicionarPlacaTrilha(List<Placa> placas, ConsultaAltura chao, List<Ponto> t, int i, String nome) {
		Ponto p = aoLado(t, i, 1.8f);
		Ponto frente = t.get(Math.min(t.size() - 1, i + 4));
		float dx = frente.x() - t.get(i).x(), dz = frente.z() - t.get(i).z();
		if (bomParaPlaca(chao, p) && (dx != 0 || dz != 0)) {
			placas.add(new Placa(false, p.x(), p.z(), (float) Math.atan2(-dz, dx), nome));
		}
	}

	/** Um ponto a "distancia" metros à direita da trilha, no ponto i. */
	private static Ponto aoLado(List<Ponto> t, int i, float distancia) {
		Ponto a = t.get(Math.max(0, i - 1)), b = t.get(Math.min(t.size() - 1, i + 1));
		float dx = b.x() - a.x(), dz = b.z() - a.z(), n = (float) Math.hypot(dx, dz);
		if (n < 1e-4f) return t.get(i);
		return new Ponto(t.get(i).x() - dz / n * distancia, t.get(i).z() + dx / n * distancia);
	}

	private static int indiceAntesDoFim(List<Ponto> t, float metros) {
		float total = 0;
		for (int i = t.size() - 1; i > 0; i--) {
			total += t.get(i).distancia(t.get(i - 1));
			if (total >= metros) return i;
		}
		return -1;
	}

	private static boolean longeDasTrilhas(Ponto p, List<List<Ponto>> trilhas, List<Ponto> mais, float distancia) {
		for (List<Ponto> t : trilhas) {
			for (Ponto q : t) if (q.distancia(p) < distancia) return false;
		}
		for (Ponto q : mais) if (q.distancia(p) < distancia) return false;
		return true;
	}

	private static boolean bomParaPlaca(ConsultaAltura chao, Ponto p) {
		return chao.dentroDoMapa(p.x(), p.z(), 15) && chao.alturaEm(p.x(), p.z()) > 0.2f && chao.anguloEm(p.x(), p.z()) < 25;
	}
}
