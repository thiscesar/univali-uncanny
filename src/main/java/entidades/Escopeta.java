package entidades;

import jogo.Regras;
import render.Mola;

/**
 * A escopeta: munição, estados (pronta, atirando, recarregando, vazia) e a animação procedural
 * (recuo, atraso ao girar, balanço ao andar, abaixar ao correr, inclinar na recarga, telha).
 * Só lógica (não usa OpenGL): o desenho fica em render.DesenhoEscopeta, que lê a pose daqui.
 *
 * A pose é dada em espaço da câmera (x para a direita, y para cima, -z para a frente):
 * um deslocamento em metros e três giros em radianos, somados à posição de descanso da arma.
 */
public class Escopeta {

	public enum Estado { PRONTA, ATIRANDO, RECARREGANDO, VAZIA }

	/** Quem quer saber o que a arma faz (sons, efeitos, o cálculo dos bagos). Tudo opcional. */
	public interface Eventos {
		default void disparou() {}
		default void engatilhou() {}
		default void cartuchoInserido() {}
		default void cliqueSeco() {}
	}

	/** Quanto a telha anda para trás no engatilhar, em metros. */
	public static final float CURSO_TELHA = 0.09f;

	private Eventos eventos = new Eventos() {};
	private Estado estado = Estado.PRONTA;
	private int cano = Regras.CAPACIDADE_CANO;
	private int reserva = Regras.RESERVA_INICIAL;
	private float tempoNoEstado = 0;
	private float tempoCartucho = 0;
	/** A recarga começou com o cano vazio: no fim, a telha engatilha. */
	private boolean recargaComCanoVazio = false;
	private float telha = 0;          // 0 a 1: quanto a telha está para trás
	private float tempoTelha = -1;    // segundos desde que a telha começou a andar (-1 = parada)
	private boolean engatilhouNestaVolta = false;

	// as molas da animação
	private final Mola recuo = new Mola(260, 22);         // chute para trás (metros)
	private final Mola recuoGiro = new Mola(220, 18);     // cano subindo (radianos)
	private final Mola atrasoX = new Mola(120, 18);       // fica para trás ao girar o mouse
	private final Mola atrasoY = new Mola(120, 18);
	private final Mola abaixar = new Mola(160, 24);       // 0 = em posição, 1 = abaixada (correndo)
	private final Mola inclinar = new Mola(90, 17);       // 0 a 1: recarga
	private final Mola solavanco = new Mola(400, 20);     // tranco de cada cartucho
	private float balancoX = 0, balancoY = 0;

	public void definirEventos(Eventos eventos) {
		this.eventos = eventos;
	}

	// ---------- comandos ----------

	/**
	 * Aperta o gatilho. Atira se houver cartucho no cano e a arma estiver em posição (não dá
	 * para atirar correndo). Durante a recarga, interrompe e atira se já houver um no cano.
	 * Devolve true se o tiro saiu (os bagos são calculados por quem chamou).
	 */
	public boolean gatilho() {
		if (estado == Estado.ATIRANDO || abaixar.posicao > 0.35f) {
			return false;
		}
		if (cano == 0) {
			eventos.cliqueSeco();
			return false;
		}
		cano--;
		estado = Estado.ATIRANDO;
		tempoNoEstado = 0;
		recuo.empurrar(1.6f);
		recuoGiro.empurrar(9f);
		tempoTelha = 0; // a telha engatilha logo depois do tiro
		engatilhouNestaVolta = false;
		eventos.disparou();
		return true;
	}

	/** Começa a recarregar, um cartucho por vez (só se couber e se houver reserva). */
	public void recarregar() {
		if (estado == Estado.ATIRANDO || estado == Estado.RECARREGANDO) return;
		if (cano >= Regras.CAPACIDADE_CANO || reserva == 0) return;
		recargaComCanoVazio = cano == 0;
		estado = Estado.RECARREGANDO;
		tempoNoEstado = 0;
		tempoCartucho = 0;
	}

	/** Pega uma caixa de munição. */
	public void adicionarReserva(int quantidade) {
		reserva += quantidade;
	}

	// ---------- simulação ----------

	/**
	 * dt em segundos. mouseX/Y é quanto o mouse girou neste quadro (pixels); fasePasso e
	 * velocidade vêm do jogador, para o balanço acompanhar a cabeça.
	 */
	public void atualizar(float dt, float mouseX, float mouseY, float fasePasso, float velocidade, boolean correndo) {
		tempoNoEstado += dt;
		switch (estado) {
		case ATIRANDO -> {
			if (tempoNoEstado >= Regras.DURACAO_TIRO) {
				estado = Estado.PRONTA;
			}
		}
		case RECARREGANDO -> {
			tempoCartucho += dt;
			if (tempoCartucho >= Regras.DURACAO_CARTUCHO) {
				tempoCartucho -= Regras.DURACAO_CARTUCHO;
				cano++;
				reserva--;
				solavanco.empurrar(1.2f);
				eventos.cartuchoInserido();
				if (cano >= Regras.CAPACIDADE_CANO || reserva == 0) {
					estado = Estado.PRONTA;
					if (recargaComCanoVazio) {
						tempoTelha = 0; // com o cano vazio, precisa engatilhar o primeiro
						engatilhouNestaVolta = false;
					}
				}
			}
		}
		default -> { }
		}
		if (estado == Estado.PRONTA && cano == 0) estado = Estado.VAZIA;
		if (estado == Estado.VAZIA && cano > 0) estado = Estado.PRONTA;

		atualizarTelha(dt);

		// o mouse empurra o alvo do atraso; a mola traz a arma de volta ao centro
		atrasoX.alvo = limitar(-mouseX * 0.0012f, 0.05f);
		atrasoY.alvo = limitar(mouseY * 0.0012f, 0.05f);
		abaixar.alvo = correndo && velocidade > 0.5f ? 1 : 0;
		inclinar.alvo = estado == Estado.RECARREGANDO ? 1 : 0;
		for (Mola m : new Mola[] { recuo, recuoGiro, atrasoX, atrasoY, abaixar, inclinar, solavanco }) {
			m.atualizar(dt);
		}

		// balanço em "8": o lado vai e volta a cada dois passos, a altura a cada passo
		float intensidade = Math.min(1, velocidade / Jogador.VELOCIDADE_CORRENDO) * (correndo ? 1.6f : 1f);
		balancoX = (float) Math.sin(fasePasso) * 0.012f * intensidade;
		balancoY = -Math.abs((float) Math.cos(fasePasso)) * 0.010f * intensidade;
	}

	/** A telha vai para trás e volta em 0,3 s (começando 0,05 s depois do tiro), com o som no meio. */
	private void atualizarTelha(float dt) {
		if (tempoTelha < 0) {
			telha = 0;
			return;
		}
		tempoTelha += dt;
		float p = (tempoTelha - 0.05f) / 0.25f;
		if (p >= 0.3f && !engatilhouNestaVolta) {
			engatilhouNestaVolta = true;
			eventos.engatilhou();
		}
		if (p >= 1) {
			tempoTelha = -1;
			telha = 0;
		} else {
			telha = p <= 0 ? 0 : (float) Math.sin(Math.PI * p);
		}
	}

	private static float limitar(float v, float maximo) {
		return Math.max(-maximo, Math.min(maximo, v));
	}

	// ---------- a pose, para o desenho ----------

	/** Deslocamento da arma em relação ao descanso, em metros (espaço da câmera). */
	public float[] deslocamento() {
		return new float[] {
				atrasoX.posicao + balancoX + inclinar.posicao * 0.03f,
				atrasoY.posicao + balancoY - abaixar.posicao * 0.07f - inclinar.posicao * 0.06f - solavanco.posicao * 0.01f,
				recuo.posicao * 0.09f + abaixar.posicao * 0.02f };
	}

	/** Giros da arma em radianos: {em volta de x (cano sobe), de y (vira), de z (deita de lado)}. */
	public float[] giro() {
		return new float[] {
				recuoGiro.posicao * 0.03f - abaixar.posicao * 0.35f - inclinar.posicao * 0.15f,
				atrasoX.posicao * 2f + abaixar.posicao * 0.25f,
				inclinar.posicao * 0.6f + abaixar.posicao * 0.15f };
	}

	/** De 0 (na frente) a 1 (toda para trás): o deslocamento da telha ao engatilhar. */
	public float telha() {
		return telha;
	}

	// ---------- consultas ----------

	public Estado estado() {
		return estado;
	}

	public int cano() {
		return cano;
	}

	public int reserva() {
		return reserva;
	}

	/** Segundos desde o último tiro (grande se faz tempo): o clarão e a chama usam isto. */
	public float tempoDesdeOTiro() {
		return estado == Estado.ATIRANDO ? tempoNoEstado : 99;
	}
}
