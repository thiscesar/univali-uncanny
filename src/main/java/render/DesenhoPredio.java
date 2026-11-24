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
 * a porta de entrada e a da sala do professor (enquanto trancadas), o crachá numa mesinha e a
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
	private final MalhaCaixas pintura, piso, forro, lampadasTeto, lampadasPiscando, saida, portaEntrada, portaProfessor;
	private final MalhaCaixas portasTrancadas, madeira, metalInterno, lousas, monitores, telas, louca, armarios, murais;
	private final MalhaCaixas logoSaguao;
	private final List<MalhaCaixas> placas = new ArrayList<>();
	private final List<String> nomesPlacas = new ArrayList<>();
	private final MalhaCaixas cracha, mesa, papeis, luminaria, cadeira;
	private final MalhaCaixas corpoProfessor, cabecaProfessor;
	private final PontosBrilhantes halos;
	private final FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
	private float tempo = 0;
	private int janelaMontada = -1;
	/** Para onde a cabeça do professor está virada (radianos, no modelo dele) e para onde ela quer ir. */
	private float cabeca = 0, cabecaAlvo = 0;

	// o que muda durante o jogo
	public boolean portaEntradaAberta = false, portaProfessorAberta = false, crachaPego = false;

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
		lampadasTeto = new MalhaCaixas();
		lampadasPiscando = new MalhaCaixas();
		for (AndarUnivali.Lampada l : p.andar.lampadas()) {
			if (l.estado() == AndarUnivali.Lampada.Estado.QUEIMADA) continue;
			MalhaCaixas m = l.estado() == AndarUnivali.Lampada.Estado.PISCANDO ? lampadasPiscando : lampadasTeto;
			m.caixa(l.x() - 0.6f, l.x() + 0.6f, pe - 0.04f, pe, l.z() - 0.3f, l.z() + 0.3f, TUDO, TUDO, TUDO);
		}
		lampadasTeto.enviar();
		lampadasPiscando.enviar();
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
		logoSaguao = new MalhaCaixas().caixa(-AndarUnivali.MEIO_SAGUAO + 0.12f, -AndarUnivali.MEIO_SAGUAO + 0.16f, 1.6f, 2.8f,
				5.5f, 10.5f, TUDO, TUDO, TUDO).enviar();
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
		// o professor, sentado: a textura tem roupa em cima (camisa) e embaixo (calça)
		float[] camisa = { 0, 0, 1, 0.5f }, calca = { 0, 0.5f, 1, 1 };
		corpoProfessor = new MalhaCaixas()
				.caixa(-0.2f, 0.2f, 0.45f, 0.6f, -0.15f, 0.45f, calca, calca, calca)    // coxas
				.caixa(-0.2f, 0.2f, 0, 0.45f, 0.35f, 0.5f, calca, calca, calca)         // canelas
				.caixa(-0.22f, 0.22f, 0.6f, 1.25f, -0.13f, 0.13f, camisa, camisa, camisa) // tronco
				.caixa(-0.32f, -0.22f, 0.9f, 1.22f, -0.06f, 0.08f, camisa, camisa, camisa) // braços
				.caixa(0.22f, 0.32f, 0.9f, 1.22f, -0.06f, 0.08f, camisa, camisa, camisa)
				.caixa(-0.3f, -0.18f, 0.82f, 0.92f, 0.0f, 0.62f, camisa, camisa, camisa) // antebraços na mesa
				.caixa(0.18f, 0.3f, 0.82f, 0.92f, 0.0f, 0.62f, camisa, camisa, camisa)
				.enviar();
		// a cabeça: o rosto na metade esquerda da textura, pele e cabelo na direita
		float[] rosto = { 0, 0, 0.5f, 1 }, pele = { 0.5f, 0, 1, 1 };
		cabecaProfessor = new MalhaCaixas()
				.caixa(-0.05f, 0.05f, 1.25f, 1.32f, -0.05f, 0.05f, pele, pele, pele)  // pescoço
				.caixa(-0.11f, 0.11f, 1.32f, 1.58f, -0.12f, 0.12f, rosto, pele, pele)
				.enviar();
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

	/** As lâmpadas que piscam estão acesas agora? (A mesma conta do desenho, para a luz bater com ele.) */
	public boolean lampadaPiscandoAcesa() {
		return Math.sin(tempo * 17.0) * Math.sin(tempo * 5.3 + 2.0) > 0.3;
	}

	/** Onde fica a lâmpada da luminária, no mundo (a TelaJogo põe uma luz pontual ali). */
	public float[] luminariaNoMundo() {
		float[] prof = predio.professorNoModelo();
		float c = (float) Math.cos(prof[2]), s = (float) Math.sin(prof[2]);
		float lx = 0.57f, lz = 0.95f; // a lâmpada, no espaço do professor
		return predio.noMundo(prof[0] + c * lx + s * lz, 1.1f, prof[1] - s * lx + c * lz);
	}

	public void desenhar(FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente, int alturaImagem) {
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
		desenhar(p, lampadasTeto, "predio: lampada do teto", () -> liso(new Color(210, 225, 230)), 0.55f, false);
		float falha = (float) (Math.sin(tempo * 17.0) * Math.sin(tempo * 5.3 + 2.0));
		desenhar(p, lampadasPiscando, "predio: lampada do teto", () -> liso(new Color(210, 225, 230)),
				falha > 0.3f ? 0.9f : 0.02f, false);
		desenhar(p, saida, "predio: saida", DesenhoPredio::placaSaida, 1.0f, false);
		if (!portaEntradaAberta) desenhar(p, portaEntrada, "predio: porta", DesenhoPredio::porta, 0.05f, false);
		if (!portaProfessorAberta) desenhar(p, portaProfessor, "predio: porta de madeira", DesenhoPredio::portaMadeira, 0f, false);
		desenhar(p, portasTrancadas, "predio: porta de madeira", DesenhoPredio::portaMadeira, 0f, false);
		desenhar(p, madeira, "predio: madeira", DesenhoPredio::madeira, 0f, false);
		desenhar(p, metalInterno, "predio: metal", () -> liso(new Color(40, 42, 45)), 0f, false);
		desenhar(p, lousas, "predio: lousa", DesenhoPredio::lousa, 0.03f, false);
		desenhar(p, monitores, "predio: monitor", () -> liso(new Color(25, 25, 28)), 0f, false);
		desenhar(p, telas, "predio: tela", DesenhoPredio::tela, 0.8f, false);
		desenhar(p, louca, "predio: louca", () -> liso(new Color(215, 215, 210)), 0f, false);
		desenhar(p, armarios, "predio: armario", DesenhoPredio::armario, 0f, false);
		desenhar(p, murais, "predio: mural", DesenhoPredio::mural, 0.04f, false);
		desenhar(p, logoSaguao, "predio: letreiro", DesenhoPredio::letreiro, 0.5f, false);
		for (int i = 0; i < placas.size(); i++) {
			String nome = nomesPlacas.get(i);
			desenhar(p, placas.get(i), "placa da porta: " + nome, () -> placaDaPorta(nome), 0.2f, false);
		}
		if (!crachaPego) desenhar(p, cracha, "predio: cracha", DesenhoPredio::cracha, 0.6f, false);

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
		if (!crachaPego) {
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
				vulto, pintura, piso, forro, lampadasTeto, lampadasPiscando, saida, portaEntrada, portaProfessor, portasTrancadas,
				madeira, metalInterno, lousas, monitores, telas, louca, armarios, murais, logoSaguao, cracha, mesa, papeis,
				luminaria, cadeira, corpoProfessor, cabecaProfessor }) {
			m.liberar();
		}
		for (MalhaCaixas m : placas) m.liberar();
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

	/** A roupa do professor: camisa clara em cima, calça escura embaixo. */
	public static BufferedImage roupa() {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(61);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int v = r.nextInt(10);
				img.setRGB(x, y, y < 8 ? new Color(170 + v, 185 + v, 205 + v).getRGB() : new Color(45 + v, 45 + v, 52 + v).getRGB());
			}
		}
		return img;
	}

	/**
	 * A cabeça do professor: à esquerda o rosto (cabelo grisalho em cima, óculos, boca séria),
	 * à direita a pele com o cabelo em cima, para os lados e a nuca.
	 */
	public static BufferedImage cabeca() {
		BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = pincel(img);
		Color pele = new Color(205, 165, 135), cabelo = new Color(150, 150, 150);
		g.setColor(pele);
		g.fillRect(0, 0, 64, 32);
		g.setColor(cabelo);
		g.fillRect(0, 0, 32, 7);
		g.fillRect(32, 0, 32, 10);
		g.setColor(new Color(20, 20, 22)); // óculos
		g.setStroke(new BasicStroke(1.5f));
		g.drawRect(5, 11, 9, 6);
		g.drawRect(18, 11, 9, 6);
		g.drawLine(14, 13, 18, 13);
		g.fillRect(8, 13, 3, 3); // olhos
		g.fillRect(21, 13, 3, 3);
		g.setColor(new Color(120, 60, 50));
		g.drawLine(11, 25, 21, 25); // boca séria
		g.dispose();
		return img;
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
