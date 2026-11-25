package jogo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import entidades.Jubilado;

/**
 * O diretor de sustos: controla o ritmo da floresta, para não ficar chato (monstro o tempo todo)
 * nem sem graça (nada acontecendo). A ideia vem de jogos como Left 4 Dead e Alien: Isolation.
 * A floresta é dos Jubilados; o Dragon Knight fica para a volta, dentro da Univali.
 *
 * O ciclo é calmaria, caçada, calmaria, caçada...
 *  - CALMARIA: ninguém ataca. Os Jubilados só rondam de longe (dá para ver os olhos entre as
 *    árvores). A primeira dura a CARENCIA; as outras encurtam conforme as provas (40 s sem
 *    nenhuma, 22 s com as três): o fim é mais tenso, mas nunca sem respiro.
 *  - CACADA: o Jubilado mais perto do jogador (às vezes os dois mais perto, com chance que cresce
 *    com as provas) fareja e vem caçar. Depois que um deles acha o jogador, a caçada dura
 *    PERSEGUICAO segundos: perder o jogador de vista não basta, o caçador fareja e volta, e ao
 *    levantar de um tiro vem atrás de onde o jogador está. Acaba antes só se todos os caçadores
 *    desistirem (derrubados pela segunda vez: levantam e fogem), ou em 60 s se nenhum o achou.
 *  - PROVOCAÇÃO: quem ronda recua quando o jogador chega perto ou o ilumina. Se o jogador insistir
 *    (a lanterna nele por 2 s, chegar a 6 m ou atirar nele), ele vira caçador na hora: começa uma
 *    caçada, ou ele entra na que já está acontecendo.
 *
 * Só lógica (os Jubilados também): dá para testar e simular sem janela.
 */
public class DiretorDeSustos {

	public enum Vez { CALMARIA, CACADA }

	private final List<Jubilado> jubilados;
	private final Random random;

	private Vez vez = Vez.CALMARIA;
	/** Na calmaria: segundos até a próxima caçada. Na caçada: o limite para achar o jogador. */
	private float restante = Regras.CARENCIA;
	/** Algum caçador já achou o jogador (a caçada só acaba depois que todos o perderem). */
	private boolean comecou;
	/** Os caçadores desta caçada, com quantas quedas cada um tinha quando ela começou. */
	private final Map<Jubilado, Integer> cacadores = new HashMap<>();
	private int cacadas, sobrevividas;

	public DiretorDeSustos(List<Jubilado> jubilados, Random random) {
		this.jubilados = jubilados;
		this.random = random;
		for (Jubilado j : jubilados) j.encerrarCaca(); // todos começam só rondando
	}

	/**
	 * A cada quadro, antes de simular os Jubilados. ativo = eles já estão soltos (o jogador já saiu
	 * da Univali e ainda não voltou com as provas); fora disso, o tempo não corre.
	 */
	public void atualizar(float dt, int provas, float jogadorX, float jogadorZ, boolean ativo) {
		for (Jubilado j : jubilados) {
			if (j.foiProvocado() && ativo) provocado(j); // fora de hora, o aviso é só descartado
		}
		if (!ativo) return;
		restante -= dt;
		if (vez == Vez.CALMARIA) {
			if (restante <= 0) comecar(provas, jogadorX, jogadorZ);
			return;
		}
		boolean achou = false;
		for (var it = cacadores.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Jubilado j = e.getKey();
			if (j.quedas() - e.getValue() >= Regras.QUEDAS_PARA_DESISTIR) {
				j.encerrarCaca(); // derrubado de novo: desiste (ainda caído, vira observador e levanta fugindo)
				it.remove();
			} else if (j.estado() == Jubilado.Estado.CACANDO || j.estado() == Jubilado.Estado.ATACANDO
					|| j.quedas() > e.getValue()) {
				achou = true; // está atrás do jogador, ou o jogador já o derrubou: os dois se encontraram
			}
		}
		if (achou && !comecou) {
			comecou = true; // achou o jogador: daqui a perseguição tem hora para acabar
			restante = Regras.PERSEGUICAO;
		}
		if (restante <= 0 || cacadores.isEmpty()) {
			terminar(provas); // o tempo da perseguição passou (ou nenhum o achou, ou todos desistiram)
		}
	}

	/** Quantos caçam nesta caçada: um, ou às vezes dois (mais provável com mais provas). */
	int sortearQuantos(int provas) {
		float chance = Regras.CHANCE_DOIS_CACADORES[Math.min(provas, Regras.CHANCE_DOIS_CACADORES.length - 1)];
		return random.nextFloat() < chance ? 2 : 1;
	}

	private void comecar(int provas, float jogadorX, float jogadorZ) {
		abrirCacada();
		for (Jubilado j : maisPerto(jogadorX, jogadorZ, sortearQuantos(provas))) {
			j.liberarCaca();
			cacadores.put(j, j.quedas());
		}
	}

	private void abrirCacada() {
		vez = Vez.CACADA;
		comecou = false;
		restante = Regras.VEZ_JUBILADO;
		cacadas++;
	}

	/** Um que rondava foi provocado: vira caçador na hora, numa caçada nova ou na que já está acontecendo. */
	private void provocado(Jubilado j) {
		if (cacadores.containsKey(j) || j.estado() == Jubilado.Estado.CAIDO) return;
		if (vez == Vez.CALMARIA) abrirCacada();
		j.enfurecer();
		cacadores.put(j, j.quedas());
	}

	/** Os "quantos" Jubilados mais perto do jogador que podem caçar (não estão caídos). */
	private List<Jubilado> maisPerto(float px, float pz, int quantos) {
		List<Jubilado> livres = new ArrayList<>();
		for (Jubilado j : jubilados) {
			if (j.estado() != Jubilado.Estado.CAIDO) livres.add(j);
		}
		livres.sort((a, b) -> Double.compare(Math.hypot(a.x - px, a.z - pz), Math.hypot(b.x - px, b.z - pz)));
		return livres.subList(0, Math.min(quantos, livres.size()));
	}

	private void terminar(int provas) {
		if (comecou) sobrevividas++; // acharam o jogador e ele escapou
		for (Jubilado j : cacadores.keySet()) j.encerrarCaca();
		cacadores.clear();
		vez = Vez.CALMARIA;
		float base = Regras.CALMARIA[Math.min(provas, Regras.CALMARIA.length - 1)];
		restante = base * (1 + (random.nextFloat() * 2 - 1) * Regras.VARIACAO_INTERVALO);
	}

	/** Pegou uma prova: a floresta reage, e a calmaria acaba em no máximo AMEACA_DEPOIS_DA_PROVA segundos. */
	public void provaColetada() {
		if (vez == Vez.CALMARIA) restante = Math.min(restante, Regras.AMEACA_DEPOIS_DA_PROVA);
	}

	/** Começa uma caçada agora (depuração), se não houver uma em andamento. */
	public void forcar(int provas, float jogadorX, float jogadorZ) {
		if (vez == Vez.CALMARIA) comecar(provas, jogadorX, jogadorZ);
	}

	/** Acaba com tudo (o jogador voltou para a Univali com as provas): todos voltam a só rondar. */
	public void encerrar() {
		for (Jubilado j : cacadores.keySet()) j.encerrarCaca();
		cacadores.clear();
		vez = Vez.CALMARIA;
		restante = Float.MAX_VALUE;
	}

	public Vez vez() {
		return vez;
	}

	/** Segundos que faltam: da calmaria, ou do limite da caçada. */
	public float restante() {
		return restante;
	}

	/** Os caçadores desta caçada (vazio na calmaria). */
	public List<Jubilado> cacadores() {
		return new ArrayList<>(cacadores.keySet());
	}

	/** Quantas caçadas já começaram, e de quantas o jogador escapou (acharam e ele sobreviveu). */
	public int cacadas() {
		return cacadas;
	}

	public int sobrevividas() {
		return sobrevividas;
	}
}
