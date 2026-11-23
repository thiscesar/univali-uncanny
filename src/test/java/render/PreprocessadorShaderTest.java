package render;

import static org.junit.jupiter.api.Assertions.*;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** O pré-processador sem OpenGL: arquivos "em memória" no lugar do disco. */
class PreprocessadorShaderTest {

	private static PreprocessadorShader.Resultado processar(Map<String, String> arquivos, String principal) {
		return PreprocessadorShader.processar(principal, nome -> {
			String conteudo = arquivos.get(nome);
			if (conteudo == null) throw new UncheckedIOException(new java.io.IOException("falta " + nome));
			return conteudo.lines().toList();
		});
	}

	@Test
	void trocaOIncludePeloConteudoEMantemOVersionNoTopo() {
		var r = processar(Map.of(
				"shaders/a.txt", "#version 330 core\n#include \"comum.glsl\"\nvoid main(){}",
				"shaders/comum.glsl", "float x = 1.0;"), "shaders/a.txt");
		List<String> linhas = r.texto().lines().toList();
		assertEquals("#version 330 core", linhas.get(0));
		assertTrue(r.texto().contains("float x = 1.0;"));
		assertTrue(r.texto().indexOf("float x") < r.texto().indexOf("void main"));
		assertFalse(r.texto().contains("#include"));
		assertEquals(List.of("shaders/a.txt", "shaders/comum.glsl"), r.fontes());
	}

	@Test
	void marcaAsLinhasDeCadaArquivoParaOsErros() {
		var r = processar(Map.of(
				"a.txt", "#version 330 core\n#include \"b.glsl\"\nvoid main(){}",
				"b.glsl", "float y;"), "a.txt");
		String t = r.texto();
		assertTrue(t.contains("#line 1 1\nfloat y;"), "o include começa na linha 1 da fonte 1");
		assertTrue(t.contains("#line 3 0\nvoid main(){}"), "depois volta para a linha 3 do arquivo principal");
	}

	@Test
	void includeDentroDeIncludeECadaArquivoUmaVezSo() {
		var r = processar(Map.of(
				"a.txt", "#version 330 core\n#include \"b.glsl\"\n#include \"c.glsl\"",
				"b.glsl", "#include \"c.glsl\"\nfloat b;",
				"c.glsl", "float c;"), "a.txt");
		assertEquals(1, r.texto().split("float c;", -1).length - 1, "c.glsl entra uma vez só");
		assertTrue(r.texto().indexOf("float c;") < r.texto().indexOf("float b;"));
	}

	@Test
	void includeCircularNaoTravaOPreprocessador() {
		var r = processar(Map.of(
				"a.txt", "#version 330 core\n#include \"b.glsl\"",
				"b.glsl", "#include \"a.txt\"\nfloat b;"), "a.txt");
		assertTrue(r.texto().contains("float b;"));
	}

	@Test
	void arquivoAusenteDaErroClaro() {
		assertThrows(UncheckedIOException.class, () -> processar(Map.of(
				"a.txt", "#version 330 core\n#include \"nao_existe.glsl\""), "a.txt"));
	}

	@Test
	void traduzErrosNosFormatosMesaENvidia() {
		List<String> fontes = List.of("shaders/a.txt", "shaders/comum.glsl");
		assertEquals("shaders/comum.glsl:23(5): error: x",
				PreprocessadorShader.traduzirErros("1:23(5): error: x", fontes));
		assertEquals("shaders/a.txt:7 : error C1008: y",
				PreprocessadorShader.traduzirErros("0(7) : error C1008: y", fontes));
	}
}
