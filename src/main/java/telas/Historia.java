package telas;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A historinha contada enquanto o mapa carrega, datilografada como numa máquina de escrever
 * (no clima dos documentos encontrados de Resident Evil): uma anotação deixada por outro aluno,
 * página por página. Cada letra tem o seu instante (com um pouco de irregularidade, como uma mão
 * de verdade), as pausas são maiores depois de ponto e vírgula, e o fim de cada linha tem o
 * retorno do carro. Quando uma página termina, ela fica um pouco e some.
 *
 * Enter (ou clique): termina de datilografar a página na hora; com a página pronta, passa para a
 * próxima. Só dados e tempo (testável): a TelaCarregando desenha e toca os sons que vêm daqui.
 */
class Historia {

	/** O que soa em cada momento da datilografia. */
	enum Som { TECLA, ESPACO, RETORNO, PAGINA }

	/** As páginas: no máximo 42 letras por linha (cabem na folha). */
	static final List<String[]> PAGINAS = List.of(
			new String[] { "UNIVALI - CAMPUS KOBRASOL", "SEMANA DE PROVAS - 23:58", "",
					"Fiquei na sala até tarde, revisando",
					"matrizes de projeção para a prova de",
					"Computação Gráfica. Devo ter cochilado",
					"em cima da carteira." },
			new String[] { "Acordei com o zumbido das lâmpadas.",
					"O relógio da parede parou em 23:58.",
					"Os corredores estão vazios. Chamei pelo",
					"vigia. Ninguém respondeu." },
			new String[] { "Olhei pela janela. Onde deveria estar",
					"a cidade, só existe floresta.",
					"Escura. Sem fim.", "",
					"Esta é a Univali.",
					"Mas não a que eu conheço." },
			new String[] { "Minhas provas sumiram. M1, M2 e M3.",
					"De vez em quando, ouço sussurros lá",
					"fora, entre as árvores.",
					"Acho que são elas me chamando." },
			new String[] { "Há uma luz acesa na sala dos professores.",
					"Ele ainda está aqui.",
					"E ainda espera as provas." },
			new String[] { "Não estou sozinho. Alguma coisa anda",
					"pela floresta. Se você encontrar os",
					"olhos vermelhos dele, atire.",
					"Você só tem cinco segundos.", "",
					"         - anotação encontrada na carteira" });

	/** Segundos por letra (média), pausas, quanto a página fica pronta na tela e quanto leva para sumir. */
	static final float POR_LETRA = 0.055f, PAUSA_PONTO = 0.35f, PAUSA_VIRGULA = 0.18f, RETORNO = 0.5f;
	static final float PARADA = 2.5f, SUMIR = 0.7f;

	private final Random random = new Random(1958);
	private int pagina = 0;
	private float tempo = 0;
	/** O instante de cada letra da página atual: [linha][coluna]. */
	private float[][] instantes;
	private float fimDaDatilografia;
	/** Até que instante os sons já foram entregues. */
	private float tocadoAte = -1;

	Historia() {
		montarPagina();
	}

	/** Sorteia o instante de cada letra da página atual. */
	private void montarPagina() {
		if (terminou()) return;
		String[] linhas = PAGINAS.get(pagina);
		instantes = new float[linhas.length][];
		float t = 0.4f; // a folha entra e a primeira tecla demora um pouco
		for (int l = 0; l < linhas.length; l++) {
			instantes[l] = new float[linhas[l].length()];
			for (int c = 0; c < linhas[l].length(); c++) {
				char ch = linhas[l].charAt(c);
				t += ch == ' ' ? POR_LETRA * 0.8f : POR_LETRA * (0.6f + random.nextFloat() * 0.8f);
				instantes[l][c] = t;
				if (ch == '.') t += PAUSA_PONTO;
				if (ch == ',') t += PAUSA_VIRGULA;
			}
			t += RETORNO; // o carro volta
		}
		fimDaDatilografia = t;
		tempo = 0;
		tocadoAte = -1;
	}

	/** Avança o tempo e devolve os sons que aconteceram nesse intervalo, em ordem. */
	public List<Som> atualizar(float dt) {
		List<Som> sons = new ArrayList<>();
		if (terminou()) return sons;
		tempo += dt;
		String[] linhas = PAGINAS.get(pagina);
		for (int l = 0; l < linhas.length; l++) {
			for (int c = 0; c < linhas[l].length(); c++) {
				float i = instantes[l][c];
				if (i > tocadoAte && i <= tempo) sons.add(linhas[l].charAt(c) == ' ' ? Som.ESPACO : Som.TECLA);
			}
			// o retorno do carro logo depois da última letra da linha (linha em branco: só o retorno)
			float fimLinha = (linhas[l].isEmpty() ? (l > 0 && instantes[l - 1].length > 0
					? instantes[l - 1][instantes[l - 1].length - 1] : 0.4f) : instantes[l][linhas[l].length() - 1]) + 0.12f;
			if (fimLinha > tocadoAte && fimLinha <= tempo) sons.add(Som.RETORNO);
		}
		tocadoAte = tempo;
		if (tempo >= fimDaDatilografia + PARADA + SUMIR) {
			pagina++;
			montarPagina();
			if (!terminou()) sons.add(Som.PAGINA);
		}
		return sons;
	}

	/** Enter: termina a página na hora; se ela já está pronta, vai para a próxima. */
	public void pular() {
		if (terminou()) return;
		if (tempo < fimDaDatilografia) {
			tempo = fimDaDatilografia;
			tocadoAte = tempo; // sem a rajada de teclas atrasadas
		} else {
			pagina++;
			montarPagina();
		}
	}

	public boolean terminou() {
		return pagina >= PAGINAS.size();
	}

	/** As linhas da página na tela (null quando acabou). */
	public String[] linhas() {
		return terminou() ? null : PAGINAS.get(pagina);
	}

	/** Quantas letras da linha já foram batidas. */
	public int letrasVisiveis(int linha) {
		if (terminou()) return 0;
		int n = 0;
		while (n < instantes[linha].length && instantes[linha][n] <= tempo) n++;
		return n;
	}

	/** A página já foi toda datilografada? */
	public boolean paginaPronta() {
		return !terminou() && tempo >= fimDaDatilografia;
	}

	/** De 1 a 0 no fim da página (ela some antes da próxima). */
	public float alfa() {
		if (terminou()) return 0;
		return Math.max(0, Math.min(1, (fimDaDatilografia + PARADA + SUMIR - tempo) / SUMIR));
	}

	public int pagina() {
		return pagina;
	}
}
