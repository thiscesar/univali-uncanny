package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

import dados.Constantes;
import obj.Mapa3D;
import obj.ObjHTGsrtm;
import terreno.GeradorTerreno;

/**
 * Carregamento provisório. Gera o terreno (uma vez só) e espera o Enter para começar.
 *
 * Por enquanto o algoritmo genético roda na thread principal, então a janela congela
 * alguns segundos durante a geração. A tela espera um quadro antes de gerar, para a
 * cor e o título aparecerem primeiro.
 */
public class TelaCarregando extends TelaProvisoria {

	/** Mesma seed de antes; mais tarde ela virá do menu. */
	private static final long SEED = 42;
	private static final int TAMANHO_GRADE = 512;

	private int quadros = 0;
	private boolean pronto = false;

	public TelaCarregando(GerenciadorTelas gerenciador) {
		super(gerenciador, "Carregando mapa...", "gerando o relevo, aguarde", 0.0f, 0.0f, 0.0f);
	}

	@Override
	public void entrar() {
		super.entrar();
		pronto = Constantes.mapa != null; // o terreno já foi gerado numa partida anterior
		if (pronto) {
			definirDica("Mapa pronto. Enter: começar, ESC: menu");
		}
	}

	@Override
	public void atualizar(long diftime) {
		quadros++;
		if (!pronto && quadros >= 2) {
			gerarTerreno();
			pronto = true;
			definirDica("Mapa pronto. Enter: começar, ESC: menu");
		}
		if (gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			gerenciador.trocarPara(new TelaMenu(gerenciador));
		} else if (pronto && gerenciador.entrada().foiPressionada(GLFW_KEY_ENTER)) {
			gerenciador.trocarPara(new TelaJogo(gerenciador));
		}
	}

	/** O mesmo que o Main3D fazia antes de abrir a cena: AG, malha e objeto do terreno. */
	private void gerarTerreno() {
		int[][] alturas = GeradorTerreno.gerar(SEED, TAMANHO_GRADE);
		ObjHTGsrtm malha = new ObjHTGsrtm(alturas);
		malha.load();

		Constantes.mapa = new Mapa3D(-10.0f, 0.0f, -10.0f, 10);
		Constantes.mapa.model = malha;
	}
}
