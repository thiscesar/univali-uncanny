package render;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.List;

import org.lwjgl.BufferUtils;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import dados.Recursos;
import entidades.DragonKnight;
import entidades.Jubilado;
import md2.Animador;
import md2.MalhaMd2;
import md2.PersonagemMd2;
import md2.PontoMd2;
import obj.Object3D;
import shaders.ShaderMd2;
import shaders.ShaderObjeto;

/**
 * Desenha um monstro MD2 (o Dragon Knight ou o Jubilado): o modelo animado na posição e na guinada da lógica, com um brilho
 * próprio fraco e o tom vermelho do dano, e os olhos brilhando para ele ser achado no escuro
 * mesmo com a lanterna desligada.
 *
 * Os olhos: o olho amarelo do dragão está pintado na skin, no pixel (54, 122) da skin.png de
 * 256 x 256 (o do Jubilado, verde, no (217, 147)): cada monstro tem a sua Aparencia. O PontoMd2 acha os triângulos do modelo que cobrem esse pixel (um de cada lado da
 * cabeça) e, a cada quadro, a posição deles na animação. Ali se desenha um ponto que brilha
 * (PontosBrilhantes), somado à cena.
 *
 * Sem o MD2, desenha o substituto: um retângulo vermelho em pé, sempre de frente para a
 * câmera (billboard), com dois olhos no alto.
 */
public class DesenhoMonstro {

	/**
	 * O que muda de um monstro para outro: onde o olho está na skin (de 0 a 1; o v conta do topo,
	 * como no MD2), o tamanho e a cor do brilho do olho, o brilho próprio do corpo (pouco, só para
	 * a silhueta não sumir no preto) e o tamanho do retângulo substituto, em metros.
	 */
	public record Aparencia(float olhoU, float olhoV, float tamanhoOlho, float[] corOlho, float emissividade,
			float larguraSubstituto, float alturaSubstituto) {}

	public static final Aparencia DRAGON_KNIGHT = new Aparencia(54.5f / 256f, 122f / 256f, 0.16f,
			new float[] { 1.6f, 0.75f, 0.15f }, 0.08f, 1.4f, 2.2f);
	public static final Aparencia JUBILADO = new Aparencia(217.5f / 256f, 147.5f / 256f, 0.11f,
			new float[] { 0.35f, 1.7f, 0.3f }, 0.06f, 0.8f, 2.0f);

	private final Aparencia aparencia;
	private final PersonagemMd2 personagem;
	private final List<PontoMd2> olhos;
	private final FloatBuffer bufferMatriz = BufferUtils.createFloatBuffer(16);
	private final PontosBrilhantes pontosOlhos;
	private int vboSubstituto;

	/** O Dragon Knight já carregado, ou null para usar o substituto. Só na thread principal. */
	public DesenhoMonstro(PersonagemMd2 personagem) {
		this(personagem, DRAGON_KNIGHT);
	}

	/** Um monstro já carregado (ou null para o substituto), com a sua aparência. Só na thread principal. */
	public DesenhoMonstro(PersonagemMd2 personagem, Aparencia aparencia) {
		this.personagem = personagem;
		this.aparencia = aparencia;
		olhos = personagem != null ? PontoMd2.naSkin(personagem.malhaCorpo(), aparencia.olhoU(), aparencia.olhoV())
				: List.of();

		pontosOlhos = new PontosBrilhantes(4);

		if (personagem == null) {
			// retângulo em pé no plano xy, com a base no chão: posição 3, cor 3, normal 3 (zero), uv 2
			float l = aparencia.larguraSubstituto() / 2, a = aparencia.alturaSubstituto();
			float[][] cantos = { { -l, 0 }, { l, 0 }, { l, a }, { l, a }, { -l, a }, { -l, 0 } };
			FloatBuffer b = BufferUtils.createFloatBuffer(6 * 11);
			for (float[] c : cantos) {
				b.put(c[0]).put(c[1]).put(0).put(1).put(1).put(1).put(0).put(0).put(0).put(0).put(0);
			}
			b.flip();
			vboSubstituto = glGenBuffers();
			glBindBuffer(GL_ARRAY_BUFFER, vboSubstituto);
			glBufferData(GL_ARRAY_BUFFER, b, GL_STATIC_DRAW);
		}
		glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	/**
	 * Desenha o monstro (se estiver visível). As matrizes vêm prontas (storeTranspose);
	 * cameraX/Z servem para o substituto virar para a câmera; alturaImagem é a altura, em pixels,
	 * da imagem onde a cena está sendo desenhada (a janela ou a imagem pequena do modo PS1).
	 */
	public void desenhar(DragonKnight m, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente,
			float cameraX, float cameraZ, int alturaImagem) {
		if (!m.visivel()) {
			return;
		}
		// morto, os olhos apagam
		desenhar(m, m.guinada, m.animador(), m.marcaDano(), m.estado() != DragonKnight.Estado.MORRENDO, projecao, visao,
				ambiente, cameraX, cameraZ, alturaImagem);
	}

	/** O Jubilado: sempre em cena (quando ativo); caído, os olhos apagam. */
	public void desenhar(Jubilado j, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente,
			float cameraX, float cameraZ, int alturaImagem) {
		if (!j.ativo) {
			return;
		}
		desenhar(j, j.guinada, j.animador(), j.marcaDano(), j.estado() != Jubilado.Estado.CAIDO, projecao, visao,
				ambiente, cameraX, cameraZ, alturaImagem);
	}

	private void desenhar(Object3D m, float guinada, Animador a, float dano, boolean olhosAcesos, FloatBuffer projecao,
			FloatBuffer visao, Ambiente ambiente, float cameraX, float cameraZ, int alturaImagem) {
		pontosOlhos.limpar();
		if (personagem != null) {
			desenharMd2(m, guinada, a, dano, projecao, visao, ambiente);
		} else {
			desenharSubstituto(m, dano, projecao, visao, ambiente, cameraX, cameraZ);
		}
		if (olhosAcesos) {
			pontosOlhos.desenhar(aparencia.tamanhoOlho(), aparencia.corOlho(), projecao, visao, ambiente, alturaImagem);
		}
	}

	private void desenharMd2(Object3D m, float guinada, Animador a, float dano, FloatBuffer projecao, FloatBuffer visao,
			Ambiente ambiente) {
		ShaderMd2 shader = Recursos.shaderMd2();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, guardar(modelo(m.x, m.y, m.z, guinada)));
		glUniform1f(glGetUniformLocation(p, "emissividade"), aparencia.emissividade());
		glUniform1f(glGetUniformLocation(p, "dano"), dano);
		ambiente.enviar(shader);
		personagem.desenhar(shader, a.quadroA(), a.quadroB(), a.fator(), true, true);
		shader.stop();

		// os olhos acompanham a animação: a mesma mistura de quadros que o vertex shader faz
		MalhaMd2 malha = personagem.malhaCorpo();
		float c = (float) Math.cos(guinada), s = (float) Math.sin(guinada);
		for (PontoMd2 olho : olhos) {
			float[] l = olho.posicao(malha, a.quadroA(), a.quadroB(), a.fator());
			float[] n = olho.normal(malha, a.quadroA(), a.quadroB(), a.fator());
			for (int k = 0; k < 3; k++) {
				l[k] += n[k] * 0.03f; // um pouco para fora da pele, para não ficar escondido nela
			}
			// o mesmo giro do modelo (rotação em y), depois a posição
			pontosOlhos.adicionar(m.x + c * l[0] + s * l[2], m.y + l[1], m.z - s * l[0] + c * l[2]);
		}
	}

	private void desenharSubstituto(Object3D m, float dano, FloatBuffer projecao, FloatBuffer visao, Ambiente ambiente,
			float cameraX, float cameraZ) {
		// o retângulo tem a frente em +z; girar por atan2(dx, dz) leva +z para a câmera
		float virar = (float) Math.atan2(cameraX - m.x, cameraZ - m.z);
		ShaderObjeto shader = Recursos.shaderObjeto();
		shader.start();
		int p = shader.programID;
		glUniformMatrix4fv(glGetUniformLocation(p, "projection"), false, projecao);
		glUniformMatrix4fv(glGetUniformLocation(p, "view"), false, visao);
		glUniformMatrix4fv(glGetUniformLocation(p, "model"), false, guardar(modelo(m.x, m.y, m.z, virar)));
		glUniform1f(glGetUniformLocation(p, "emissividade"), 0.35f + dano);
		glUniform1i(glGetUniformLocation(p, "tex"), 0);
		ambiente.enviar(shader);
		glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, Recursos.texturaGerada("monstro substituto", () -> {
			BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) img.setRGB(x, y, 0xFF8A0E0A);
			return img;
		}, false, true).id());

		int bytes = 11 * 4;
		glBindBuffer(GL_ARRAY_BUFFER, vboSubstituto);
		glEnableVertexAttribArray(0);
		glVertexAttribPointer(0, 3, GL_FLOAT, false, bytes, 0);
		glDisableVertexAttribArray(1);
		glVertexAttrib3f(1, 1, 1, 1);
		glEnableVertexAttribArray(2);
		glVertexAttribPointer(2, 3, GL_FLOAT, false, bytes, 24);
		glEnableVertexAttribArray(3);
		glVertexAttribPointer(3, 2, GL_FLOAT, false, bytes, 36);
		glDrawArrays(GL_TRIANGLES, 0, 6);
		glBindBuffer(GL_ARRAY_BUFFER, 0);
		shader.stop();

		// dois olhos no alto do retângulo, um pouco à frente dele
		float c = (float) Math.cos(virar), s = (float) Math.sin(virar);
		for (float lado : new float[] { -0.22f, 0.22f }) {
			float lx = lado, lz = 0.05f;
			pontosOlhos.adicionar(m.x + c * lx + s * lz, m.y + aparencia.alturaSubstituto() * 0.85f, m.z - s * lx + c * lz);
		}
	}

	/**
	 * Um olho em relação aos pés do monstro, no espaço do modelo (sem o giro), no quadro de agora.
	 * Usado para pôr a cabeça bem na frente da câmera no susto.
	 */
	public float[] olhoLocal(Animador a) {
		if (personagem == null || olhos.isEmpty() || a == null) {
			return new float[] { 0, aparencia.alturaSubstituto() * 0.85f, 0 };
		}
		return olhos.get(0).posicao(personagem.malhaCorpo(), a.quadroA(), a.quadroB(), a.fator());
	}

	/** A matriz model: primeiro gira em volta de y, depois leva até a posição. */
	private static Matrix4f modelo(float x, float y, float z, float guinada) {
		Matrix4f m = new Matrix4f();
		m.translate(new Vector3f(x, y, z));
		m.rotate(guinada, new Vector3f(0, 1, 0));
		return m;
	}

	private FloatBuffer guardar(Matrix4f m) {
		bufferMatriz.clear();
		m.storeTranspose(bufferMatriz);
		bufferMatriz.flip();
		return bufferMatriz;
	}

	public void liberar() {
		pontosOlhos.liberar();
		if (vboSubstituto != 0) glDeleteBuffers(vboSubstituto);
	}
}
