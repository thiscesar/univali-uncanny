package util;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve o caminho de todo arquivo que o jogo carrega (texturas, modelos, sons, shaders...).
 *
 * O projeto original usa caminhos relativos à pasta em que o programa foi aberto. Isso funciona
 * no VS Code, mas quebra quando o jogo é empacotado, porque o pacote não roda a partir da pasta
 * do aplicativo. Centralizando tudo aqui, basta mudar a pasta base num único lugar.
 *
 * A pasta base vem da propriedade de sistema "univali.assets" (por exemplo
 * -Dunivali.assets=/opt/jogo). Sem ela, vale a pasta atual.
 */
public final class Caminhos {

	/** Nome da propriedade de sistema que define a pasta base dos arquivos. */
	public static final String PROPRIEDADE = "univali.assets";

	/** Caminhos que já geraram aviso, para não repetir a mesma mensagem a cada quadro. */
	private static final Set<String> avisados = ConcurrentHashMap.newKeySet();

	private Caminhos() {}

	/** A pasta a partir da qual todos os arquivos são procurados. */
	public static File pastaBase() {
		return new File(System.getProperty(PROPRIEDADE, "."));
	}

	/**
	 * Devolve o arquivo correspondente ao caminho relativo, por exemplo "texturas/grama.png".
	 * Se ele não existir, avisa no console (uma vez) e devolve o File mesmo assim, para quem
	 * chamou decidir o que fazer (normalmente, usar um substituto).
	 */
	public static File arquivo(String relativo) {
		File f = resolver(relativo);
		if (!f.exists() && avisados.add(f.getPath())) {
			System.out.println("[Caminhos] arquivo não encontrado: " + f.getPath());
		}
		return f;
	}

	/** O mesmo que arquivo(relativo), mas como texto, para APIs que pedem String. */
	public static String caminho(String relativo) {
		return arquivo(relativo).getPath();
	}

	/** Verifica se o arquivo existe, sem gerar aviso. */
	public static boolean existe(String relativo) {
		return resolver(relativo).exists();
	}

	/**
	 * Junta o caminho à pasta base. Um caminho que já é absoluto (porque já passou por aqui)
	 * fica como está; sem isso, new File(base, "/x/y") viraria "base/x/y".
	 */
	private static File resolver(String caminho) {
		File f = new File(caminho);
		return f.isAbsolute() ? f : new File(pastaBase(), caminho);
	}
}
