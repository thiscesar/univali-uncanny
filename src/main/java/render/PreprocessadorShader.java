package render;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.Caminhos;

/**
 * Permite usar #include "arquivo" nos shaders, coisa que o GLSL não tem.
 *
 * A neblina, a luz ambiente e a lanterna precisam ser idênticas em todos os shaders 3D.
 * Em vez de copiar esse código em cada um, ele fica em comum.glsl, e cada shader escreve
 * #include "comum.glsl". Este pré-processador troca a linha pelo conteúdo do arquivo
 * (inclusive includes dentro de includes, cada arquivo uma vez só), grava o resultado em
 * target/shaders/ e devolve o caminho, que vai para o construtor do ShaderProgram do
 * projeto original, sem precisar alterá-lo.
 *
 * Para os erros apontarem o arquivo certo, cada trecho recebe uma diretiva #line com um
 * número de "fonte". Antes de entregar, o shader é compilado uma vez para teste; se der
 * erro, a mensagem é traduzida (fonte 1, linha 12 vira comum.glsl:12) e o jogo para com
 * uma explicação clara.
 */
public final class PreprocessadorShader {

	private static final Pattern INCLUDE = Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");
	private static final Pattern VERSAO = Pattern.compile("^\\s*#version\\b.*");
	// formatos de erro dos drivers: Mesa "0:12(5): error", NVIDIA "0(12) : error"
	private static final Pattern ERRO_MESA = Pattern.compile("\\b(\\d+):(\\d+)\\((\\d+)\\)");
	private static final Pattern ERRO_NVIDIA = Pattern.compile("\\b(\\d+)\\((\\d+)\\)");

	/** O texto pronto e a lista de arquivos de cada número de fonte (0 = o próprio shader). */
	public record Resultado(String texto, List<String> fontes) {}

	private PreprocessadorShader() {}

	/**
	 * Resolve os includes de um shader, testa a compilação e devolve o caminho do arquivo
	 * gerado. Só na thread principal (usa OpenGL para o teste).
	 *
	 * @param relativo ex.: "shaders/objetoFragment.txt"
	 * @param tipo     GL_VERTEX_SHADER ou GL_FRAGMENT_SHADER
	 */
	public static String preparar(String relativo, int tipo) {
		Resultado r = processar(relativo, PreprocessadorShader::lerArquivo);
		File saida = gravar(relativo, r.texto());
		verificar(r, tipo, relativo);
		return saida.getPath();
	}

	/**
	 * A parte sem OpenGL (dá para testar): monta o texto final.
	 *
	 * @param leitor devolve as linhas de um arquivo pelo caminho relativo
	 */
	public static Resultado processar(String relativo, Function<String, List<String>> leitor) {
		List<String> fontes = new ArrayList<>();
		StringBuilder texto = new StringBuilder();
		incluir(relativo, leitor, fontes, new HashSet<>(), texto, true);
		return new Resultado(texto.toString(), fontes);
	}

	private static void incluir(String arquivo, Function<String, List<String>> leitor, List<String> fontes,
			Set<String> jaIncluidos, StringBuilder texto, boolean principal) {
		jaIncluidos.add(arquivo);
		int numero = fontes.size();
		fontes.add(arquivo);
		List<String> linhas = leitor.apply(arquivo);
		String pasta = arquivo.contains("/") ? arquivo.substring(0, arquivo.lastIndexOf('/') + 1) : "";

		boolean marcouInicio = false;
		for (int i = 0; i < linhas.size(); i++) {
			String linha = linhas.get(i);
			int numeroLinha = i + 1;
			if (VERSAO.matcher(linha).matches()) {
				if (principal) {
					texto.append(linha).append('\n'); // o #version precisa ser a primeira linha
				}
				continue; // num include, o #version é ignorado
			}
			if (!marcouInicio) {
				texto.append("#line ").append(numeroLinha).append(' ').append(numero).append('\n');
				marcouInicio = true;
			}
			Matcher m = INCLUDE.matcher(linha);
			if (m.matches()) {
				String incluido = pasta + m.group(1);
				if (jaIncluidos.contains(incluido)) {
					texto.append("// (já incluído: ").append(m.group(1)).append(")\n");
				} else {
					incluir(incluido, leitor, fontes, jaIncluidos, texto, false);
					// volta a numerar como o arquivo atual, na linha seguinte ao include
					texto.append("#line ").append(numeroLinha + 1).append(' ').append(numero).append('\n');
				}
				continue;
			}
			texto.append(linha).append('\n');
		}
	}

	/**
	 * Troca "fonte:linha" pelo nome do arquivo nas mensagens de erro do compilador.
	 * Ex.: "1:23(5): error: ..." vira "comum.glsl:23(5): error: ...".
	 */
	public static String traduzirErros(String log, List<String> fontes) {
		Matcher m = ERRO_MESA.matcher(log);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(sb, Matcher.quoteReplacement(nomeFonte(m.group(1), fontes) + ":" + m.group(2) + "(" + m.group(3) + ")"));
		}
		m.appendTail(sb);
		if (!sb.toString().equals(log)) {
			return sb.toString();
		}
		m = ERRO_NVIDIA.matcher(log);
		sb = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(sb, Matcher.quoteReplacement(nomeFonte(m.group(1), fontes) + ":" + m.group(2)));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	private static String nomeFonte(String numero, List<String> fontes) {
		int n = Integer.parseInt(numero);
		return n < fontes.size() ? fontes.get(n) : "fonte " + numero;
	}

	private static List<String> lerArquivo(String relativo) {
		try {
			return Files.readAllLines(Caminhos.arquivo(relativo).toPath(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("shader não encontrado: " + relativo, e);
		}
	}

	/** Grava em target/shaders/ (ou na pasta temporária, se não der para escrever ali). */
	private static File gravar(String relativo, String texto) {
		String nome = new File(relativo).getName();
		File pasta = new File(Caminhos.pastaBase(), "target/shaders");
		if (!pasta.isDirectory() && !pasta.mkdirs()) {
			pasta = new File(System.getProperty("java.io.tmpdir"), "univali-uncanny-shaders");
			pasta.mkdirs();
		}
		File saida = new File(pasta, nome);
		try {
			Files.writeString(saida.toPath(), texto, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException("não foi possível gravar " + saida, e);
		}
		return saida;
	}

	/** Compila uma vez para teste; se falhar, mostra os erros com os nomes dos arquivos e para. */
	private static void verificar(Resultado r, int tipo, String relativo) {
		int id = glCreateShader(tipo);
		glShaderSource(id, r.texto());
		glCompileShader(id);
		boolean ok = glGetShaderi(id, GL_COMPILE_STATUS) != GL_FALSE;
		String log = ok ? "" : glGetShaderInfoLog(id, 8192);
		glDeleteShader(id);
		if (!ok) {
			String erros = traduzirErros(log, r.fontes());
			System.err.println("[Shader] erro ao compilar " + relativo + ":\n" + erros);
			throw new IllegalStateException("erro no shader " + relativo + ": " + erros.lines().findFirst().orElse(""));
		}
	}
}
