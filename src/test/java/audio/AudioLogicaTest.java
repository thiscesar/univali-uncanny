package audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A parte do áudio que não depende da placa de som. */
class AudioLogicaTest {

	@Test
	void escolheAPrimeiraFonteLivre() {
		boolean[] livres = { false, false, true, true };
		long[] inicio = { 5, 1, 9, 0 };
		assertEquals(2, Audio.escolherFonte(livres, inicio, new boolean[4]));
	}

	@Test
	void semFonteLivreInterrompeAMaisAntiga() {
		boolean[] livres = { false, false, false };
		long[] inicio = { 7, 3, 12 };
		assertEquals(1, Audio.escolherFonte(livres, inicio, new boolean[3]));
	}

	@Test
	void somEmLoopEPoupadoMesmoSendoOMaisAntigo() {
		boolean[] livres = { false, false, false };
		long[] inicio = { 1, 8, 5 };
		boolean[] emLoop = { true, false, false };
		assertEquals(2, Audio.escolherFonte(livres, inicio, emLoop));
	}

	@Test
	void seTodosEstaoEmLoopInterrompeOMaisAntigo() {
		boolean[] livres = { false, false };
		long[] inicio = { 4, 2 };
		boolean[] emLoop = { true, true };
		assertEquals(1, Audio.escolherFonte(livres, inicio, emLoop));
	}

	@Test
	void fadeVaiDoInicioAoAlvoNoTempoCerto() {
		assertEquals(0f, Musica.fatorFade(0, 1, 1000, 2000, 1000), 1e-6);
		assertEquals(0.5f, Musica.fatorFade(0, 1, 1000, 2000, 2000), 1e-6);
		assertEquals(1f, Musica.fatorFade(0, 1, 1000, 2000, 9000), 1e-6, "depois do fim fica no alvo");
		assertEquals(0.4f, Musica.fatorFade(0.8f, 0, 0, 1000, 500), 1e-6, "fade-out a partir do meio");
	}

	@Test
	void abafarBaixaAMusicaDuranteAFalaEDepoisVolta() {
		// abafada a 25% de 1000 ms a 4000 ms
		assertEquals(1f, Musica.fatorAbafar(0.25f, 1000, 4000, 900), 1e-6, "antes, normal");
		assertEquals(0.25f, Musica.fatorAbafar(0.25f, 1000, 4000, 1000 + Musica.ABAFAR_DESCE), 1e-6, "desce rápido");
		assertEquals(0.25f, Musica.fatorAbafar(0.25f, 1000, 4000, 3999), 1e-6, "baixa durante a fala");
		assertEquals(0.625f, Musica.fatorAbafar(0.25f, 1000, 4000, 4000 + Musica.ABAFAR_SOBE / 2), 1e-6, "subindo");
		assertEquals(1f, Musica.fatorAbafar(0.25f, 1000, 4000, 9000), 1e-6, "depois, normal");
		assertEquals(1f, Musica.fatorAbafar(1, 0, 0, 5000), 1e-6, "sem abafar, normal");
	}

	@Test
	void doisAbafamentosAoMesmoTempoSeSomam() {
		Musica.Abafo fala = Musica.juntar(new Musica.Abafo(1, 0, 0), 0.25f, 3000, 1000); // uma fala de 1 a 4 s
		assertEquals(new Musica.Abafo(0.25f, 1000, 4000), fala);
		// no meio dela, um susto pede 8% por 2 s: fica o mais baixo, até o fim mais tarde, sem recomeçar a descida
		Musica.Abafo susto = Musica.juntar(fala, 0.08f, 2000, 2500);
		assertEquals(new Musica.Abafo(0.08f, 1000, 4500), susto);
		// e uma fala curta depois disso não levanta a música nem encurta o abafamento
		assertEquals(susto, Musica.juntar(susto, 0.25f, 500, 3000));
		// depois que acabou, um pedido novo começa do zero
		assertEquals(new Musica.Abafo(0.25f, 6000, 7000), Musica.juntar(susto, 0.25f, 1000, 6000));
	}

	@Test
	void audioNaoIniciadoFicaMudoSemErro() {
		Audio mudo = new Audio(); // sem iniciar(): como um computador sem placa de som
		Efeito e = mudo.carregarEfeito("audio/nao_existe.ogg");
		assertNull(mudo.tocar(e, 1f));
		assertNull(mudo.tocar3D(e, 1, 2, 3, 1f, true));
		mudo.parar(null);
		mudo.musica().tocar("audio/musica_menu.ogg", true);
		mudo.musica().fadeOut(100);
		mudo.encerrar();
		assertFalse(mudo.disponivel());
	}

	@Test
	void pingTemSomNoComecoESilencioNoFim() {
		short[] pcm = Sintetizador.ping(44100, 880, 120, 700);
		assertEquals(44100 * 700 / 1000, pcm.length);
		int maior = 0;
		for (int i = 0; i < 44100 / 20; i++) maior = Math.max(maior, Math.abs(pcm[i]));
		assertTrue(maior > 10000, "o ataque precisa ser audível");
		assertEquals(0, pcm[pcm.length - 1], "depois do ping vem silêncio");
	}
}
