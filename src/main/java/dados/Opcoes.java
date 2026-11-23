package dados;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.Random;

import util.Caminhos;

/**
 * As opções do jogador (volumes, sensibilidade do mouse, seed do mapa), salvas no arquivo
 * config.properties, para continuarem valendo depois de fechar e abrir o jogo.
 */
public class Opcoes {

	public static final String ARQUIVO = "config.properties";

	/** Volumes de 0 a 1. */
	public float volumeGeral = 0.8f;
	public float volumeMusica = 0.7f;
	public float volumeEfeitos = 0.8f;
	/** Multiplicador da velocidade do mouse (1 = padrão). */
	public float sensibilidade = 1.0f;
	/** Seed digitada (só dígitos). Vazia = um mapa novo a cada partida. */
	public String seed = "";

	public static final float SENSIBILIDADE_MINIMA = 0.25f;
	public static final float SENSIBILIDADE_MAXIMA = 3.0f;

	private static Opcoes atual;

	/** As opções em uso, lidas do arquivo na primeira vez. */
	public static Opcoes atual() {
		if (atual == null) {
			atual = carregar(arquivoPadrao());
		}
		return atual;
	}

	static File arquivoPadrao() {
		return new File(Caminhos.pastaBase(), ARQUIVO);
	}

	/** Lê o arquivo; valores ausentes ou inválidos ficam com o padrão. */
	public static Opcoes carregar(File arquivo) {
		Opcoes o = new Opcoes();
		if (!arquivo.exists()) {
			return o;
		}
		Properties p = new Properties();
		try (Reader r = Files.newBufferedReader(arquivo.toPath(), StandardCharsets.UTF_8)) {
			p.load(r);
		} catch (IOException e) {
			System.out.println("[Opcoes] não foi possível ler " + arquivo + ": " + e.getMessage());
			return o;
		}
		o.volumeGeral = lerNumero(p, "volume.geral", o.volumeGeral, 0, 1);
		o.volumeMusica = lerNumero(p, "volume.musica", o.volumeMusica, 0, 1);
		o.volumeEfeitos = lerNumero(p, "volume.efeitos", o.volumeEfeitos, 0, 1);
		o.sensibilidade = lerNumero(p, "mouse.sensibilidade", o.sensibilidade, SENSIBILIDADE_MINIMA,
				SENSIBILIDADE_MAXIMA);
		String seed = p.getProperty("mapa.seed", "").trim();
		o.seed = seed.matches("\\d{0,18}") ? seed : "";
		return o;
	}

	public void salvar() {
		salvar(arquivoPadrao());
	}

	public void salvar(File arquivo) {
		Properties p = new Properties();
		p.setProperty("volume.geral", String.valueOf(volumeGeral));
		p.setProperty("volume.musica", String.valueOf(volumeMusica));
		p.setProperty("volume.efeitos", String.valueOf(volumeEfeitos));
		p.setProperty("mouse.sensibilidade", String.valueOf(sensibilidade));
		p.setProperty("mapa.seed", seed);
		try (Writer w = Files.newBufferedWriter(arquivo.toPath(), StandardCharsets.UTF_8)) {
			p.store(w, "Univali Uncanny - opcoes do jogador");
		} catch (IOException e) {
			System.out.println("[Opcoes] não foi possível salvar " + arquivo + ": " + e.getMessage());
		}
	}

	public Opcoes copia() {
		Opcoes o = new Opcoes();
		o.copiarDe(this);
		return o;
	}

	public void copiarDe(Opcoes o) {
		volumeGeral = o.volumeGeral;
		volumeMusica = o.volumeMusica;
		volumeEfeitos = o.volumeEfeitos;
		sensibilidade = o.sensibilidade;
		seed = o.seed;
	}

	/** A seed da próxima partida: a digitada nas opções ou uma nova, sorteada. */
	public long seedDaPartida(Random random) {
		if (seed.isEmpty()) {
			return random.nextInt(1_000_000); // número curto, fácil de anotar
		}
		return Long.parseLong(seed);
	}

	private static float lerNumero(Properties p, String chave, float padrao, float minimo, float maximo) {
		try {
			float v = Float.parseFloat(p.getProperty(chave, String.valueOf(padrao)));
			return Math.max(minimo, Math.min(maximo, v));
		} catch (NumberFormatException e) {
			return padrao;
		}
	}
}
