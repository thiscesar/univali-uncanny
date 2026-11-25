package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import entidades.PredioUnivali;
import fase.AndarUnivali;
import shaders.ShaderObjeto;
import util.Caminhos;
import util.TextureLoader;

/**
 * Desenha a Univali com caixas (MalhaCaixas), uma malha por material, cada uma com a sua textura
 * gerada por código e o seu brilho próprio.
 *
 * Por fora: a casca de concreto manchado (com o vão da porta), laje, marquise, colunas, calçada,
 * letreiro UNIVALI, postes, as janelas da fachada (acesas, apagadas, uma piscando, um vulto) e,
 * de longe, um brilho em cada janela acesa que atravessa a neblina.
 *
 * Por dentro (o térreo): piso de granilite, forro, paredes pintadas como corredor de escola, as
 * paredes do corredor e das salas (com placas, armários, murais e a mobília de cada sala), lâmpadas no teto (acesas, piscando ou queimadas), a placa de SAÍDA,
 * a porta de entrada e a da sala do professor (enquanto trancadas), o crachá numa mesinha (só depois que o professor responde pela porta) e a
 * sala do professor: mesa, papéis, luminária e ele, um boneco de caixas que vira a cabeça para
 * acompanhar o jogador.
 */
public class DesenhoPredio {

	private static final float[] COR_JANELA = { 0.30f, 0.24f, 0.13f }, COR_LAMPADA = { 0.9f, 0.75f, 0.5f };
	private static final float[] COR_CRACHA = { 0.5f, 0.42f, 0.12f };
	private static final float[] TUDO = { 0, 0, 1, 1 };

	private final PredioUnivali predio;
	// por fora
	private final MalhaCaixas casca, concreto, letreiro, metal, lampadasPoste, acesas, apagadas, piscando, vulto;
	// por dentro
	private final MalhaCaixas pintura, piso, forro, saida, portaEntrada, portaProfessor;
	/** Uma malha por lâmpada do teto (null nas queimadas), na ordem de andar.lampadas(): cada uma com o seu brilho. */
	private final List<MalhaCaixas> lampadas = new ArrayList<>();
	/** O brilho de cada lâmpada agora (0 a 1), escrito pela TelaJogo a partir das FalhasDeLuz. */
	public final float[] brilhoLampadas;
	private final MalhaCaixas portasTrancadas, madeira, metalInterno, lousas, monitores, telas, louca, armarios, murais;
	private final MalhaCaixas logoSaguao;
	/** A folha de uma porta de sala (dobradiça em x = 0, abre girando em volta dela) e o ângulo de cada porta que abre sozinha. */
	private final MalhaCaixas folhaPorta;
	public final float[] anguloPortas;
	/** O relógio da sala onde o jogador acorda, parado em 23:58: o mostrador e o ponteiro dos segundos (travado). */
	private final MalhaCaixas relogio, ponteiroSegundos;
	private final float[] centroRelogio;
	/** A TV de tubo no balcão do saguão: o gabinete e a tela (chuvisco animado, às vezes o logo). */
	private final MalhaCaixas tvGabinete, tvTela;
	/** Onde fica a tela da TV, no modelo: {x, y, z} (a TelaJogo põe ali a luz e o som). */
	public static final float[] TV = { 1.8f, 1.4f, 8.7f };
	/** A frase escrita com sangue na parede do fim do corredor, do lado da sala do professor. */
	private final MalhaCaixas escritaSangue;
	private final List<MalhaCaixas> placas = new ArrayList<>();
	private final List<String> nomesPlacas = new ArrayList<>();
	private final MalhaCaixas cracha, carta, mesa, papeis, luminaria, cadeira;
	private final MalhaCaixas corpoProfessor, cabecaProfessor;
	private final PontosBrilhantes halos;
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private float tempo = 0;
	private int janelaMontada = -1;
	/** Para onde a cabeça do professor está virada (radianos, no modelo dele) e para onde ela quer ir. */
	private float cabeca = 0, cabecaAlvo = 0;

	// o que muda durante o jogo
	public boolean portaEntradaAberta = false, portaProfessorAberta = false, crachaPego = false;
	/** A escopeta e a carta já foram pegas no balcão (a carta some, e o brilho também). */
	public boolean escopetaPega = false;
	/** O crachá só aparece depois que o professor diz, lá de dentro, que sem ele ninguém entra. */
	public boolean crachaVisivel = false;

	/** Só na thread principal. */
	public DesenhoPredio(PredioUnivali p) {
		this.predio = p;
		float L = PredioUnivali.LARGURA / 2, P = PredioUnivali.PROFUNDIDADE / 2, A = PredioUnivali.ALTURA;
		float pe = PredioUnivali.PE_DIREITO, e = PredioUnivali.ESPESSURA, vao = AndarUnivali.VAO_ENTRADA / 2;

		// a casca: as paredes de fora inteiras (até a laje) e a verga em cima da porta; o concreto
		// se repete a cada 4 m (u e t passam de 1: GL_REPEAT)
		casca = new MalhaCaixas();
		for (AndarUnivali.Caixa c : p.paredesNoModelo().subList(0, 5)) {
			float u = Math.max(c.xMax() - c.xMin(), c.zMax() - c.zMin()) / 4;
			float[] rep = { 0, 0, u, A / 4 };
			casca.caixa(c.xMin(), c.xMax(), 0, A, c.zMin(), c.zMax(), rep, rep, rep);
		}
		casca.caixa(-vao, vao, 3.0f, A, P - e, P, new float[] { 0, 0, 0.75f, 2.25f }, new float[] { 0, 0, 0.75f, 2.25f }, TUDO);
		casca.enviar();

		concreto = new MalhaCaixas()
				.caixa(-L - 0.3f, L + 0.3f, A, A + 0.4f, -P - 0.3f, P + 0.3f, TUDO, TUDO, TUDO) // laje
				.caixa(-6, 6, 3.6f, 3.9f, P, P + 4.2f, TUDO, TUDO, TUDO)                        // marquise
				.caixa(-5.6f, -5.2f, 0, 3.6f, P + 3.6f, P + 4.0f, TUDO, TUDO, TUDO)             // colunas
				.caixa(5.2f, 5.6f, 0, 3.6f, P + 3.6f, P + 4.0f, TUDO, TUDO, TUDO)
				.caixa(-7, 7, 0, 0.15f, P, P + 5, TUDO, TUDO, TUDO)                             // calçada
				.enviar();
		letreiro = new MalhaCaixas().caixa(-4.5f, 4.5f, 3.95f, 5.15f, P + 3.9f, P + 4.05f, TUDO, TUDO, TUDO).enviar();
		metal = new MalhaCaixas();
		lampadasPoste = new MalhaCaixas();
		for (float lado : new float[] { -6, 6 }) {
			float z = P + 4;
			metal.caixa(lado - 0.08f, lado + 0.08f, 0, 5, z - 0.08f, z + 0.08f, TUDO, TUDO, TUDO);
			lampadasPoste.caixa(lado - 0.3f, lado + 0.3f, 5, 5.3f, z - 0.3f, z + 0.3f, TUDO, TUDO, TUDO);
		}
		metal.enviar();
		lampadasPoste.enviar();

		acesas = new MalhaCaixas();
		apagadas = new MalhaCaixas();
		piscando = new MalhaCaixas();
		halos = new PontosBrilhantes(p.janelas().size() + 8);
		for (int i = 0; i < p.janelas().size(); i++) {
			PredioUnivali.Janela j = p.janelas().get(i);
			MalhaCaixas destino = i == p.janelaPiscando() ? piscando : j.acesa() ? acesas : apagadas;
			janela(destino, j, 0.02f, PredioUnivali.JANELA_LARGURA / 2, PredioUnivali.JANELA_ALTURA / 2, 0);
		}
		acesas.enviar();
		apagadas.enviar();
		piscando.enviar();
		vulto = new MalhaCaixas(); // montada a cada troca de janela (ver desenhar)

		// ---------- por dentro ----------
		// a pintura das paredes: forra o lado de dentro da casca e todas as paredes internas
		pintura = new MalhaCaixas();
		float f = 0.02f; // um pouco para dentro, para não brigar com a casca no mesmo lugar
		forrar(pintura, -L + e, L - e, -P + e, -P + e + f, pe);         // fundos
		forrar(pintura, -L + e, -L + e + f, -P + e, P - e, pe);         // esquerda
		forrar(pintura, L - e - f, L - e, -P + e, P - e, pe);           // direita
		forrar(pintura, -L + e, -vao, P - e - f, P - e, pe);            // frente
		forrar(pintura, vao, L - e, P - e - f, P - e, pe);
		forrar(pintura, -vao, vao, P - e - f, P - e, 0);                // (só a verga, abaixo)
		pintura.caixa(-vao, vao, 3.0f, pe, P - e - f, P - e, TUDO, TUDO, TUDO);
		for (AndarUnivali.Caixa c : p.andar.paredes()) {
			forrar(pintura, c.xMin(), c.xMax(), c.zMin(), c.zMax(), pe);
		}
		pintura.enviar();
		piso = new MalhaCaixas().caixa(-L + e, L - e, 0, 0.06f, -P + e, P - e, new float[] { 0, 0, L, P },
				new float[] { 0, 0, L, P }, new float[] { 0, 0, L, 0.1f }).enviar();
		forro = new MalhaCaixas().caixa(-L + e, L - e, pe, pe + 0.1f, -P + e, P - e, new float[] { 0, 0, L / 1.2f, P / 1.2f },
				new float[] { 0, 0, L / 1.2f, P / 1.2f }, new float[] { 0, 0, L / 1.2f, P / 1.2f }).enviar();

		// lâmpadas no teto, uma por sala: a maioria apagada (no desenho, junto com o forro escuro),
		// umas acesas fracas, umas piscando
		// as lâmpadas do teto (as queimadas não aparecem: o forro escuro já é a lâmpada apagada)
		brilhoLampadas = new float[p.andar.lampadas().size()];
		for (AndarUnivali.Lampada l : p.andar.lampadas()) {
			boolean queimada = l.estado() == AndarUnivali.Lampada.Estado.QUEIMADA;
			lampadas.add(queimada ? null : new MalhaCaixas()
					.caixa(l.x() - 0.6f, l.x() + 0.6f, pe - 0.04f, pe, l.z() - 0.3f, l.z() + 0.3f, TUDO, TUDO, TUDO).enviar());
			brilhoLampadas[lampadas.size() - 1] = queimada ? 0 : 1;
		}
		saida = new MalhaCaixas().caixa(-0.6f, 0.6f, 3.15f, 3.45f, P - e - 0.12f, P - e - 0.04f, TUDO, TUDO, TUDO).enviar();
		portaEntrada = new MalhaCaixas().caixa(-vao, vao, 0, 3.0f, P - 0.2f, P - 0.12f, TUDO, TUDO, TUDO).enviar();
		AndarUnivali.Caixa pp = p.andar.portaProfessor();
		portaProfessor = new MalhaCaixas().caixa(pp.xMin(), pp.xMax(), 0, 2.3f, pp.zMin(), pp.zMax(), TUDO, TUDO, TUDO)
				.enviar();
		portasTrancadas = new MalhaCaixas();
		for (AndarUnivali.Sala sala : p.andar.salas()) {
			if (sala.trancada() && sala.tipo() != AndarUnivali.Tipo.PROFESSORES) {
				AndarUnivali.Caixa c = p.andar.porta(sala);
				portasTrancadas.caixa(c.xMin(), c.xMax(), 0, 2.3f, c.zMin(), c.zMax(), TUDO, TUDO, TUDO);
			}
		}
		portasTrancadas.enviar();
		// a folha das portas que abrem sozinhas: do tamanho do vão, com a dobradiça na origem
		folhaPorta = new MalhaCaixas().caixa(0, AndarUnivali.VAO_PORTA, 0, 2.3f, -0.03f, 0.03f, TUDO, TUDO, TUDO).enviar();
		anguloPortas = new float[p.andar.portasQueAbremSozinhas().size()];

		// os móveis de cada sala, conforme o tipo
		madeira = new MalhaCaixas();
		metalInterno = new MalhaCaixas();
		lousas = new MalhaCaixas();
		monitores = new MalhaCaixas();
		telas = new MalhaCaixas();
		louca = new MalhaCaixas();
		for (AndarUnivali.Sala sala : p.andar.salas()) {
			mobiliar(sala);
		}
		// o saguão: balcão de atendimento, dois bancos e o letreiro da universidade na parede
		madeira.caixa(-3, 3, 0, 1.05f, 8.6f, 9.4f, TUDO, TUDO, TUDO);
		madeira.caixa(-3.1f, 3.1f, 1.05f, 1.12f, 8.5f, 9.5f, TUDO, TUDO, TUDO);
		for (float lado : new float[] { -1, 1 }) {
			float x = lado * (AndarUnivali.MEIO_SAGUAO - 0.6f);
			metalInterno.caixa(x - 0.25f, x + 0.25f, 0.42f, 0.48f, 4.5f, 7.5f, TUDO, TUDO, TUDO);
			metalInterno.caixa(x - 0.05f, x + 0.05f, 0, 0.42f, 5.8f, 6.2f, TUDO, TUDO, TUDO);
		}
		// o relógio: na parede da porta da sala onde o jogador acorda (a da frente do corredor), ao lado
		// da porta e acima dela, virado para dentro da sala (+z); o jogador acorda olhando para lá
		AndarUnivali.Sala inicio = p.andar.inicio();
		float rx = inicio.porta()[0] + 1.4f;
		if (rx + 0.3f > inicio.xMax()) rx = inicio.porta()[0] - 1.4f;
		float rz = AndarUnivali.MEIO_CORREDOR + AndarUnivali.ESPESSURA / 2; // a face da parede, do lado da sala
		centroRelogio = new float[] { rx, 2.75f, rz + 0.045f };
		float[] canto = { 0, 0, 0.01f, 0.01f }; // o canto transparente: as bordas da caixa somem e ele fica redondo
		relogio = new MalhaCaixas().caixa(rx - 0.26f, rx + 0.26f, 2.49f, 3.01f, rz, rz + 0.04f, TUDO, canto, canto).enviar();
		ponteiroSegundos = new MalhaCaixas().caixa(-0.005f, 0.005f, -0.04f, 0.21f, 0, 0.004f, TUDO, TUDO, TUDO).enviar();

		// a TV de tubo no balcão, na ponta direita, com a tela virada para o corredor (-z): quem
		// vai para a saída a vê de frente. A face de trás da caixa (-z) é lida certo de lá.
		float[] vazio = { 0, 0, 0.01f, 0.01f };
		tvGabinete = new MalhaCaixas()
				.caixa(1.45f, 2.15f, 1.12f, 1.68f, 8.75f, 9.32f, TUDO, TUDO, TUDO)  // a caixa
				.caixa(1.62f, 1.98f, 1.68f, 1.71f, 8.9f, 9.15f, TUDO, TUDO, TUDO)   // a saída de ar em cima
				.caixa(1.98f, 2.08f, 1.2f, 1.3f, 8.72f, 8.75f, TUDO, TUDO, TUDO)    // os botões
				.caixa(1.98f, 2.08f, 1.34f, 1.44f, 8.72f, 8.75f, TUDO, TUDO, TUDO).enviar();
		tvTela = new MalhaCaixas().caixa(1.5f, 1.95f, 1.18f, 1.62f, 8.735f, 8.75f, vazio, TUDO, vazio).enviar();
		logoSaguao = new MalhaCaixas().caixa(-AndarUnivali.MEIO_SAGUAO + 0.12f, -AndarUnivali.MEIO_SAGUAO + 0.16f, 1.6f, 2.8f,
				5.5f, 10.5f, TUDO, TUDO, TUDO).enviar();
		// a escrita de sangue: na parede do fim do corredor do lado da sala do professor (a sala fica
		// num canto), de frente para quem anda pelo corredor até ela; colada na pintura da parede
		boolean esquerda = p.andar.professores().xMin() <= AndarUnivali.X0 + 0.01f;
		float xParede = esquerda ? -L + e + f : L - e - f;
		escritaSangue = new MalhaCaixas().placaEmX(esquerda ? xParede + 0.01f : xParede - 0.01f, 0.95f, 3.21f,
				-AndarUnivali.MEIO_CORREDOR + 0.15f, AndarUnivali.MEIO_CORREDOR - 0.15f, esquerda, TUDO).enviar();

		// o corredor: armários na parede dos fundos e murais na da frente, entre as portas
		armarios = new MalhaCaixas();
		murais = new MalhaCaixas();
		float c = AndarUnivali.MEIO_CORREDOR, et = AndarUnivali.ESPESSURA / 2;
		for (float x = -L + 1.2f; x < L - 1.2f; x += 1.0f) {
			if (!pertoDeUmaPorta(x, false, 1.4f)) {
				armarios.caixa(x - 0.48f, x + 0.48f, 0, 1.9f, -c + et, -c + et + 0.45f, TUDO, TUDO, TUDO);
			}
		}
		for (float x = -L + 2.5f; x < L - 2.5f; x += 3.5f) {
			if (Math.abs(x) > AndarUnivali.MEIO_SAGUAO + 1.2f && !pertoDeUmaPorta(x, true, 2.4f)) {
				murais.caixa(x - 1.1f, x + 1.1f, 1.1f, 2.3f, c - et - 0.04f, c - et, TUDO, TUDO, TUDO);
			}
		}
		// o bebedouro, perto do saguão
		louca.caixa(AndarUnivali.MEIO_SAGUAO + 0.4f, AndarUnivali.MEIO_SAGUAO + 0.9f, 0, 1.0f, c - et - 0.4f, c - et,
				TUDO, TUDO, TUDO);
		madeira.enviar();
		metalInterno.enviar();
		lousas.enviar();
		monitores.enviar();
		telas.enviar();
		louca.enviar();
		armarios.enviar();
		murais.enviar();

		// a placa de cada porta, na parede do corredor, ao lado do vão
		for (AndarUnivali.Sala sala : p.andar.salas()) {
			float[] porta = sala.porta();
			float x = porta[0] + 1.25f, z = sala.naFrente() ? c - et - 0.05f : -c + et + 0.05f;
			placas.add(new MalhaCaixas().caixa(x - 0.5f, x + 0.5f, 2.0f, 2.3f, Math.min(z, z + (sala.naFrente() ? -0.03f : 0.03f)),
					Math.max(z, z + (sala.naFrente() ? -0.03f : 0.03f)), TUDO, TUDO, TUDO).enviar());
			nomesPlacas.add(sala.nome());
		}

		// o crachá, em cima da mesa perto da lousa da sala dele
		float[] k = p.chaveNoModelo();
		cracha = new MalhaCaixas().caixa(k[0] - 0.06f, k[0] + 0.06f, 0.76f, 0.78f, k[1] - 0.09f, k[1] + 0.09f, TUDO, TUDO, TUDO)
				.enviar();
		// a carta, uma folha em cima do balcão do saguão, ao lado da escopeta
		float[] cc = PredioUnivali.CARTA;
		carta = new MalhaCaixas().caixa(cc[0] - 0.15f, cc[0] + 0.15f, cc[1], cc[1] + 0.003f, cc[2] - 0.105f, cc[2] + 0.105f, TUDO, TUDO,
				TUDO).enviar();

		// a sala do professor: tudo montado no espaço dele (origem na cadeira, olhando para +z) e
		// depois girado e levado para o lugar pela matriz de modelo
		mesa = new MalhaCaixas()
				.caixa(-0.8f, 0.8f, 0.72f, 0.78f, 0.55f, 1.35f, TUDO, TUDO, TUDO)   // tampo
				.caixa(-0.8f, -0.7f, 0, 0.72f, 0.55f, 1.35f, TUDO, TUDO, TUDO)      // laterais
				.caixa(0.7f, 0.8f, 0, 0.72f, 0.55f, 1.35f, TUDO, TUDO, TUDO)
				.caixa(-0.7f, 0.7f, 0.2f, 0.72f, 1.3f, 1.35f, TUDO, TUDO, TUDO)     // frente (esconde as pernas)
				.enviar();
		papeis = new MalhaCaixas()
				.caixa(-0.65f, -0.35f, 0.78f, 0.98f, 0.7f, 0.92f, TUDO, TUDO, TUDO) // pilhas de provas
				.caixa(-0.3f, -0.02f, 0.78f, 0.86f, 0.62f, 0.84f, TUDO, TUDO, TUDO)
				.caixa(0.05f, 0.4f, 0.78f, 0.8f, 0.75f, 1.0f, TUDO, TUDO, TUDO)
				.enviar();
		luminaria = new MalhaCaixas()
				.caixa(0.55f, 0.6f, 0.78f, 1.15f, 0.9f, 0.95f, TUDO, TUDO, TUDO)
				.caixa(0.42f, 0.72f, 1.1f, 1.2f, 0.85f, 1.05f, TUDO, TUDO, TUDO)
				.enviar();
		cadeira = new MalhaCaixas()
				.caixa(-0.28f, 0.28f, 0.42f, 0.48f, -0.25f, 0.3f, TUDO, TUDO, TUDO)
				.caixa(-0.28f, 0.28f, 0.48f, 1.25f, -0.3f, -0.24f, TUDO, TUDO, TUDO)
				.caixa(-0.04f, 0.04f, 0, 0.42f, -0.04f, 0.04f, TUDO, TUDO, TUDO)
				.enviar();
		// o professor, sentado, como na foto dele: polo preta (com a gola vinho por dentro), ombros
		// largos, barriga, e os braços cruzados na frente do peito (antebraços de fora). A textura
		// da roupa tem quatro partes: polo, pele, calça e a gola.
		float[] polo = { 0, 0, 0.5f, 0.5f }, pele = { 0.5f, 0, 1, 0.5f }, calca = { 0, 0.5f, 0.5f, 1 }, gola = { 0.5f, 0.5f, 1, 1 };
		corpoProfessor = new MalhaCaixas()
				.caixa(-0.21f, 0.21f, 0.45f, 0.6f, -0.15f, 0.45f, calca, calca, calca)     // coxas
				.caixa(-0.21f, 0.21f, 0, 0.45f, 0.33f, 0.5f, calca, calca, calca)          // canelas
				.caixa(-0.22f, 0.22f, 0.6f, 0.86f, -0.12f, 0.17f, polo, polo, polo)        // barriga
				.caixa(-0.23f, 0.23f, 0.86f, 1.12f, -0.13f, 0.15f, polo, polo, polo)       // peito
				.caixa(-0.26f, 0.26f, 1.08f, 1.24f, -0.12f, 0.11f, polo, polo, polo)       // ombros
				.caixa(-0.085f, 0.085f, 1.2f, 1.27f, -0.07f, 0.09f, polo, polo, polo)      // a gola da polo
				.caixa(-0.03f, 0.03f, 1.18f, 1.25f, 0.14f, 0.152f, gola, gola, gola)      // a abertura da polo (vinho)
				.caixa(-0.33f, -0.24f, 1.0f, 1.22f, -0.08f, 0.09f, polo, polo, polo)       // mangas curtas
				.caixa(0.24f, 0.33f, 1.0f, 1.22f, -0.08f, 0.09f, polo, polo, polo)
				.caixa(-0.32f, -0.245f, 0.9f, 1.0f, -0.06f, 0.08f, pele, pele, pele)       // braços
				.caixa(0.245f, 0.32f, 0.9f, 1.0f, -0.06f, 0.08f, pele, pele, pele)
				.caixa(-0.31f, -0.23f, 0.88f, 0.96f, 0.0f, 0.19f, pele, pele, pele)         // cotovelos, indo para a frente
				.caixa(0.23f, 0.31f, 0.95f, 1.03f, 0.02f, 0.21f, pele, pele, pele)
				.caixa(-0.24f, 0.16f, 0.89f, 0.96f, 0.15f, 0.22f, pele, pele, pele)         // antebraços cruzados, um sobre o outro
				.caixa(-0.16f, 0.24f, 0.96f, 1.03f, 0.17f, 0.24f, pele, pele, pele)
				.caixa(-0.22f, -0.15f, 0.97f, 1.04f, 0.13f, 0.2f, pele, pele, pele)         // as mãos, enfiadas nos braços
				.caixa(0.15f, 0.22f, 0.9f, 0.97f, 0.12f, 0.19f, pele, pele, pele)
				.enviar();
		cabecaProfessor = cabecaFacetada();
	}

	/**
	 * A cabeça do professor (no espaço dele, o pescoço em y = 1,3): em vez de um cubo, um prisma de
	 * 8 lados que afina no queixo. As três faces da frente (o rosto e as bochechas) levam a foto
	 * do rosto, como os jogos de PlayStation faziam; os lados e a nuca, pele e cabelo; em cima e
	 * atrás, um volume de cabelo; as orelhas e o pescoço, caixinhas de pele. A textura da cabeça
	 * tem três partes: o rosto (metade esquerda), o lado e a nuca.
	 */
	private static MalhaCaixas cabecaFacetada() {
		float W = 0.115f, D = 0.13f, c = 0.05f;          // meia largura, meia profundidade e o chanfro
		float y0 = 1.32f, y1 = 1.43f, y2 = 1.66f;        // queixo, mandíbula e o alto da cabeça
		// o contorno de cima (de y1 a y2), em volta, começando pela bochecha esquerda (vista de frente)
		float[][] oct = { { -W, D - c }, { -W + c, D }, { W - c, D }, { W, D - c }, { W, -D + c }, { W - c, -D }, { -W + c, -D },
				{ -W, -D + c } };
		// no queixo o contorno afina: mais estreito e um pouco para trás
		float[][] queixo = new float[8][];
		for (int i = 0; i < 8; i++) queixo[i] = new float[] { oct[i][0] * 0.72f, oct[i][1] * 0.85f - 0.01f };
		float diag = c * (float) Math.sqrt(2), frente = 2 * W - 2 * c, total = 2 * diag + frente;
		// o rosto cobre as faces 0 (bochecha esquerda), 1 (frente) e 2 (bochecha direita): u de 0 a 0,5
		float[] uRosto = { 0, 0.5f * diag / total, 0.5f * (diag + frente) / total, 0.5f };
		float[] lado = { 0.5f, 0, 0.75f, 1 }, nuca = { 0.75f, 0, 1, 1 };
		float tMeio = (y2 - y1) / (y2 - y0);             // onde a mandíbula cai na altura da textura
		MalhaCaixas m = new MalhaCaixas();
		for (int i = 0; i < 8; i++) {
			int j = (i + 1) % 8;
			float[] a = oct[i], b = oct[j], qa = queixo[i], qb = queixo[j];
			float[] uvCima, uvBaixo;
			if (i < 3) { // o rosto
				uvCima = new float[] { uRosto[i], 0, uRosto[i + 1], tMeio };
				uvBaixo = new float[] { uRosto[i], tMeio, uRosto[i + 1], 1 };
			} else {
				float[] r = i == 3 || i == 7 ? lado : nuca;
				uvCima = new float[] { r[0], 0, r[2], tMeio };
				uvBaixo = new float[] { r[0], tMeio, r[2], 1 };
			}
			m.quad(new float[] { a[0], y1, a[1] }, new float[] { b[0], y1, b[1] }, new float[] { b[0], y2, b[1] },
					new float[] { a[0], y2, a[1] }, uvCima);
			m.quad(new float[] { qa[0], y0, qa[1] }, new float[] { qb[0], y0, qb[1] }, new float[] { b[0], y1, b[1] },
					new float[] { a[0], y1, a[1] }, uvBaixo);
		}
		float[] cabelo = { 0.76f, 0.02f, 0.99f, 0.2f }, peleLado = { 0.55f, 0.7f, 0.7f, 0.8f };
		m.caixa(-W + 0.004f, W - 0.004f, y2 - 0.012f, y2 + 0.035f, -D + 0.004f, D - 0.014f, cabelo, cabelo, cabelo) // o cabelo em cima
				.caixa(-W - 0.004f, W + 0.004f, y1 + 0.03f, y2 + 0.012f, -D - 0.014f, -D + 0.045f, cabelo, cabelo, cabelo) // e atrás
				.caixa(-W - 0.02f, -W + 0.004f, 1.45f, 1.53f, -0.012f, 0.035f, peleLado, peleLado, peleLado) // orelhas
				.caixa(W - 0.004f, W + 0.02f, 1.45f, 1.53f, -0.012f, 0.035f, peleLado, peleLado, peleLado)
				.caixa(-0.065f, 0.065f, 1.24f, 1.34f, -0.06f, 0.05f, peleLado, peleLado, peleLado); // pescoço
		return m.enviar();
	}

	/** A porta de alguma sala daquele lado do corredor fica a menos de "folga" metros de x? */
	private boolean pertoDeUmaPorta(float x, boolean daFrente, float folga) {
		for (AndarUnivali.Sala s : predio.andar.salas()) {
			if (s.naFrente() == daFrente && Math.abs(s.porta()[0] - x) < folga) return true;
		}
		return false;
	}

	/**
	 * Os móveis de uma sala. Todas (menos banheiros e professores) têm a mesa perto da parede do
	 * fundo; salas de aula têm lousa e 12 carteiras; o laboratório, bancadas com computadores (uns
	 * ligados); a coordenação, mesas e um arquivo; os banheiros, cabines e pias.
	 */
	private void mobiliar(AndarUnivali.Sala sala) {
		float[] c = sala.centro(), d = sala.direcaoDaPorta();
		float fundo = sala.zFundo(), sentido = -d[1]; // sentido: do centro para o fundo (+1 ou -1 em z)
		float largura = sala.xMax() - sala.xMin();
		if (sala.tipo() == AndarUnivali.Tipo.PROFESSORES) {
			// uma estante na lateral
			madeira.caixa(sala.xMin() + 0.15f, sala.xMin() + 0.55f, 0, 2.0f, c[1] - 1.5f, c[1] + 1.5f, TUDO, TUDO, TUDO);
			return;
		}
		if (sala.tipo() == AndarUnivali.Tipo.BANHEIROS) {
			for (int i = 0; i < 3; i++) { // divisórias das cabines, no fundo
				float x = sala.xMin() + 1.2f + i * 1.6f;
				metalInterno.caixa(x - 0.03f, x + 0.03f, 0.15f, 2.0f, zEntre(fundo, fundo - sentido * 1.6f), zEntre2(fundo, fundo - sentido * 1.6f), TUDO, TUDO, TUDO);
			}
			for (int i = 0; i < 3; i++) { // pias na lateral
				float z = c[1] - 1.5f + i * 1.5f;
				louca.caixa(sala.xMax() - 0.65f, sala.xMax() - 0.15f, 0.8f, 0.95f, z - 0.25f, z + 0.25f, TUDO, TUDO, TUDO);
			}
			return;
		}
		// a mesa do professor da sala (é nela que o crachá pode estar)
		float zMesa = c[1] + sentido * (Math.abs(sala.zMax() - sala.zMin()) / 2 - 2.2f);
		madeira.caixa(c[0] - 0.7f, c[0] + 0.7f, 0, 0.76f, zMesa - 0.35f, zMesa + 0.35f, TUDO, TUDO, TUDO);
		switch (sala.tipo()) {
		case AULA -> {
			// a lousa na parede do fundo e as carteiras viradas para ela
			float zl = fundo - sentido * (PredioUnivali.ESPESSURA + 0.04f);
			lousas.caixa(c[0] - Math.min(2.4f, largura / 2 - 0.6f), c[0] + Math.min(2.4f, largura / 2 - 0.6f), 0.9f, 2.2f,
					Math.min(zl, zl - sentido * 0.04f), Math.max(zl, zl - sentido * 0.04f), TUDO, TUDO, TUDO);
			for (float[] cart : AndarUnivali.carteiras(sala)) {
				madeira.caixa(cart[0] - 0.3f, cart[0] + 0.3f, 0.7f, 0.74f, cart[1] - 0.22f, cart[1] + 0.22f, TUDO, TUDO, TUDO);
				metalInterno.caixa(cart[0] - 0.22f, cart[0] + 0.22f, 0.42f, 0.46f, cart[1] - sentido * 0.45f - 0.18f,
						cart[1] - sentido * 0.45f + 0.18f, TUDO, TUDO, TUDO); // o assento, atrás da mesinha
				metalInterno.caixa(cart[0] - 0.03f, cart[0] + 0.03f, 0, 0.7f, cart[1] - 0.03f, cart[1] + 0.03f, TUDO, TUDO, TUDO);
			}
		}
		case LABORATORIO -> {
			for (int b = 0; b < 2; b++) { // duas bancadas compridas com 4 computadores cada
				float zb = c[1] - sentido * 1.2f + sentido * b * 2.6f;
				madeira.caixa(sala.xMin() + 0.8f, sala.xMax() - 0.8f, 0, 0.76f, zb - 0.4f, zb + 0.4f, TUDO, TUDO, TUDO);
				for (int m = 0; m < 4; m++) {
					float x = sala.xMin() + 1.4f + m * (largura - 2.8f) / 3;
					monitores.caixa(x - 0.28f, x + 0.28f, 0.8f, 1.2f, zb - 0.05f, zb + 0.05f, TUDO, TUDO, TUDO);
					if ((m + b) % 3 == 0) { // uns ainda ligados: a tela azul brilha
						float zt = zb - sentido * 0.06f;
						telas.caixa(x - 0.24f, x + 0.24f, 0.84f, 1.16f, Math.min(zt, zt - sentido * 0.01f),
								Math.max(zt, zt - sentido * 0.01f), TUDO, TUDO, TUDO);
					}
				}
			}
		}
		case COORDENACAO -> {
			madeira.caixa(c[0] - 2.2f, c[0] - 0.8f, 0, 0.76f, c[1] - 0.4f, c[1] + 0.4f, TUDO, TUDO, TUDO);
			madeira.caixa(c[0] + 0.8f, c[0] + 2.2f, 0, 0.76f, c[1] - 0.4f, c[1] + 0.4f, TUDO, TUDO, TUDO);
			metalInterno.caixa(sala.xMax() - 0.7f, sala.xMax() - 0.2f, 0, 1.4f, c[1] - 0.3f, c[1] + 0.3f, TUDO, TUDO, TUDO);
		}
		default -> { }
		}
	}

	private static float zEntre(float a, float b) {
		return Math.min(a, b);
	}

	private static float zEntre2(float a, float b) {
		return Math.max(a, b);
	}

	/** Uma parede pintada (do chão até "altura"), por cima do retângulo dado. */
	private static void forrar(MalhaCaixas m, float x0, float x1, float z0, float z1, float altura) {
		if (altura <= 0) return;
		float u = Math.max(x1 - x0, z1 - z0) / 4, t = altura / 4;
		float[] rep = { 0, 0, Math.max(u, 0.05f), t };
		m.caixa(x0, x1, 0, altura, z0, z1, rep, rep, rep);
	}

	/** Uma janela: uma placa fina na fachada, deslocada "frente" metros para fora da parede. */
	private static void janela(MalhaCaixas m, PredioUnivali.Janela j, float espessura, float meiaL, float meiaA, float frente) {
		float cx = j.x() + j.nx() * frente, cz = j.z() + j.nz() * frente;
		if (j.nz() != 0) {
			m.caixa(cx - meiaL, cx + meiaL, j.y() - meiaA, j.y() + meiaA, cz - espessura, cz + espessura, TUDO, TUDO, TUDO);
		} else {
			m.caixa(cx - espessura, cx + espessura, j.y() - meiaA, j.y() + meiaA, cz - meiaL, cz + meiaL, TUDO, TUDO, TUDO);
		}
	}

	/** O tempo anda; a cabeça do professor vira devagar para o jogador (se ele estiver na frente dele). */
	public void atualizar(float dt, float jogadorX, float jogadorZ) {
		tempo += dt;
		float[] prof = predio.professorNoModelo();
		float[] w = predio.noMundo(prof[0], 0, prof[1]);
		float dx = jogadorX - w[0], dz = jogadorZ - w[2];
		if (dx * dx + dz * dz < 64) {
			// a direção do jogador, no espaço do professor: tira o giro do prédio e o dele
			float angulo = (float) Math.atan2(dx, dz) - predio.giro - prof[2];
			angulo = (float) Math.atan2(Math.sin(angulo), Math.cos(angulo));
			cabecaAlvo = Math.max(-1.2f, Math.min(1.2f, angulo));
		} else {
			cabecaAlvo = 0;
		}
		cabeca += (cabecaAlvo - cabeca) * (1 - (float) Math.exp(-4 * dt));
	}

	/** Onde fica a lâmpada da luminária, no mundo (a TelaJogo põe uma luz pontual ali). */
	public float[] luminariaNoMundo() {
		float[] prof = predio.professorNoModelo();
		float c = (float) Math.cos(prof[2]), s = (float) Math.sin(prof[2]);
		float lx = 0.57f, lz = 0.95f; // a lâmpada, no espaço do professor
		return predio.noMundo(prof[0] + c * lx + s * lz, 1.1f, prof[1] - s * lx + c * lz);
	}

	/** O prédio inteiro: as partes sólidas e depois os halos (usado no mapa de sombra). */
	public void desenhar(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		desenharSolido(projecao, visao, ambiente, alturaImagem);
		desenharHalos(projecao, visao, ambiente, alturaImagem);
	}

	/**
	 * As partes sólidas (paredes, móveis, janelas, o professor). A TelaJogo desenha isto ANTES do
	 * terreno: dentro do prédio, o piso já gravou a profundidade e a placa descarta os pixels do
	 * terreno escondidos embaixo dele sem calcular a cor deles (teste de profundidade antecipado).
	 */
	public void desenharSolido(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		glUniform1i(glGetUniformLocation(p, "usaNormalMap"), 0);
		ambiente.enviar(shader);
		glActiveTexture(GL_TEXTURE0);
		Matrix4f doPredio = new Matrix4f();
		doPredio.translate(new Vector3f(predio.x, predio.y, predio.z));
		doPredio.rotate(predio.giro, new Vector3f(0, 1, 0));
		modelo(p, doPredio);

		// por fora
		desenhar(p, casca, "predio: concreto", DesenhoPredio::concreto, 0f, true);
		desenhar(p, concreto, "predio: concreto claro", DesenhoPredio::concretoClaro, 0f, true);
		desenhar(p, letreiro, "predio: letreiro", DesenhoPredio::letreiro, 0.9f, false);
		desenhar(p, metal, "predio: metal", () -> liso(new Color(40, 42, 45)), 0f, false);
		desenhar(p, lampadasPoste, "predio: lampada", () -> liso(new Color(255, 230, 180)), 1.5f, false);
		desenhar(p, acesas, "predio: janela acesa", DesenhoPredio::janelaAcesa, 1.0f, false);
		desenhar(p, apagadas, "predio: janela apagada", DesenhoPredio::janelaApagada, 0.02f, false);
		float pisca = (float) (Math.sin(tempo * 11.0) * Math.sin(tempo * 3.7 + 1.1)); // rajadas irregulares
		desenhar(p, piscando, "predio: janela acesa", DesenhoPredio::janelaAcesa, pisca > 0.1f ? 1.0f : 0.05f, false);
		int jv = predio.janelaVulto();
		if (jv >= 0) {
			if (jv != janelaMontada) { // o vulto trocou de janela: a placa da silhueta é montada de novo
				vulto.liberar();
				PredioUnivali.Janela j = predio.janelas().get(jv);
				janela(vulto, new PredioUnivali.Janela(j.x(), j.y() - 0.15f, j.z(), j.nx(), j.nz(), true), 0.005f, 0.45f,
						0.65f, 0.04f);
				vulto.enviar();
				janelaMontada = jv;
			}
			desenhar(p, vulto, "predio: vulto", DesenhoPredio::vulto, 0f, false);
		}

		// por dentro
		desenhar(p, pintura, "predio: pintura", DesenhoPredio::pintura, 0f, true);
		desenhar(p, piso, "predio: piso", DesenhoPredio::piso, 0f, true);
		desenhar(p, forro, "predio: forro", DesenhoPredio::forro, 0f, true);
		for (int i = 0; i < lampadas.size(); i++) { // cada lâmpada com o seu brilho (as que falham piscam)
			if (lampadas.get(i) == null) continue;
			desenhar(p, lampadas.get(i), "predio: lampada do teto", () -> liso(new Color(210, 225, 230)),
					0.02f + 0.6f * brilhoLampadas[i], false);
		}
		desenhar(p, saida, "predio: saida", DesenhoPredio::placaSaida, 1.0f, false);
		if (!portaEntradaAberta) desenhar(p, portaEntrada, "predio: porta", DesenhoPredio::porta, 0.05f, false);
		if (!portaProfessorAberta) desenhar(p, portaProfessor, "predio: porta de madeira", DesenhoPredio::portaMadeira, 0f, false);
		desenhar(p, portasTrancadas, "predio: porta de madeira", DesenhoPredio::portaMadeira, 0f, false);
		// as portas que abrem sozinhas: cada uma girada em volta da dobradiça (no canto do vão), para dentro da sala
		java.util.List<AndarUnivali.Sala> sozinhas = predio.andar.portasQueAbremSozinhas();
		for (int i = 0; i < sozinhas.size(); i++) {
			AndarUnivali.Sala s = sozinhas.get(i);
			AndarUnivali.Caixa vao = predio.andar.porta(s);
			Matrix4f m = new Matrix4f(doPredio);
			m.translate(new Vector3f(vao.xMin(), 0, (vao.zMin() + vao.zMax()) / 2));
			// girar +x para +z pede ângulo negativo (em y, +x vai para (cos, 0, -sen)); a sala da frente fica em +z
			float ang = (float) Math.toRadians(anguloPortas[i]);
			m.rotate(s.naFrente() ? -ang : ang, new Vector3f(0, 1, 0));
			modelo(p, m);
			desenhar(p, folhaPorta, "predio: porta de madeira", DesenhoPredio::portaMadeira, 0f, false);
		}
		modelo(p, doPredio);
		desenhar(p, madeira, "predio: madeira", DesenhoPredio::madeira, 0f, false);
		desenhar(p, metalInterno, "predio: metal", () -> liso(new Color(40, 42, 45)), 0f, false);
		desenhar(p, lousas, "predio: lousa", DesenhoPredio::lousa, 0.03f, false);
		desenhar(p, monitores, "predio: monitor", () -> liso(new Color(25, 25, 28)), 0f, false);
		desenhar(p, telas, "predio: tela", DesenhoPredio::tela, 0.8f, false);
		desenhar(p, louca, "predio: louca", () -> liso(new Color(215, 215, 210)), 0f, false);
		desenhar(p, armarios, "predio: armario", DesenhoPredio::armario, 0f, false);
		desenhar(p, murais, "predio: mural", DesenhoPredio::mural, 0.04f, false);
		desenhar(p, logoSaguao, "predio: letreiro", DesenhoPredio::letreiro, 0.5f, false);
		desenhar(p, escritaSangue, "predio: escrita de sangue", DesenhoPredio::escritaSangue, 0.04f, false);
		desenhar(p, relogio, "predio: relogio", DesenhoPredio::relogio, 0.03f, false);
		// o ponteiro dos segundos, travado: a cada segundo ele avança um tique e volta
		Matrix4f doPonteiro = new Matrix4f(doPredio);
		doPonteiro.translate(new Vector3f(centroRelogio[0], centroRelogio[1], centroRelogio[2]));
		float segundos = 53 + (tempo % 1.0f < 0.12f ? 1 : 0);
		doPonteiro.rotate((float) Math.toRadians(-segundos * 6), new Vector3f(0, 0, 1)); // horário, visto de +z
		modelo(p, doPonteiro);
		desenhar(p, ponteiroSegundos, "predio: ponteiro", () -> liso(new Color(150, 20, 18)), 0.02f, false);
		modelo(p, doPredio);
		desenhar(p, tvGabinete, "predio: plastico da tv", () -> liso(new Color(38, 34, 32)), 0f, false);
		// a tela: um quadro de chuvisco a cada 70 ms; por meio segundo a cada 7 s, o logo atravessa o chuvisco
		int quadro = (int) (tempo / 0.07f) % 4;
		boolean logo = tempo % 7.0f < 0.5f;
		desenhar(p, tvTela, logo ? "predio: tv logo" : "predio: tv chuvisco " + quadro,
				logo ? () -> chuvisco(99, true) : () -> chuvisco(quadro, false), 0.9f, false);
		for (int i = 0; i < placas.size(); i++) {
			String nome = nomesPlacas.get(i);
			desenhar(p, placas.get(i), "placa da porta: " + nome, () -> placaDaPorta(nome), 0.2f, false);
		}
		if (crachaVisivel && !crachaPego) desenhar(p, cracha, "predio: cracha", DesenhoPredio::cracha, 0.6f, false);
		if (!escopetaPega) desenhar(p, carta, "predio: carta", DesenhoPredio::folhaDaCarta, 0.35f, false);

		// a sala do professor, no espaço dele
		float[] prof = predio.professorNoModelo();
		Matrix4f doProfessor = new Matrix4f(doPredio);
		doProfessor.translate(new Vector3f(prof[0], 0, prof[1]));
		doProfessor.rotate(prof[2], new Vector3f(0, 1, 0));
		modelo(p, doProfessor);
		desenhar(p, mesa, "predio: madeira", DesenhoPredio::madeira, 0f, false);
		desenhar(p, cadeira, "predio: metal", () -> liso(new Color(40, 42, 45)), 0f, false);
		desenhar(p, papeis, "predio: papel", () -> liso(new Color(225, 220, 205)), 0.05f, false);
		desenhar(p, luminaria, "predio: luminaria", () -> liso(new Color(40, 110, 60)), 0.5f, false);
		desenhar(p, corpoProfessor, "professor: roupa", DesenhoPredio::roupa, 0f, false);
		Matrix4f doPescoco = new Matrix4f(doProfessor);
		doPescoco.translate(new Vector3f(0, 1.3f, 0));
		doPescoco.rotate(cabeca, new Vector3f(0, 1, 0));
		doPescoco.translate(new Vector3f(0, -1.3f, 0));
		modelo(p, doPescoco);
		desenhar(p, cabecaProfessor, "professor: cabeca", DesenhoPredio::cabeca, 0f, false);
		shader.stop();
	}

	/**
	 * Os halos das janelas e lâmpadas: transparentes e sem gravar profundidade, por isso vêm depois
	 * de tudo o que é sólido (senão o terreno, desenhado depois, os apagaria).
	 */
	public void desenharHalos(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
		float pisca = (float) (Math.sin(tempo * 11.0) * Math.sin(tempo * 3.7 + 1.1)); // a mesma conta das janelas
		// de longe: um brilho em cada janela acesa e em cada lâmpada, que atravessa a neblina
		halos.limpar();
		for (int i = 0; i < predio.janelas().size(); i++) {
			PredioUnivali.Janela j = predio.janelas().get(i);
			if (!j.acesa() || (i == predio.janelaPiscando() && pisca <= 0.1f)) continue;
			float[] w = predio.noMundo(j.x() + j.nx() * 0.4f, j.y(), j.z() + j.nz() * 0.4f);
			halos.adicionar(w[0], w[1], w[2]);
		}
		halos.desenhar(5.0f, COR_JANELA, projecao, visao, ambiente, alturaImagem, 0.14f);
		halos.limpar();
		for (float lado : new float[] { -6, 6 }) {
			float[] w = predio.noMundo(lado, 5.0f, PredioUnivali.PROFUNDIDADE / 2 + 4);
			halos.adicionar(w[0], w[1], w[2]);
		}
		halos.desenhar(2.0f, COR_LAMPADA, projecao, visao, ambiente, alturaImagem, 0.3f);
		if (!escopetaPega) { // um brilho fraco em cima do balcão: tem algo ali
			halos.limpar();
			float[] w = predio.noMundo(-0.2f, 1.22f, PredioUnivali.ESCOPETA[2]);
			halos.adicionar(w[0], w[1], w[2]);
			halos.desenhar(0.45f, COR_CRACHA, projecao, visao, ambiente, alturaImagem);
		}
		if (crachaVisivel && !crachaPego) {
			halos.limpar();
			float[] k = predio.chaveNoModelo();
			float[] w = predio.noMundo(k[0], 0.8f, k[1]);
			halos.adicionar(w[0], w[1], w[2]);
			halos.desenhar(0.6f, COR_CRACHA, projecao, visao, ambiente, alturaImagem);
		}
	}

	private void modelo(int programa, Matrix4f m) {
		buffer.clear();
		m.storeTranspose(buffer);
		buffer.flip();
		glUniformMatrix4fv(glGetUniformLocation(programa, "model"), false, buffer);
	}

	private void desenhar(int programa, MalhaCaixas malha, String chave, Supplier<BufferedImage> textura, float emissividade,
			boolean repetir) {
		glUniform1f(glGetUniformLocation(programa, "emissividade"), emissividade);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada(chave, textura, repetir, false).id());
		malha.desenhar();
	}

	public void liberar() {
		for (MalhaCaixas m : new MalhaCaixas[] { casca, concreto, letreiro, metal, lampadasPoste, acesas, apagadas, piscando,
				vulto, pintura, piso, forro, saida, escritaSangue, folhaPorta, tvGabinete, tvTela, relogio, ponteiroSegundos, portaEntrada, portaProfessor, portasTrancadas,
				madeira, metalInterno, lousas, monitores, telas, louca, armarios, murais, logoSaguao, cracha, mesa, papeis,
				luminaria, cadeira, corpoProfessor, cabecaProfessor }) {
			m.liberar();
		}
		for (MalhaCaixas m : placas) m.liberar();
		for (MalhaCaixas m : lampadas) if (m != null) m.liberar();
		halos.liberar();
	}

	// ---------- texturas (sem OpenGL) ----------

	private static Graphics2D pincel(BufferedImage img) {
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g;
	}

	/** Concreto pintado, com granulado, uma junta horizontal e manchas de umidade escorrendo. */
	public static BufferedImage concreto() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(41);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				int v = 150 + r.nextInt(18);
				img.setRGB(x, y, new Color(v, v - 6, v - 16).getRGB());
			}
		}
		Graphics2D g = pincel(img);
		g.setColor(new Color(90, 86, 80, 160));
		g.drawLine(0, 63, 63, 63); // junta entre andares
		for (int i = 0; i < 6; i++) { // escorridos de umidade
			int x = r.nextInt(64), comprimento = 12 + r.nextInt(40);
			g.setPaint(new GradientPaint(x, 0, new Color(60, 58, 50, 110), x, comprimento, new Color(60, 58, 50, 0)));
			g.fillRect(x, 0, 2 + r.nextInt(3), comprimento);
		}
		g.dispose();
		return img;
	}

	public static BufferedImage concretoClaro() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(43);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int v = 175 + r.nextInt(15);
				img.setRGB(x, y, new Color(v, v - 4, v - 10).getRGB());
			}
		}
		return img;
	}

	/** Janela acesa: vidro amarelado mais claro no meio, caixilho escuro em cruz e as linhas da persiana. */
	public static BufferedImage janelaAcesa() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setPaint(new GradientPaint(0, 0, new Color(255, 214, 140), 0, 32, new Color(220, 150, 70)));
		g.fillRect(0, 0, 32, 32);
		g.setColor(new Color(190, 130, 60, 120));
		for (int y = 3; y < 30; y += 3) g.drawLine(2, y, 29, y); // persiana
		g.setColor(new Color(40, 38, 36));
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, 30, 30);
		g.drawLine(16, 0, 16, 32);
		g.drawLine(0, 16, 32, 16);
		g.dispose();
		return img;
	}

	/** Janela apagada: vidro escuro azulado com um reflexo fraco na diagonal. */
	public static BufferedImage janelaApagada() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(16, 22, 32));
		g.fillRect(0, 0, 32, 32);
		g.setColor(new Color(70, 85, 105, 90));
		g.setStroke(new BasicStroke(3));
		g.drawLine(4, 28, 28, 4);
		g.setColor(new Color(40, 38, 36));
		g.setStroke(new BasicStroke(2));
		g.drawRect(1, 1, 30, 30);
		g.drawLine(16, 0, 16, 32);
		g.drawLine(0, 16, 32, 16);
		g.dispose();
		return img;
	}

	/** Porta de vidro dupla, com moldura de metal e puxadores. */
	public static BufferedImage porta() {
		BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(20, 28, 36));
		g.fillRect(0, 0, 64, 48);
		g.setColor(new Color(110, 112, 115));
		g.setStroke(new BasicStroke(3));
		g.drawRect(2, 2, 60, 46);
		g.drawLine(32, 2, 32, 48);
		g.fillRect(27, 22, 3, 8);
		g.fillRect(34, 22, 3, 8);
		g.dispose();
		return img;
	}

	/** O letreiro: "UNIVALI" em branco sobre o azul da universidade. */
	public static BufferedImage letreiro() {
		BufferedImage img = new BufferedImage(256, 40, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(0, 63, 135));
		g.fillRect(0, 0, 256, 40);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
		int w = g.getFontMetrics().stringWidth("UNIVALI");
		g.drawString("UNIVALI", (256 - w) / 2, 31);
		g.dispose();
		return img;
	}

	/**
	 * O mostrador do relógio de parede (128 x 128, cantos transparentes: fica redondo): aro escuro,
	 * fundo amarelado, os tiques e os números 12, 3, 6 e 9, os ponteiros parados em 23:58 e uma
	 * rachadura no vidro. O ponteiro dos segundos é à parte (ele treme).
	 */
	public static BufferedImage relogio() {
		BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(38, 36, 40));
		g.fillOval(1, 1, 126, 126);
		g.setColor(new Color(212, 204, 182));
		g.fillOval(9, 9, 110, 110);
		g.setColor(new Color(30, 28, 30));
		for (int i = 0; i < 60; i++) { // os tiques: grandes nas horas
			double a = Math.toRadians(i * 6);
			float r0 = i % 5 == 0 ? 44 : 49, r1 = 53;
			g.setStroke(new BasicStroke(i % 5 == 0 ? 3 : 1));
			g.drawLine(64 + (int) (Math.sin(a) * r0), 64 - (int) (Math.cos(a) * r0), 64 + (int) (Math.sin(a) * r1),
					64 - (int) (Math.cos(a) * r1));
		}
		g.setFont(new Font(Font.SERIF, Font.BOLD, 15));
		String[] numeros = { "12", "3", "6", "9" };
		int[][] pos = { { 56, 34 }, { 88, 69 }, { 60, 101 }, { 31, 69 } };
		for (int i = 0; i < 4; i++) g.drawString(numeros[i], pos[i][0], pos[i][1]);
		// 23:58: o ponteiro das horas quase no 12, o dos minutos no 58
		ponteiro(g, (11 + 58 / 60.0) * 30, 28, 5);
		ponteiro(g, 58 * 6, 42, 3);
		g.fillOval(60, 60, 8, 8);
		g.setColor(new Color(240, 240, 235, 170)); // a rachadura no vidro
		g.setStroke(new BasicStroke(1));
		g.drawPolyline(new int[] { 22, 40, 47, 63, 70, 92 }, new int[] { 40, 47, 58, 60, 74, 83 }, 6);
		g.drawLine(47, 58, 44, 72);
		g.dispose();
		return img;
	}

	private static void ponteiro(Graphics2D g, double graus, float comprimento, float largura) {
		double a = Math.toRadians(graus);
		g.setStroke(new BasicStroke(largura, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.drawLine(64, 64, 64 + (int) (Math.sin(a) * comprimento), 64 - (int) (Math.cos(a) * comprimento));
	}

	/**
	 * Um quadro de chuvisco de TV velha (64 x 48): pontos de cinza ao acaso, linhas de varredura
	 * mais escuras e uma faixa clara rolando. Com o logo, "UNIVALI" aparece fraco no meio do chuvisco.
	 */
	public static BufferedImage chuvisco(int semente, boolean logo) {
		int w = 64, h = 48;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(semente * 7919L);
		int faixa = r.nextInt(h);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int v = 40 + r.nextInt(170);
				if (y % 2 == 1) v = v * 7 / 10;                  // linhas de varredura
				if (Math.abs(y - faixa) < 3) v = Math.min(255, v + 50); // a faixa clara
				int azul = Math.min(255, v + 18);
				img.setRGB(x, y, new Color(v, v, azul).getRGB());
			}
		}
		if (logo) {
			Graphics2D g = img.createGraphics();
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
			g.setColor(new Color(235, 240, 255, 170));
			g.drawString("UNIVALI", 6, 29);
			g.dispose();
		}
		return img;
	}

	/** A frase da parede, em linhas (caixa alta, como pintada às pressas com a mão). */
	static final String[] FRASE_SANGUE = { "EU SEI QUE VOCÊ", "REPROVOU EM", "LINGUAGENS FORMAIS", "E AUTÔMATOS" };

	/**
	 * As letras da frase como traços de pincel (como quem escreve com o dedo): cada letra é uma
	 * lista de traços, cada traço uma linha de pontos numa caixa de 0 a 1 (y para baixo). Curvas
	 * viram arcos de elipse. Só as letras que a frase usa; ^ é o acento circunflexo.
	 */
	private static final java.util.Map<Character, float[][]> TRACOS = new java.util.HashMap<>();
	private static final java.util.Map<Character, Float> LARGURAS = new java.util.HashMap<>();

	private static void letra(char c, float largura, float[]... tracos) {
		TRACOS.put(c, tracos);
		LARGURAS.put(c, largura);
	}

	/** Um arco de elipse (centro, raios, de um ângulo a outro, em graus; y para baixo) como linha de pontos. */
	private static float[] arco(float cx, float cy, float rx, float ry, float de, float ate) {
		int n = 14;
		float[] p = new float[(n + 1) * 2];
		for (int i = 0; i <= n; i++) {
			double a = Math.toRadians(de + (ate - de) * i / n);
			p[i * 2] = cx + rx * (float) Math.cos(a);
			p[i * 2 + 1] = cy - ry * (float) Math.sin(a);
		}
		return p;
	}

	static {
		letra('A', 0.72f, new float[] { 0, 1, 0.36f, 0 }, new float[] { 0.36f, 0, 0.72f, 1 }, new float[] { 0.12f, 0.62f, 0.62f, 0.6f });
		letra('C', 0.66f, arco(0.38f, 0.5f, 0.36f, 0.5f, 45, 315));
		letra('E', 0.6f, new float[] { 0.6f, 0, 0, 0, 0, 1, 0.62f, 1 }, new float[] { 0, 0.5f, 0.5f, 0.48f });
		letra('F', 0.58f, new float[] { 0.6f, 0, 0, 0, 0, 1 }, new float[] { 0, 0.48f, 0.46f, 0.47f });
		letra('G', 0.72f, arco(0.38f, 0.5f, 0.36f, 0.5f, 40, 340), new float[] { 0.4f, 0.58f, 0.74f, 0.58f, 0.73f, 1.0f });
		letra('I', 0.22f, new float[] { 0.1f, 0, 0.11f, 1 });
		letra('L', 0.56f, new float[] { 0, 0, 0, 1, 0.58f, 1 });
		letra('M', 0.86f, new float[] { 0, 1, 0.06f, 0, 0.43f, 0.7f, 0.8f, 0, 0.86f, 1 });
		letra('N', 0.7f, new float[] { 0, 1, 0.02f, 0, 0.66f, 1, 0.68f, 0 });
		letra('O', 0.74f, arco(0.37f, 0.5f, 0.37f, 0.5f, 100, 470));
		letra('P', 0.62f, new float[] { 0, 1, 0, 0, 0.4f, 0 }, arco(0.4f, 0.25f, 0.22f, 0.25f, 90, -90), new float[] { 0.4f, 0.5f, 0, 0.5f });
		letra('Q', 0.76f, arco(0.37f, 0.5f, 0.37f, 0.5f, 100, 470), new float[] { 0.45f, 0.68f, 0.82f, 1.08f });
		letra('R', 0.66f, new float[] { 0, 1, 0, 0, 0.4f, 0 }, arco(0.4f, 0.25f, 0.22f, 0.25f, 90, -90),
				new float[] { 0.4f, 0.5f, 0, 0.5f }, new float[] { 0.22f, 0.5f, 0.66f, 1 });
		letra('S', 0.62f, new float[] { 0.6f, 0.12f, 0.45f, 0, 0.17f, 0, 0.02f, 0.14f, 0.06f, 0.34f, 0.3f, 0.47f, 0.55f, 0.6f,
				0.64f, 0.8f, 0.55f, 0.96f, 0.26f, 1, 0, 0.88f });
		letra('T', 0.7f, new float[] { 0, 0, 0.72f, 0.02f }, new float[] { 0.36f, 0, 0.35f, 1 });
		letra('U', 0.66f, new float[] { 0, 0, 0, 0.68f, 0.1f, 0.92f, 0.33f, 1, 0.55f, 0.92f, 0.65f, 0.68f, 0.66f, 0 });
		letra('V', 0.7f, new float[] { 0, 0, 0.35f, 1, 0.7f, 0 });
		letra('^', 0, new float[] { -0.18f, -0.1f, 0, -0.26f, 0.18f, -0.1f });
	}

	/**
	 * texturas/escrita_sangue.png, ou a frase pintada com sangue, gerada por código: cada letra
	 * feita de traços de pincel (ver TRACOS), um pouco torta e de tamanho diferente; cada traço
	 * pintado por várias cerdas finas que falham no fim (pincel seco) e passam um pouco da ponta;
	 * respingos nas pontas e escorridos descendo das letras, com uma gota no fim.
	 */
	public static BufferedImage escritaSangue() {
		if (Caminhos.existe("texturas/escrita_sangue.png")) {
			BufferedImage img = TextureLoader.loadImage(Caminhos.caminho("texturas/escrita_sangue.png"));
			if (img != null) return img;
		}
		int w = 1536, h = 940;
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Random r = new Random(66);
		float espaco = 0.2f, y = 70;
		for (int l = 0; l < FRASE_SANGUE.length; l++) {
			String linha = FRASE_SANGUE[l];
			float largura = 0; // em alturas de letra
			for (char c : linha.toCharArray()) largura += (c == ' ' ? 0.35f : LARGURAS.get(base(c))) + espaco;
			float altura = Math.min(225, (w - 120) / largura); // cada linha do tamanho que couber
			y += altura * (l == 0 ? 0.25f : 0.42f);
			float x = (w - largura * altura) / 2 + (r.nextFloat() - 0.5f) * 40;
			for (char c : linha.toCharArray()) {
				if (c != ' ') {
					float escala = altura * (0.88f + r.nextFloat() * 0.22f);
					float dy = (r.nextFloat() - 0.5f) * altura * 0.12f, giro = (r.nextFloat() - 0.5f) * 0.18f;
					float lx = LARGURAS.get(base(c));
					java.util.List<float[]> tracos = new java.util.ArrayList<>(java.util.List.of(TRACOS.get(base(c))));
					if (c == 'Ê' || c == 'Ô') { // o acento, no meio da letra
						float[] ac = TRACOS.get('^')[0].clone();
						for (int k = 0; k < ac.length; k += 2) ac[k] += lx / 2;
						tracos.add(ac);
					}
					for (float[] t : tracos) {
						float[] px = new float[t.length];
						for (int k = 0; k < t.length; k += 2) { // para pixels, girando a letra em volta do centro
							float ux = (t[k] - lx / 2) * escala, uy = (t[k + 1] - 0.5f) * escala;
							px[k] = x + lx * altura / 2 + ux * (float) Math.cos(giro) - uy * (float) Math.sin(giro)
									+ (r.nextFloat() - 0.5f) * altura * 0.03f;
							px[k + 1] = y + altura / 2 + dy + ux * (float) Math.sin(giro) + uy * (float) Math.cos(giro)
									+ (r.nextFloat() - 0.5f) * altura * 0.03f;
						}
						pincelada(g, px, altura * (0.1f + r.nextFloat() * 0.035f), r, l == FRASE_SANGUE.length - 1);
					}
					x += (lx + espaco) * altura;
				} else {
					x += (0.35f + espaco) * altura;
				}
			}
			y += altura * 1.0f;
		}
		// respingos soltos pela parede
		for (int i = 0; i < 220; i++) {
			float rx = r.nextFloat() * w, ry = r.nextFloat() * h * 0.92f, raio = 1 + (float) Math.pow(r.nextFloat(), 4) * 9;
			g.setColor(corSangue(r));
			g.fill(new java.awt.geom.Ellipse2D.Float(rx - raio, ry - raio, raio * 2, raio * 2));
		}
		g.dispose();
		// borda dura (o shader descarta o que tiver alfa abaixo de 0,5): sem franja clara em volta
		for (int yy = 0; yy < h; yy++) {
			for (int xx = 0; xx < w; xx++) {
				int argb = img.getRGB(xx, yy);
				img.setRGB(xx, yy, (argb >>> 24) >= 110 ? argb | 0xFF000000 : argb & 0x00FFFFFF);
			}
		}
		return img;
	}

	/** A letra sem acento (o acento é desenhado à parte). */
	private static char base(char c) {
		return c == 'Ê' ? 'E' : c == 'Ô' ? 'O' : c;
	}

	private static Color corSangue(Random r) {
		return r.nextFloat() < 0.7f ? new Color(150 + r.nextInt(35), 10 + r.nextInt(10), 12 + r.nextInt(8))
				: new Color(105 + r.nextInt(25), 6, 8);
	}

	/**
	 * Uma pincelada ao longo da linha de pontos (em pixels): 7 cerdas lado a lado, cada uma com a
	 * sua cor e falhando mais perto do fim (o pincel secando); o traço passa um pouco da ponta e
	 * afina; respingos no começo e no fim; às vezes um escorrido desce de algum ponto do traço.
	 */
	private static void pincelada(Graphics2D g, float[] p, float largura, Random r, boolean escorreMais) {
		// os pontos a cada 3 px, com o traço esticado um pouco nas pontas
		java.util.List<float[]> pts = new java.util.ArrayList<>();
		for (int k = 0; k + 3 < p.length; k += 2) {
			float x0 = p[k], y0 = p[k + 1], x1 = p[k + 2], y1 = p[k + 3];
			if (k == 0) { // começa um pouco antes
				x0 += (x0 - x1) * 0.08f * r.nextFloat();
				y0 += (y0 - y1) * 0.08f * r.nextFloat();
			}
			if (k + 4 >= p.length) { // e termina um pouco depois (a "puxada" do pincel)
				x1 += (x1 - x0) * 0.15f * r.nextFloat();
				y1 += (y1 - y0) * 0.15f * r.nextFloat();
			}
			int passos = Math.max(1, (int) (Math.hypot(x1 - x0, y1 - y0) / 3));
			for (int i = 0; i < passos; i++) pts.add(new float[] { x0 + (x1 - x0) * i / passos, y0 + (y1 - y0) * i / passos });
			if (k + 4 >= p.length) pts.add(new float[] { x1, y1 });
		}
		int n = pts.size();
		if (n < 2) return;
		int cerdas = 7;
		float fase = r.nextFloat() * 10;
		for (int b = 0; b < cerdas; b++) {
			float desloc = (b / (float) (cerdas - 1) - 0.5f) * largura;
			Color cor = corSangue(r);
			float seca = 0.4f + r.nextFloat() * 0.6f; // a partir de onde esta cerda começa a falhar
			for (int i = 1; i < n; i++) {
				float t = (float) i / n;
				if (t > seca && r.nextFloat() < (t - seca) * 2.5f) continue; // pincel seco: falha
				if (r.nextFloat() < 0.03f) continue;
				float[] a = pts.get(i - 1), c = pts.get(i);
				float dx = c[0] - a[0], dy = c[1] - a[1], comp = (float) Math.max(1e-3, Math.hypot(dx, dy));
				float nx = -dy / comp, ny = dx / comp;
				// a pressão muda ao longo do traço e afina nas pontas
				float pressao = (0.75f + 0.25f * (float) Math.sin(fase + t * 7)) * (float) Math.min(1, Math.min(t, 1 - t) * 6 + 0.35);
				float o = desloc * pressao;
				g.setColor(cor);
				g.setStroke(new BasicStroke(Math.max(1, largura / cerdas * 1.7f * pressao), BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND));
				g.draw(new java.awt.geom.Line2D.Float(a[0] + nx * o, a[1] + ny * o, c[0] + nx * o, c[1] + ny * o));
			}
		}
		// respingos nas pontas
		for (float[] ponta : new float[][] { pts.get(0), pts.get(n - 1) }) {
			int quantos = 3 + r.nextInt(8);
			for (int k = 0; k < quantos; k++) {
				double ang = r.nextDouble() * 2 * Math.PI;
				float dist = largura * (0.6f + r.nextFloat() * 2.5f), raio = 0.8f + r.nextFloat() * largura * 0.18f;
				g.setColor(corSangue(r));
				g.fill(new java.awt.geom.Ellipse2D.Float(ponta[0] + (float) Math.cos(ang) * dist - raio,
						ponta[1] + (float) Math.sin(ang) * dist - raio, raio * 2, raio * 2));
			}
		}
		// escorridos: descem de pontos do traço (mais na última linha), afinando, com a gota na ponta
		int escorridos = r.nextFloat() < (escorreMais ? 0.8f : 0.5f) ? 1 + r.nextInt(2) : 0;
		for (int k = 0; k < escorridos; k++) {
			float[] de = pts.get(r.nextInt(n));
			float comprimento = largura * (2 + r.nextFloat() * (escorreMais ? 12 : 7)), largo = largura * (0.25f + r.nextFloat() * 0.2f);
			g.setColor(corSangue(r));
			java.awt.geom.Path2D gota = new java.awt.geom.Path2D.Float();
			gota.moveTo(de[0] - largo / 2, de[1]);
			gota.lineTo(de[0] + largo / 2, de[1]);
			gota.lineTo(de[0] + largo * 0.2f, de[1] + comprimento);
			gota.lineTo(de[0] - largo * 0.2f, de[1] + comprimento);
			gota.closePath();
			g.fill(gota);
			g.fill(new java.awt.geom.Ellipse2D.Float(de[0] - largo * 0.4f, de[1] + comprimento - largo * 0.3f, largo * 0.8f,
					largo * 1.2f));
		}
	}

	/** O vulto nas janelas (sprites/professor.png, ou uma figura escura de cabeça e ombros). */
	public static BufferedImage vulto() {
		if (Caminhos.existe("sprites/professor.png")) {
			BufferedImage img = TextureLoader.loadImage(Caminhos.caminho("sprites/professor.png"));
			if (img != null) return img;
		}
		BufferedImage img = new BufferedImage(48, 72, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(12, 10, 10));
		g.fillOval(15, 6, 18, 22);                          // cabeça
		g.fillRoundRect(4, 30, 40, 46, 18, 18);             // ombros e tronco
		g.fillRect(20, 24, 8, 8);                           // pescoço
		g.dispose();
		return img;
	}

	private static BufferedImage liso(Color c) {
		BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, c.getRGB());
		return img;
	}

	/** Parede de escola: verde-claro em cima, faixa escura embaixo (a barra lavável). */
	public static BufferedImage pintura() {
		BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(51);
		for (int y = 0; y < 64; y++) {
			for (int x = 0; x < 64; x++) {
				boolean barra = y > 46; // 1 m de barra embaixo (a textura cobre 4 m de altura)
				int v = r.nextInt(10);
				Color c = barra ? new Color(70 + v, 82 + v, 70 + v) : new Color(168 + v, 182 + v, 160 + v);
				img.setRGB(x, y, c.getRGB());
			}
		}
		Graphics2D g = pincel(img);
		g.setColor(new Color(50, 55, 45, 120));
		g.drawLine(0, 46, 63, 46);
		g.dispose();
		return img;
	}

	/** Piso de granilite: cinza com pontinhos, em placas de 1 m com junta escura. */
	public static BufferedImage piso() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(53);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int v = 110 + r.nextInt(14);
				if (r.nextFloat() < 0.08f) v += 50;
				if (x == 0 || y == 0) v = 70;
				img.setRGB(x, y, new Color(v, v - 4, v - 8).getRGB());
			}
		}
		return img;
	}

	/** Forro de placas, com as juntas. */
	public static BufferedImage forro() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(57);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int v = 150 + r.nextInt(12);
				if (x == 0 || y == 0) v = 95;
				img.setRGB(x, y, new Color(v, v, v - 6).getRGB());
			}
		}
		return img;
	}

	public static BufferedImage madeira() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(59);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				double veio = Math.sin(y * 0.7 + Math.sin(x * 0.2) * 2);
				int v = (int) (85 + 20 * veio + r.nextInt(8));
				img.setRGB(x, y, new Color(v, (int) (v * 0.65), (int) (v * 0.4)).getRGB());
			}
		}
		return img;
	}

	/** A folha da carta vista de cima: papel amarelado e manchado com linhas de garrancho. */
	public static BufferedImage folhaDaCarta() {
		BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(214, 200, 168));
		g.fillRect(0, 0, 64, 48);
		java.util.Random r = new java.util.Random(23);
		g.setColor(new Color(60, 52, 48));
		for (int linha = 0; linha < 9; linha++) {
			int y = 7 + linha * 4, x = 6;
			while (x < 56 && !(linha == 8 && x > 30)) {
				int palavra = 3 + r.nextInt(7);
				g.drawLine(x, y + r.nextInt(2), Math.min(57, x + palavra), y + r.nextInt(2));
				x += palavra + 2;
			}
		}
		g.setColor(new Color(120, 30, 20, 120)); // uma mancha
		g.fillOval(44, 30, 9, 7);
		g.dispose();
		return img;
	}

	/** O crachá: cartão amarelo com uma faixa azul e "CHAVE". */
	public static BufferedImage cracha() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(240, 200, 60));
		g.fillRect(0, 0, 32, 32);
		g.setColor(new Color(0, 63, 135));
		g.fillRect(0, 0, 32, 10);
		g.dispose();
		return img;
	}

	/** A placa de SAÍDA: letras brancas em fundo verde. */
	public static BufferedImage placaSaida() {
		BufferedImage img = new BufferedImage(64, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(0, 140, 60));
		g.fillRect(0, 0, 64, 16);
		g.setColor(Color.WHITE);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
		int w = g.getFontMetrics().stringWidth("SAÍDA");
		g.drawString("SAÍDA", (64 - w) / 2, 13);
		g.dispose();
		return img;
	}

	/** A roupa do professor, em quatro partes: polo preta (com textura de malha), pele, calça jeans escura e a gola vinho. */
	public static BufferedImage roupa() {
		BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(61);
		for (int y = 0; y < 32; y++) {
			for (int x = 0; x < 32; x++) {
				int v = r.nextInt(8);
				Color c;
				if (y < 16 && x < 16) c = new Color(22 + v, 22 + v, 25 + v + ((x + y) % 3 == 0 ? 6 : 0)); // a malha da polo
				else if (y < 16) c = new Color(178 + v, 140 + v, 118 + v);                               // pele (dos braços da foto)
				else if (x < 16) c = new Color(38 + v, 42 + v, 58 + v);                                   // jeans escuro
				else c = new Color(68 + v, 20, 26);                                                       // a gola vinho
				img.setRGB(x, y, c.getRGB());
			}
		}
		return img;
	}

	/**
	 * A textura da cabeça (256 x 192): à esquerda o rosto (texturas/professor_rosto.png, recortado
	 * da foto do professor; sem o arquivo, um rosto desenhado), depois o lado (cabelo em cima, pele e
	 * barba por fazer embaixo) e a nuca (cabelo descendo até o pescoço). As cores do lado e da nuca
	 * saem do próprio rosto, para emendar.
	 */
	public static BufferedImage cabeca() {
		BufferedImage img = new BufferedImage(256, 192, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		Color pele = new Color(170, 132, 112), cabelo = new Color(30, 26, 24), barba = new Color(110, 100, 96);
		BufferedImage rosto = null;
		if (Caminhos.existe("texturas/professor_rosto.png")) rosto = TextureLoader.loadImage(Caminhos.caminho("texturas/professor_rosto.png"));
		if (rosto != null) {
			g.drawImage(rosto, 0, 0, 128, 192, null);
			pele = media(rosto, 0.25f, 0.55f, 0.1f); // a bochecha esquerda
			barba = media(rosto, 0.5f, 0.92f, 0.06f);
		} else { // sem a foto: um rosto desenhado
			g.setColor(pele);
			g.fillRect(0, 0, 128, 192);
			g.setColor(cabelo);
			g.fillRect(0, 0, 128, 45);
			g.setColor(new Color(20, 20, 22));
			g.setStroke(new BasicStroke(5));
			g.drawRect(18, 70, 38, 24);
			g.drawRect(72, 70, 38, 24);
			g.fillRect(30, 78, 12, 10);
			g.fillRect(84, 78, 12, 10);
			g.setColor(barba);
			g.fillRect(44, 150, 40, 42);
		}
		Random r = new Random(9);
		for (int x = 128; x < 256; x++) {
			boolean nuca = x >= 192;
			int fimCabelo = nuca ? 150 : 70; // o cabelo desce até a nuca; do lado, até a altura das orelhas
			for (int y = 0; y < 192; y++) {
				Color c = y < fimCabelo + r.nextInt(8) ? cabelo : !nuca && y > 120 ? barba : pele;
				int v = r.nextInt(14) - 7;
				img.setRGB(x, y, new Color(lim(c.getRed() + v), lim(c.getGreen() + v), lim(c.getBlue() + v)).getRGB());
			}
		}
		g.dispose();
		return img;
	}

	/** A cor média de um quadradinho da imagem (centro e meio lado em frações da imagem). */
	private static Color media(BufferedImage img, float cx, float cy, float meio) {
		int x0 = (int) ((cx - meio) * img.getWidth()), x1 = (int) ((cx + meio) * img.getWidth());
		int y0 = (int) ((cy - meio) * img.getHeight()), y1 = (int) ((cy + meio) * img.getHeight());
		long rr = 0, gg = 0, bb = 0, n = 0;
		for (int y = Math.max(0, y0); y < Math.min(img.getHeight(), y1); y++) {
			for (int x = Math.max(0, x0); x < Math.min(img.getWidth(), x1); x++) {
				Color c = new Color(img.getRGB(x, y));
				rr += c.getRed();
				gg += c.getGreen();
				bb += c.getBlue();
				n++;
			}
		}
		return n == 0 ? Color.GRAY : new Color((int) (rr / n), (int) (gg / n), (int) (bb / n));
	}

	private static int lim(int v) {
		return Math.max(0, Math.min(255, v));
	}

	/** Porta de madeira escura com o visor de vidro e a maçaneta. */
	public static BufferedImage portaMadeira() {
		BufferedImage img = madeira();
		Graphics2D g = pincel(img);
		g.setColor(new Color(15, 20, 28));
		g.fillRect(11, 5, 10, 8);
		g.setColor(new Color(180, 170, 120));
		g.fillRect(24, 16, 3, 2);
		g.dispose();
		return img;
	}

	/** Lousa verde com giz: a matéria da prova, umas contas e um aviso. */
	public static BufferedImage lousa() {
		BufferedImage img = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(28, 62, 44));
		g.fillRect(0, 0, 128, 64);
		g.setColor(new Color(120, 90, 60));
		g.fillRect(0, 60, 128, 4);
		g.setColor(new Color(220, 225, 215, 200));
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
		g.drawString("PROVA: M1  M2  M3", 6, 12);
		g.drawString("v' = M v    (P V M)", 6, 26);
		g.drawString("glDrawArrays(...)", 6, 38);
		g.drawString("NÃO SAIAM DO PRÉDIO", 52, 54);
		g.dispose();
		return img;
	}

	/** Tela de computador ligada: azul, com linhas de texto claras. */
	public static BufferedImage tela() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(20, 60, 140));
		g.fillRect(0, 0, 16, 16);
		g.setColor(new Color(170, 200, 240));
		for (int y = 3; y < 14; y += 3) g.drawLine(2, y, 6 + y % 7, y);
		g.dispose();
		return img;
	}

	/** Armário de metal cinza com as fendas de ventilação e o cadeado. */
	public static BufferedImage armario() {
		BufferedImage img = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(95, 105, 110));
		g.fillRect(0, 0, 16, 32);
		g.setColor(new Color(55, 60, 65));
		g.drawRect(0, 0, 15, 31);
		for (int y = 3; y < 9; y += 2) g.drawLine(4, y, 11, y);
		g.fillRect(11, 15, 2, 3);
		g.dispose();
		return img;
	}

	/** Mural de cortiça com cartazes: o Computer on the Beach, a prova e avisos. */
	public static BufferedImage mural() {
		BufferedImage img = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(150, 110, 70));
		g.fillRect(0, 0, 128, 64);
		g.setColor(new Color(90, 60, 35));
		g.drawRect(0, 0, 127, 63);
		// o cartaz do evento
		g.setColor(new Color(16, 36, 58));
		g.fillRect(6, 6, 52, 38);
		BufferedImage logo = Caminhos.existe(DesenhoPlacas.LOGO) ? TextureLoader.loadImage(Caminhos.caminho(DesenhoPlacas.LOGO)) : null;
		if (logo != null) g.drawImage(logo, 8, 12, 48, 18, null);
		// a folha da prova e avisos
		g.setColor(new Color(230, 225, 210));
		g.fillRect(64, 6, 26, 34);
		g.fillRect(96, 10, 26, 22);
		g.fillRect(70, 44, 40, 14);
		g.setColor(new Color(40, 40, 60));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 7));
		g.drawString("PROVA", 66, 14);
		g.drawString("CG", 70, 24);
		g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 6));
		g.drawString("Não corra", 98, 18);
		g.drawString("no corredor", 97, 25);
		g.drawString("Semana Acadêmica", 72, 53);
		g.setColor(new Color(200, 30, 30));
		for (int[] t : new int[][] { { 30, 7 }, { 77, 7 }, { 109, 11 }, { 90, 45 } }) g.fillOval(t[0], t[1], 3, 3); // tachinhas
		g.dispose();
		return img;
	}

	/** A placa da porta: o nome da sala em branco sobre azul. */
	public static BufferedImage placaDaPorta(String nome) {
		BufferedImage img = new BufferedImage(128, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		g.setColor(new Color(0, 50, 110));
		g.fillRect(0, 0, 128, 32);
		g.setColor(Color.WHITE);
		int tamanho = nome.length() > 18 ? 10 : 14;
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, tamanho));
		int w = g.getFontMetrics().stringWidth(nome);
		g.drawString(nome, (128 - w) / 2, 16 + tamanho / 2 - 1);
		g.dispose();
		return img;
	}
}
