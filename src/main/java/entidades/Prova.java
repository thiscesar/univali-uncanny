package entidades;

import jogo.Regras;
import mundo.ConsultaAltura;
import obj.Object3D;

/**
 * Uma prova (M1, M2 ou M3): uma folha de papel flutuando a ~1 m do chão numa clareira, girando
 * devagar e subindo e descendo. Brilha de leve e sussurra (a TelaJogo cuida do som e o
 * render.DesenhoProvas do desenho). Com o jogador a até 1,5 m, a tecla E pega a prova.
 */
public class Prova extends Object3D {

	/** Altura em que a folha flutua e quanto ela sobe e desce, em metros. */
	public static final float ALTURA = 1.0f, OSCILACAO = 0.08f;

	public final String nome;
	private final float chao;
	private float tempo;
	private boolean coletada = false;

	public Prova(String nome, float x, float z, ConsultaAltura chao) {
		super(x, chao.alturaEm(x, z) + ALTURA, z);
		this.nome = nome;
		this.chao = chao.alturaEm(x, z);
		this.tempo = Math.abs(nome.hashCode() % 100) * 0.1f; // cada uma numa fase do balanço
	}

	@Override
	public void SimulaSe(long diftime) {
		tempo += diftime / 1000f;
		y = chao + ALTURA + OSCILACAO * (float) Math.sin(tempo * 1.6f);
	}

	/** Giro da folha em volta do eixo vertical, em radianos (uma volta a cada ~8 s). */
	public float giro() {
		return tempo * 0.8f;
	}

	public boolean coletada() {
		return coletada;
	}

	/** O jogador (os pés em x, z) está perto o bastante para pegar? */
	public boolean alcancavel(float jogadorX, float jogadorZ) {
		float dx = jogadorX - x, dz = jogadorZ - z;
		return !coletada && dx * dx + dz * dz <= Regras.DISTANCIA_PEGAR * Regras.DISTANCIA_PEGAR;
	}

	/** Pega a prova. Devolve false se ela já tinha sido pega. */
	public boolean coletar() {
		if (coletada) return false;
		coletada = true;
		vivo = false;
		return true;
	}
}
