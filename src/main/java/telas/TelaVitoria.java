package telas;

import java.awt.Color;
import java.util.List;

import render.AtmosferaMenu;
import render.Overlay2D;

/**
 * APROVADO. O fundo é a Univali à noite, como no menu, mas mais pálida e fria: chegou,
 * mas não saiu inteiro. O título é carimbado em branco-osso.
 */
public class TelaVitoria extends TelaFim {

	private static final List<String> FRASES = List.of(
			"Aprovado. Por enquanto.",
			"O semestre que vem te espera.",
			"Você saiu da floresta. Ela não saiu de você.",
			"O Dragon Knight vai lembrar do seu rosto.");
	private static final Color COR_TITULO = new Color(222, 214, 196);

	private final AtmosferaMenu atmosfera = new AtmosferaMenu();

	public TelaVitoria(GerenciadorTelas gerenciador, Resumo resumo) {
		super(gerenciador, resumo, FRASES);
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
