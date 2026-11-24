package jogo;

import java.util.List;

/**
 * A sequência da partida, que começa dentro da Univali:
 *  1. achar a sala do professor; a porta está trancada e ele responde lá de dentro que sem crachá
 *     ninguém entra: só então o crachá aparece (numa das salas) e dá para abrir a porta com ele;
 *  2. falar com o professor, que nega ("sem as provas, nem pensar") e abre a porta da frente;
 *  3. buscar as 3 provas na floresta;
 *  4. voltar e entregar as provas na mesa dele.
 * Diz qual é a etapa, o texto do objetivo na tela e as falas do professor. Só lógica.
 */
public class Objetivos {

	public enum Etapa { ACHAR_A_SALA, FALAR_COM_O_PROFESSOR, BUSCAR_PROVAS, ENTREGAR }

	private boolean temCracha = false, portaProfessorAberta = false, falouComProfessor = false;
	private boolean viuPortaTrancada = false, respondeuNaPorta = false;

	public Etapa etapa(int provas, int total) {
		if (!portaProfessorAberta) return Etapa.ACHAR_A_SALA;
		if (!falouComProfessor) return Etapa.FALAR_COM_O_PROFESSOR;
		return provas < total ? Etapa.BUSCAR_PROVAS : Etapa.ENTREGAR;
	}

	/** O objetivo, para a linha no alto da tela. */
	public String texto(int provas, int total) {
		return switch (etapa(provas, total)) {
		case ACHAR_A_SALA -> viuPortaTrancada ? (temCracha ? "Abra a sala do professor com o crachá."
				: "Procure o crachá do professor nas salas.") : "Encontre a sala do professor.";
		case FALAR_COM_O_PROFESSOR -> "Fale com o professor.";
		case BUSCAR_PROVAS -> "Encontre as provas na floresta (" + provas + " de " + total + ").";
		case ENTREGAR -> "Leve as provas ao professor.";
		};
	}

	/** O crachá só existe para o jogador depois que ele bateu na porta e ouviu que precisa dele. */
	public boolean podePegarCracha() {
		return viuPortaTrancada && !temCracha;
	}

	/** Pega o crachá (só vale depois de bater na porta). */
	public void pegarCracha() {
		if (viuPortaTrancada) temCracha = true;
	}

	public boolean temCracha() {
		return temCracha;
	}

	/**
	 * Tenta abrir a sala do professor: com o crachá, abre (true); sem ele, fica sabendo que está
	 * trancada (false).
	 */
	public boolean abrirPortaProfessor() {
		viuPortaTrancada = true;
		if (temCracha) portaProfessorAberta = true;
		return portaProfessorAberta;
	}

	/**
	 * O que o professor responde lá de dentro quando batem na porta trancada: na primeira vez, que
	 * sem crachá ninguém entra; depois, nada (a porta só está trancada). Chamar depois de
	 * abrirPortaProfessor() ter dado false.
	 */
	public List<String> respostaDaPorta() {
		if (respondeuNaPorta) return List.of();
		respondeuNaPorta = true;
		return List.of("Professor: \"Quem está aí?\"", "Professor: \"Sem crachá, ninguém entra na minha sala.\"");
	}

	public boolean portaProfessorAberta() {
		return portaProfessorAberta;
	}

	/** A porta da frente só abre depois da conversa com o professor. */
	public boolean portaEntradaAberta() {
		return falouComProfessor;
	}

	/**
	 * Fala com o professor sem as provas todas: na primeira vez, ele nega e manda buscar (e a
	 * porta da frente abre); depois, só diz o que falta.
	 */
	public List<String> falarSemAsProvas(List<String> faltando) {
		if (!falouComProfessor) {
			falouComProfessor = true;
			return List.of("Professor: \"Hm? Cadê as provas?\"", "Professor: \"Sem as três provas, nem pensar.\"",
					"Professor: \"Elas estão na floresta. A porta da frente está aberta.\"");
		}
		return List.of("Professor: \"Ainda faltam: " + String.join(", ", faltando) + ".\"");
	}

	public boolean falouComProfessor() {
		return falouComProfessor;
	}
}
