package dados;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpcoesTest {

	@TempDir
	File pasta;

	@Test
	void salvarECarregarDevolveOsMesmosValores() {
		File arquivo = new File(pasta, "config.properties");
		Opcoes o = new Opcoes();
		o.volumeGeral = 0.3f;
		o.volumeMusica = 0.1f;
		o.sensibilidade = 2.5f;
		o.seed = "4242";
		o.campoVisao = 85;
		o.densidadeArvores = 0.6f;
		o.inverterY = true;
		o.sombras = 2;
		o.salvar(arquivo);

		Opcoes lidas = Opcoes.carregar(arquivo);
		assertEquals(0.3f, lidas.volumeGeral, 1e-6);
		assertEquals(0.1f, lidas.volumeMusica, 1e-6);
		assertEquals(2.5f, lidas.sensibilidade, 1e-6);
		assertEquals("4242", lidas.seed);
		assertEquals(85f, lidas.campoVisao, 1e-6);
		assertEquals(0.6f, lidas.densidadeArvores, 1e-6);
		assertTrue(lidas.inverterY);
		assertEquals(2, lidas.sombras);
	}

	@Test
	void arquivoAusenteUsaOsPadroes() {
		Opcoes o = Opcoes.carregar(new File(pasta, "nao-existe.properties"));
		assertEquals(0.8f, o.volumeGeral, 1e-6);
		assertEquals("", o.seed);
	}

	@Test
	void valoresInvalidosSaoCorrigidos() throws IOException {
		File arquivo = new File(pasta, "config.properties");
		Files.writeString(arquivo.toPath(), "volume.geral=7\nvolume.musica=abc\nmapa.seed=12x\nvideo.campoVisao=500\nvideo.sombras=9\n");
		Opcoes o = Opcoes.carregar(arquivo);
		assertEquals(1f, o.volumeGeral, 1e-6, "limitado ao máximo");
		assertEquals(0.7f, o.volumeMusica, 1e-6, "inválido volta ao padrão");
		assertEquals("", o.seed);
		assertEquals(Opcoes.CAMPO_VISAO_MAXIMO, o.campoVisao, 1e-6);
		assertEquals(2, o.sombras, "limitado à mais alta");
	}

	@Test
	void seedDigitadaSempreDaOMesmoMapaEVaziaSorteia() {
		Opcoes o = new Opcoes();
		o.seed = "123";
		assertEquals(123L, o.seedDaPartida(new Random()));
		o.seed = "";
		long a = o.seedDaPartida(new Random(1)), b = o.seedDaPartida(new Random(2));
		assertNotEquals(a, b);
	}

	@Test
	void modoPs1ComecaDesligadoESalvaLigado() throws IOException {
		assertFalse(new Opcoes().modoPs1);
		File arquivo = new File(pasta, "config.properties");
		Opcoes o = new Opcoes();
		o.modoPs1 = true;
		o.salvar(arquivo);
		assertTrue(Opcoes.carregar(arquivo).modoPs1);
		Files.writeString(arquivo.toPath(), "volume.geral=0.5\n"); // arquivo antigo, sem a opção
		assertFalse(Opcoes.carregar(arquivo).modoPs1);
	}

	@Test
	void telaCheiaComecaLigadaESalvaDesligada() throws IOException {
		assertTrue(new Opcoes().telaCheia);
		File arquivo = new File(pasta, "config.properties");
		Opcoes o = new Opcoes();
		o.telaCheia = false;
		o.salvar(arquivo);
		assertFalse(Opcoes.carregar(arquivo).telaCheia);
		Files.writeString(arquivo.toPath(), "volume.geral=0.5\n"); // arquivo antigo, sem a opção
		assertTrue(Opcoes.carregar(arquivo).telaCheia);
	}
}
