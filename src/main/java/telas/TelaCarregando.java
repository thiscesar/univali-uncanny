package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import dados.Constantes;
import obj.Mapa3D;
import obj.ObjHTGsrtm;
import terreno.GeradorTerreno;

/**
 * Carregamento provisório. Gera o terreno da seed pedida e espera o Enter para começar.
 * Se o terreno da mesma seed já estiver pronto (de uma partida anterior), não gera de novo.
 *
 * Por enquanto o algoritmo genético roda na thread principal, então a janela congela
 * alguns segundos durante a geração. A tela espera um quadro antes de gerar, para a
 * cor e o título aparecerem primeiro.
 */
public class TelaCarregando extends TelaProvisoria {

	private static final int TAMANHO_GRADE = 512;

	/** Seed do terreno guardado em Constantes.mapa. */
	private static long seedCarregada;

	private final long seed;

	private int quadros = 0;
	private boolean pronto = false;

	public TelaCarregando(GerenciadorTelas gerenciador, long seed) {
		super(gerenciador, "Carregando mapa...", "seed " + seed + ", gerando o relevo", 0.0f, 0.0f, 0.0f);
		this.seed = seed;
	}

	@Override
	public void entrar() {
		super.entrar();
		pronto = Constantes.mapa != null && seedCarregada == seed; // já gerado numa partida anterior
		if (pronto) {
			definirDica("seed " + seed + ". Enter: começar, ESC: menu");
		}
	}

	@Override
	public void atualizar(long diftime) {
		quadros++;
		if (!pronto && quadros >= 2) {
			gerarTerreno();
			pronto = true;
			definirDica("seed " + seed + ". Enter: começar, ESC: menu");
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
		} else if (pronto && gerenciador.entrada().foiPressionada(GLFW_KEY_ENTER)) {
			gerenciador.trocarPara(new TelaJogo(gerenciador));
		}
	}

	/** O mesmo que o Main3D fazia antes de abrir a cena: AG, malha e objeto do terreno. */
	private void gerarTerreno() {
		if (Constantes.mapa != null) {
			Constantes.mapa.model.liberar(); // apaga da placa de vídeo a malha do mapa anterior
			Constantes.mapa = null;
		}
		int[][] alturas = GeradorTerreno.gerar(seed, TAMANHO_GRADE);
		ObjHTGsrtm malha = new ObjHTGsrtm(alturas);
		malha.load();

		Constantes.mapa = new Mapa3D(-10.0f, 0.0f, -10.0f, 10);
		Constantes.mapa.model = malha;
		seedCarregada = seed;
	}
}
