package jogo;

import java.util.List;
import java.util.Random;

import entidades.DragonKnight;
import entidades.Jubilado;

/**
 * O diretor de sustos: controla o ritmo da partida, para não ficar chato (monstro o tempo todo)
 * nem sem graça (nada acontecendo). A ideia vem de jogos como Left 4 Dead e Alien: Isolation.
 *
 * O ciclo é calmaria, ameaça, calmaria, ameaça...
 *  - CALMARIA: nenhum monstro ataca. Os Jubilados só rondam de longe (dá para vê-los entre as
 *    árvores). A primeira dura a CARENCIA; as outras encurtam conforme as provas (50 s sem
 *    nenhuma, 30 s com as três): o fim é mais tenso, mas nunca sem respiro.
 *  - A vez do DRAGON_KNIGHT: ele surge em alguns segundos; a vez acaba quando ele some.
 *  - A vez do JUBILADO: o Jubilado mais perto do jogador passa a caçar; a vez acaba quando a
 *    caçada termina (ele perde o jogador de vista), quando é derrubado pela segunda vez (desiste,
 *    levanta e foge), ou em 60 s sem achar o jogador.
 *
 * Quem vem depois da calmaria é sorteado: o Dragon Knight começa raro (30%) e vai até 50% com as
 * provas; e nunca a mesma ameaça três vezes seguidas. Como só um monstro tem a vez, os dois
 * nunca atacam juntos. Só lógica (os monstros também): dá para testar e simular sem janela.
 */
public class DiretorDeSustos {

	public enum Vez { CALMARIA, JUBILADO, DRAGON_KNIGHT }

	private final DragonKnight dragonKnight;
	private final List<Jubilado> jubilados;
	private final Random random;

	private Vez vez = Vez.CALMARIA;
	/** Na calmaria: segundos até a próxima ameaça. Na vez de um monstro: o limite da vez. */
	private float restante = Regras.CARENCIA;
	private Vez ultima, penultima;
	/** O monstro da vez já apareceu (a vez só acaba depois que ele sair de cena). */
	private boolean comecou;
	private Jubilado cacador;
	/** Quantas quedas o caçador já tinha quando a vez dele começou. */
	private int quedasNoInicio;
	private int ameacas;

	public DiretorDeSustos(DragonKnight dragonKnight, List<Jubilado> jubilados, Random random) {
		this.dragonKnight = dragonKnight;
		this.jubilados = jubilados;
		this.random = random;
		for (Jubilado j : jubilados) j.encerrarCaca(); // todos começam só rondando
	}

	/**
	 * A cada quadro, antes de simular os monstros. ativo = os monstros já estão soltos (o jogador
	 * já saiu da Univali); antes disso, o tempo não corre.
	 */
	public void atualizar(float dt, int provas, float jogadorX, float jogadorZ, boolean ativo) {
		if (vez != Vez.DRAGON_KNIGHT) {
			dragonKnight.segurarAparicao(1000); // fora da vez dele, ele não surge
		}
		if (!ativo) return;
		if (vez != Vez.DRAGON_KNIGHT && dragonKnight.estado() != DragonKnight.Estado.AUSENTE) {
			// ele surgiu fora da vez (depuração): a vez passa a ser dele, e o Jubilado para de caçar
			if (cacador != null) cacador.encerrarCaca();
			cacador = null;
			penultima = ultima;
			ultima = vez = Vez.DRAGON_KNIGHT;
			comecou = true;
			ameacas++;
		}
		restante -= dt;
		switch (vez) {
		case CALMARIA -> {
			if (restante <= 0) comecar(sortear(provas), jogadorX, jogadorZ);
		}
		case DRAGON_KNIGHT -> {
			if (dragonKnight.estado() != DragonKnight.Estado.AUSENTE) {
				comecou = true;
			} else if (comecou || restante <= 0) {
				terminar(provas); // ele sumiu (ou não achou lugar para surgir)
			}
		}
		case JUBILADO -> {
			int quedas = cacador != null ? cacador.quedas() - quedasNoInicio : 0;
			if (quedas >= Regras.QUEDAS_PARA_DESISTIR) {
				terminar(provas); // derrubado de novo: desiste (ainda caído, vira observador e levanta fugindo)
			} else if (ameacando(cacador)) {
				comecou = true;
			} else if (comecou || restante <= 0) {
				terminar(provas); // a caçada acabou (ou ele não achou o jogador a tempo)
			}
		}
		}
	}

	/** O Jubilado está atrás do jogador: caçando, caído (vai levantar caçando) ou atacando. */
	private static boolean ameacando(Jubilado j) {
		return j != null && (j.estado() == Jubilado.Estado.CACANDO || j.estado() == Jubilado.Estado.CAIDO
				|| j.estado() == Jubilado.Estado.ATACANDO);
	}

	/** O Dragon Knight começa raro e fica mais comum com as provas; nunca três vezes seguidas a mesma ameaça. */
	Vez sortear(int provas) {
		if (ultima != null && ultima == penultima) {
			return ultima == Vez.JUBILADO ? Vez.DRAGON_KNIGHT : Vez.JUBILADO;
		}
		float chance = Regras.CHANCE_DRAGON_KNIGHT[Math.min(provas, Regras.CHANCE_DRAGON_KNIGHT.length - 1)];
		return random.nextFloat() < chance ? Vez.DRAGON_KNIGHT : Vez.JUBILADO;
	}

	private void comecar(Vez nova, float jogadorX, float jogadorZ) {
		penultima = ultima;
		ultima = nova;
		vez = nova;
		comecou = false;
		ameacas++;
		if (nova == Vez.DRAGON_KNIGHT) {
			restante = Regras.ESPERA_DRAGON_KNIGHT;
			dragonKnight.apressarProximaAparicao(0.5f + random.nextFloat() * 2.5f);
		} else {
			restante = Regras.VEZ_JUBILADO;
			cacador = maisPerto(jogadorX, jogadorZ);
			if (cacador != null) {
				cacador.liberarCaca();
				quedasNoInicio = cacador.quedas();
			}
		}
	}

	/** O Jubilado mais perto do jogador que pode caçar (não está caído). */
	private Jubilado maisPerto(float px, float pz) {
		Jubilado melhor = null;
		double menor = Double.MAX_VALUE;
		for (Jubilado j : jubilados) {
			double d = Math.hypot(j.x - px, j.z - pz);
			if (j.estado() != Jubilado.Estado.CAIDO && d < menor) {
				menor = d;
				melhor = j;
			}
		}
		return melhor;
	}

	private void terminar(int provas) {
		if (cacador != null) cacador.encerrarCaca();
		cacador = null;
		vez = Vez.CALMARIA;
		float base = Regras.CALMARIA[Math.min(provas, Regras.CALMARIA.length - 1)];
		restante = base * (1 + (random.nextFloat() * 2 - 1) * Regras.VARIACAO_INTERVALO);
	}

	/** Pegou uma prova: a floresta reage, e a calmaria acaba em no máximo AMEACA_DEPOIS_DA_PROVA segundos. */
	public void provaColetada() {
		if (vez == Vez.CALMARIA) restante = Math.min(restante, Regras.AMEACA_DEPOIS_DA_PROVA);
	}

	/** Já dá a vez a um monstro agora (na volta com as provas, e na depuração), se ninguém estiver em cena. */
	public void forcar(Vez nova, float jogadorX, float jogadorZ) {
		if (vez != Vez.CALMARIA && comecou) return;
		if (cacador != null) cacador.encerrarCaca();
		cacador = null;
		comecar(nova, jogadorX, jogadorZ);
	}

	public Vez vez() {
		return vez;
	}

	/** Segundos que faltam: da calmaria, ou do limite da vez. */
	public float restante() {
		return restante;
	}

	/** O Jubilado da vez (null fora da vez dele). */
	public Jubilado cacador() {
		return cacador;
	}

	/** Quantas ameaças já começaram na partida. */
	public int ameacas() {
		return ameacas;
	}
}
