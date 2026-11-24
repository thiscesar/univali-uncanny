package fase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A planta do térreo da Univali (48 x 28 m): um corredor principal de 4 m de ponta a ponta, o
 * saguão ligando a porta de entrada ao corredor, e 12 salas, cada uma com a sua porta para o
 * corredor: 6 na frente (3 de cada lado do saguão) e 6 nos fundos.
 *
 * A Sala dos Professores fica num dos cantos dos fundos (o fim do corredor, sorteado pela seed).
 * As outras salas recebem um tipo sorteado (laboratório, banheiros, coordenação e salas de aula
 * numeradas), o crachá fica numa delas, duas ficam trancadas para sempre, e o jogador começa numa
 * sala de aula da frente, do lado oposto ao dos professores.
 *
 * Coordenadas do modelo do prédio: x de -24 a 24 (da esquerda para a direita de quem olha a
 * fachada), z de -14 (fundos) a 14 (frente, a porta). Só dados (dá para testar).
 */
public class AndarUnivali {

	public static final float LARGURA = 48f, PROFUNDIDADE = 28f;
	public static final float X0 = -LARGURA / 2, Z0 = -PROFUNDIDADE / 2;
	/** O corredor vai de z = -2 a z = 2; o saguão, de x = -5 a x = 5, do corredor até a frente. */
	public static final float MEIO_CORREDOR = 2f, MEIO_SAGUAO = 5f;
	public static final float ESPESSURA = 0.2f, VAO_PORTA = 1.4f;
	/** A porta do prédio: um vão de 3 m no meio da parede da frente. */
	public static final float VAO_ENTRADA = 3f;

	public enum Tipo { AULA, LABORATORIO, BANHEIROS, COORDENACAO, PROFESSORES }

	/** Uma sala: o retângulo, de que lado do corredor ela fica (frente = z positivo) e se está trancada. */
	public record Sala(String nome, Tipo tipo, float xMin, float zMin, float xMax, float zMax, boolean naFrente,
			boolean trancada) {

		public float[] centro() {
			return new float[] { (xMin + xMax) / 2, (zMin + zMax) / 2 };
		}

		/** O meio do vão da porta (na parede do corredor). */
		public float[] porta() {
			return new float[] { (xMin + xMax) / 2, naFrente ? MEIO_CORREDOR : -MEIO_CORREDOR };
		}

		/** Para onde fica a porta, a partir do fundo da sala: {0, -1} na frente, {0, 1} nos fundos. */
		public float[] direcaoDaPorta() {
			return new float[] { 0, naFrente ? -1 : 1 };
		}

		/** O fundo da sala (a parede de fora, oposta à porta): z dela. */
		public float zFundo() {
			return naFrente ? zMax : zMin;
		}
	}

	/** Um retângulo no modelo do prédio (paredes, móveis, portas trancadas). */
	public record Caixa(float xMin, float zMin, float xMax, float zMax) {}

	/** Uma lâmpada do teto: posição no modelo e se está acesa, piscando ou queimada. */
	public record Lampada(float x, float z, Estado estado) {
		public enum Estado { ACESA, PISCANDO, QUEIMADA }
	}

	private final List<Lampada> lampadas = new ArrayList<>();

	private final List<Sala> salas = new ArrayList<>();
	private final Sala professores, cracha, inicio;
	/** As salas com porta fechada que abre sozinha: a do início (sempre a primeira) e mais três. */
	private final List<Sala> portasSozinhas = new ArrayList<>();

	public AndarUnivali(long seed) {
		Random random = new Random(seed * 3 + 1);
		boolean professoresADireita = random.nextBoolean();

		// os retângulos: 3 + 3 na frente (de cada lado do saguão), 6 nos fundos
		List<float[]> frente = new ArrayList<>(), fundos = new ArrayList<>();
		float larguraFrente = (LARGURA / 2 - MEIO_SAGUAO) / 3;
		for (int i = 0; i < 3; i++) {
			frente.add(new float[] { X0 + i * larguraFrente, X0 + (i + 1) * larguraFrente });
			frente.add(new float[] { MEIO_SAGUAO + i * larguraFrente, MEIO_SAGUAO + (i + 1) * larguraFrente });
		}
		frente.sort((a, b) -> Float.compare(a[0], b[0]));
		for (int i = 0; i < 6; i++) fundos.add(new float[] { X0 + i * 8, X0 + (i + 1) * 8 });

		// os tipos: os professores num canto dos fundos, o início numa sala de aula da frente (do
		// lado oposto), e as outras 10 com os tipos embaralhados
		int canto = professoresADireita ? 5 : 0;
		int salaInicio = professoresADireita ? 0 : 5;
		List<Tipo> tipos = new ArrayList<>(List.of(Tipo.LABORATORIO, Tipo.BANHEIROS, Tipo.COORDENACAO));
		while (tipos.size() < 10) tipos.add(Tipo.AULA);
		Collections.shuffle(tipos, random);
		int t = 0, numero = 101;
		Sala inicioEscolhido = null, professoresEscolhida = null;
		for (int i = 0; i < 12; i++) {
			boolean naFrente = i < 6;
			float[] f = naFrente ? frente.get(i) : fundos.get(i - 6);
			Tipo tipo = naFrente && i == salaInicio ? Tipo.AULA : !naFrente && i - 6 == canto ? Tipo.PROFESSORES : tipos.get(t++);
			Sala s = naFrente ? new Sala(nome(tipo, numero), tipo, f[0], MEIO_CORREDOR, f[1], -Z0, true, false)
					: new Sala(nome(tipo, numero), tipo, f[0], Z0, f[1], -MEIO_CORREDOR, false, tipo == Tipo.PROFESSORES);
			if (tipo == Tipo.AULA) numero++;
			salas.add(s);
			if (naFrente && i == salaInicio) inicioEscolhido = s;
			if (tipo == Tipo.PROFESSORES) professoresEscolhida = s;
		}
		inicio = inicioEscolhido;
		professores = professoresEscolhida;

		// duas salas trancadas para sempre (nem o início, nem os professores, nem os banheiros)
		List<Integer> trancaveis = new ArrayList<>();
		for (int i = 0; i < salas.size(); i++) {
			Sala s = salas.get(i);
			if (s != inicio && s != professores && s.tipo() != Tipo.BANHEIROS) trancaveis.add(i);
		}
		Collections.shuffle(trancaveis, random);
		for (int k = 0; k < 2; k++) {
			int i = trancaveis.get(k);
			Sala s = salas.get(i);
			salas.set(i, new Sala(s.nome(), s.tipo(), s.xMin(), s.zMin(), s.xMax(), s.zMax(), s.naFrente(), true));
		}
		// o crachá: numa sala aberta, que não seja a do início nem a dos professores
		List<Sala> candidatasCracha = new ArrayList<>();
		for (Sala s : salas) {
			if (!s.trancada() && s != inicio && s.tipo() != Tipo.BANHEIROS) candidatasCracha.add(s);
		}
		cracha = candidatasCracha.get(random.nextInt(candidatasCracha.size()));

		// as lâmpadas: uma a cada 4 m no corredor, duas no saguão, duas em cada sala. Metade acesa,
		// umas piscando, o resto queimada; no corredor, nunca duas queimadas seguidas (dá para andar)
		Lampada.Estado anterior = Lampada.Estado.ACESA;
		for (float x = X0 + 2; x < -X0; x += 4) {
			Lampada.Estado e = sortearEstado(random);
			if (e == Lampada.Estado.QUEIMADA && anterior == Lampada.Estado.QUEIMADA) e = Lampada.Estado.ACESA;
			lampadas.add(new Lampada(x, 0, e));
			anterior = e;
		}
		lampadas.add(new Lampada(0, 6, Lampada.Estado.ACESA));
		lampadas.add(new Lampada(0, 11, Lampada.Estado.PISCANDO));
		for (Sala sala : salas) {
			float[] c = sala.centro();
			for (float dz : new float[] { -2.5f, 2.5f }) {
				Lampada.Estado e = sala == inicio ? Lampada.Estado.PISCANDO : sortearEstado(random);
				lampadas.add(new Lampada(c[0], c[1] + dz, e));
			}
		}

		// as portas que abrem sozinhas: a da sala do início e mais três salas abertas (sorteadas)
		portasSozinhas.add(inicio);
		List<Sala> outras = new ArrayList<>();
		for (Sala s : salas) {
			if (!s.trancada() && s != inicio) outras.add(s);
		}
		Collections.shuffle(outras, random);
		portasSozinhas.addAll(outras.subList(0, Math.min(3, outras.size())));
	}

	/** As salas cuja porta começa fechada e abre sozinha (a primeira é a do início). */
	public List<Sala> portasQueAbremSozinhas() {
		return portasSozinhas;
	}

	private static Lampada.Estado sortearEstado(Random random) {
		float r = random.nextFloat();
		return r < 0.5f ? Lampada.Estado.ACESA : r < 0.65f ? Lampada.Estado.PISCANDO : Lampada.Estado.QUEIMADA;
	}

	public List<Lampada> lampadas() {
		return lampadas;
	}

	private static String nome(Tipo tipo, int numero) {
		return switch (tipo) {
		case AULA -> "Sala " + numero;
		case LABORATORIO -> "Lab. de Computação Gráfica";
		case BANHEIROS -> "Banheiros";
		case COORDENACAO -> "Coordenação";
		case PROFESSORES -> "Sala dos Professores";
		};
	}

	// ---------- paredes ----------

	/**
	 * As paredes internas: as duas do corredor (com o vão da porta de cada sala), as do saguão e as
	 * divisórias entre as salas. A casca do prédio fica com o desenho do prédio.
	 */
	public List<Caixa> paredes() {
		List<Caixa> lista = new ArrayList<>();
		float e = ESPESSURA / 2, L = LARGURA / 2, P = PROFUNDIDADE / 2, c = MEIO_CORREDOR, s = MEIO_SAGUAO;
		// parede do corredor, lado da frente (interrompida pelo saguão)
		paredeComPortas(lista, -L, -s, c, true);
		paredeComPortas(lista, s, L, c, true);
		// parede do corredor, lado dos fundos (inteira, com as portas)
		paredeComPortas(lista, -L, L, -c, false);
		// as paredes do saguão (da parede do corredor até a frente)
		lista.add(new Caixa(-s - e, c, -s + e, P));
		lista.add(new Caixa(s - e, c, s + e, P));
		// divisórias entre as salas
		for (Sala sala : salas) {
			if (sala.xMax() < L - 0.01f && !(sala.naFrente() && Math.abs(sala.xMax() + s) < 0.01f)) {
				lista.add(new Caixa(sala.xMax() - e, sala.zMin(), sala.xMax() + e, sala.zMax()));
			}
		}
		return lista;
	}

	/** Uma parede do corredor de x0 a x1, em z, com o vão das portas das salas daquele lado. */
	private void paredeComPortas(List<Caixa> lista, float x0, float x1, float z, boolean daFrente) {
		float e = ESPESSURA / 2, meio = VAO_PORTA / 2;
		List<Float> portas = new ArrayList<>();
		for (Sala s : salas) {
			float px = s.porta()[0];
			if (s.naFrente() == daFrente && px > x0 && px < x1) portas.add(px);
		}
		Collections.sort(portas);
		float inicio = x0;
		for (float px : portas) {
			lista.add(new Caixa(inicio, z - e, px - meio, z + e));
			inicio = px + meio;
		}
		lista.add(new Caixa(inicio, z - e, x1, z + e));
	}

	/** O vão da porta da sala (o retângulo que fica bloqueado enquanto ela está trancada). */
	public Caixa porta(Sala s) {
		float[] p = s.porta();
		float e = ESPESSURA / 2, meio = VAO_PORTA / 2;
		return new Caixa(p[0] - meio, p[1] - e, p[0] + meio, p[1] + e);
	}

	public Caixa portaProfessor() {
		return porta(professores);
	}

	/** As carteiras de uma sala de aula: {x, z} do centro de cada uma (4 colunas x 3 fileiras, de frente para o fundo). */
	public static List<float[]> carteiras(Sala s) {
		List<float[]> lista = new ArrayList<>();
		float[] c = s.centro();
		float sentido = s.naFrente() ? 1 : -1; // o fundo (a lousa) fica para +z nas salas da frente
		float larguraUtil = Math.min(s.xMax() - s.xMin() - 2, 5);
		for (int fila = 0; fila < 3; fila++) {
			for (int col = 0; col < 4; col++) {
				float x = c[0] - larguraUtil / 2 + larguraUtil * (col + 0.5f) / 4;
				float z = c[1] - sentido * 2.5f + sentido * fila * 1.7f;
				lista.add(new float[] { x, z });
			}
		}
		return lista;
	}

	// ---------- consultas ----------

	public List<Sala> salas() {
		return salas;
	}

	public Sala professores() {
		return professores;
	}

	public Sala cracha() {
		return cracha;
	}

	public Sala inicio() {
		return inicio;
	}

	/** O ponto no meio do corredor, na frente da porta da sala. */
	public static float[] noCorredor(Sala s) {
		return new float[] { s.porta()[0], 0 };
	}

	/** O ponto está no corredor ou no saguão? */
	public static boolean noCorredorOuSaguao(float x, float z) {
		return Math.abs(z) <= MEIO_CORREDOR || (Math.abs(x) <= MEIO_SAGUAO && z > 0);
	}
}
