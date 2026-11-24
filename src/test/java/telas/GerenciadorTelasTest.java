package telas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import entrada.Entrada;

class GerenciadorTelasTest {

	/** Tela falsa que anota no registro tudo o que acontece com ela. */
	static class TelaTeste implements Tela {
		final String nome;
		final List<String> registro;
		long ultimoDiftime = -1;

		TelaTeste(String nome, List<String> registro) {
			this.nome = nome;
			this.registro = registro;
		}

		public void entrar() { registro.add(nome + ".entrar"); }
		public void sair() { registro.add(nome + ".sair"); }
		public void atualizar(long diftime) { registro.add(nome + ".atualizar"); ultimoDiftime = diftime; }
		public void desenhar() { registro.add(nome + ".desenhar"); }
	}

	private final List<String> registro = new ArrayList<>();
	private final GerenciadorTelas g = new GerenciadorTelas(0, new Entrada()); // janela 0: sem GLFW

	@Test
	void trocaSoAconteceNoFimDoQuadro() {
		TelaTeste a = new TelaTeste("a", registro);
		TelaTeste b = new TelaTeste("b", registro);
		g.trocarPara(a);
		g.aplicarTrocas();

		g.trocarPara(b);
		assertSame(a, g.atual(), "pedir a troca não troca na hora");
		g.atualizar(16);
		g.aplicarTrocas();

		assertSame(b, g.atual());
		assertEquals(List.of("a.entrar", "a.atualizar", "a.sair", "b.entrar"), registro);
	}

	@Test
	void sobreposicaoCongelaATelaDeBaixoMasDesenhaAsDuas() {
		TelaTeste jogo = new TelaTeste("jogo", registro);
		TelaTeste pausa = new TelaTeste("pausa", registro);
		g.trocarPara(jogo);
		g.aplicarTrocas();
		g.sobrepor(pausa);
		g.aplicarTrocas();
		registro.clear();

		g.atualizar(16);
		g.desenhar();

		assertEquals(List.of("pausa.atualizar", "jogo.desenhar", "pausa.desenhar"), registro);
	}

	@Test
	void fecharSobreposicaoVoltaAAtualizarATelaDeBaixo() {
		TelaTeste jogo = new TelaTeste("jogo", registro);
		TelaTeste pausa = new TelaTeste("pausa", registro);
		g.trocarPara(jogo);
		g.sobrepor(pausa);
		g.aplicarTrocas();

		g.fecharSobreposicao();
		g.aplicarTrocas();
		registro.clear();
		g.atualizar(16);

		assertNull(g.sobreposta());
		assertEquals(List.of("jogo.atualizar"), registro);
	}

	@Test
	void trocarDeTelaFechaASobreposicaoAntes() {
		TelaTeste jogo = new TelaTeste("jogo", registro);
		TelaTeste pausa = new TelaTeste("pausa", registro);
		TelaTeste menu = new TelaTeste("menu", registro);
		g.trocarPara(jogo);
		g.sobrepor(pausa);
		g.aplicarTrocas();
		registro.clear();

		g.trocarPara(menu);
		g.aplicarTrocas();

		assertNull(g.sobreposta());
		assertEquals(List.of("pausa.sair", "jogo.sair", "menu.entrar"), registro);
	}

	@Test
	void diftimeGrandeELimitado() {
		TelaTeste a = new TelaTeste("a", registro);
		g.trocarPara(a);
		g.aplicarTrocas();
		g.atualizar(8000); // por exemplo, depois de gerar o mapa

		assertEquals(GerenciadorTelas.DIFTIME_MAXIMO, a.ultimoDiftime);
	}

	@Test
	void proporcaoNuncaDividePorZero() {
		g.redimensionar(1500, 0); // janela minimizada
		assertEquals(1500f, g.proporcao(), 1e-6);
	}

	@Test
	void comTransicaoATelaEscureceCongeladaAntesDaTrocaEDepoisClareia() {
		TelaTeste a = new TelaTeste("a", registro);
		TelaTeste b = new TelaTeste("b", registro);
		g.trocarPara(a);
		g.aplicarTrocas();
		g.ligarTransicoes(true);
		g.trocarPara(b);
		registro.clear();
		g.atualizar(100);
		g.aplicarTrocas();
		assertSame(a, g.atual(), "ainda escurecendo");
		assertEquals(List.of(), registro, "congelada: a tela não é atualizada");
		assertEquals(100f / 1000 / GerenciadorTelas.ESCURECER, g.escuridao(), 1e-4);
		g.atualizar(100);
		g.atualizar(100);
		g.aplicarTrocas();
		assertSame(b, g.atual());
		assertEquals(1f, g.escuridao(), 1e-6, "a nova começa no preto");
		g.atualizar(100);
		assertEquals(1 - 0.1f / GerenciadorTelas.CLAREAR, g.escuridao(), 1e-4);
		g.atualizar(100);
		g.atualizar(100);
		g.atualizar(100);
		assertEquals(0f, g.escuridao(), 1e-6);
		assertEquals("b.atualizar", registro.get(registro.size() - 1), "clareando, a nova já responde");
	}
}
