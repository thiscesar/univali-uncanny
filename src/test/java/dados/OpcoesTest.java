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
		o.salvar(arquivo);

		Opcoes lidas = Opcoes.carregar(arquivo);
		assertEquals(0.3f, lidas.volumeGeral, 1e-6);
		assertEquals(0.1f, lidas.volumeMusica, 1e-6);
		assertEquals(2.5f, lidas.sensibilidade, 1e-6);
		assertEquals("4242", lidas.seed);
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
		Files.writeString(arquivo.toPath(), "volume.geral=7\nvolume.musica=abc\nmapa.seed=12x\n");
		Opcoes o = Opcoes.carregar(arquivo);
		assertEquals(1f, o.volumeGeral, 1e-6, "limitado ao máximo");
		assertEquals(0.7f, o.volumeMusica, 1e-6, "inválido volta ao padrão");
		assertEquals("", o.seed);
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
}
