package telas;

import java.awt.Color;

import jogo.Nota;
import jogo.Partida;
import render.AtmosferaMenu;
import render.Overlay2D;

/**
 * APROVADO. O fundo é a Univali à noite, como no menu, mas mais pálida e fria: chegou,
 * mas não saiu inteiro. O título é carimbado em branco-osso, com a média final (de 6,0 a 10,0,
 * pelo tempo e pelas aparições sobrevividas) e o comentário do professor para a faixa da nota.
 */
public class TelaVitoria extends TelaFim {

	private static final Color COR_TITULO = new Color(222, 214, 196);

	private final AtmosferaMenu atmosfera = new AtmosferaMenu();
	private final float nota;

	public TelaVitoria(GerenciadorTelas gerenciador, Partida.Estatisticas estatisticas) {
		this(gerenciador, estatisticas, Nota.calcular(estatisticas.segundos(), estatisticas.aparicoesSobrevividas()));
	}

	private TelaVitoria(GerenciadorTelas gerenciador, Partida.Estatisticas estatisticas, float nota) {
		super(gerenciador, estatisticas, Nota.comentario(nota));
		this.nota = nota;
	}

	@Override
	protected String titulo() {
		return "APROVADO";
	}

	@Override
	protected Color corTitulo() {
		return COR_TITULO;
	}

	@Override
	protected String linhaDestaque() {
		return "Média final: " + Nota.formatar(nota);
	}

	@Override
	public void atualizar(long diftime) {
		atmosfera.atualizar(diftime);
		super.atualizar(diftime);
	}

	@Override
	protected void desenharFundo(Overlay2D o, int w, int h) {
		atmosfera.desenharFundo(o, w, h);
		// véu frio e escuro por cima da foto: o título e os textos ficam legíveis
		o.retangulo(0, 0, w, h, new Color(10, 16, 22, 150));
	}
}
