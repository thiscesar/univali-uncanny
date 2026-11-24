package mundo;

import java.util.List;

import fase.GeradorFase;
import fase.Placa;
import fase.Ponto;
import md2.PersonagemMd2;
import obj.Mapa3D;
import terreno.PerfilTerreno;

/**
 * Tudo o que forma o lugar da partida: o terreno (malha do OpenGL), as perguntas sobre
 * o chão, as árvores, as pedras, a grade de colisão, o modelo do monstro e a fase (início, provas,
 * caixas, Univali, trilhas e placas, decididos pelo AG da fase).
 * É o que a tela de carregamento entrega pronto para a tela de jogo.
 */
public class Mundo {

	public final long seed;
	public final PerfilTerreno perfil;
	/** O terreno desenhável, do projeto original (Mapa3D com a malha ObjHTGsrtm). */
	public final Mapa3D terreno;
	public final ConsultaAltura chao;
	public final GradeColisao colisao;
	/** As árvores planas: só quando os modelos 3D não estão na pasta (senão, null). */
	public final Arvores arvores;
	/** As árvores 3D, feitas pelo gerador (null quando os modelos não estão na pasta). */
	public final Arvores3D arvores3d;
	/** O Dragon Knight já na placa de vídeo (null sem o MD2: o jogo desenha o substituto). */
	public final PersonagemMd2 monstro;
	/** A fase decidida pelo AG da fase: início, Univali, provas, caixas, trilhas, placas e o fitness. */
	public final GeradorFase.Fase fase;
	/** Onde o jogador começa (x, z): dentro da Univali, numa sala de aula da frente. */
	public final float[] inicio;
	/** Onde ficam as provas M1, M2 e M3 (x, z), cada uma numa clareira. */
	public final List<float[]> provas;
	/** Onde ficam as caixas de munição (x, z). */
	public final List<float[]> caixas;
	/** Onde fica a Univali (x, z). */
	public final float[] univali;
	/** As placas do evento e as de trilha. */
	public final List<Placa> placas;
	/** As pedras já na placa de vídeo (null sem os modelos). */
	public final Pedras pedras;
	/** A textura do mapa de detalhes (trilhas e regiões), que o shader do terreno lê. */
	public final int texturaDetalhes;
	/** O prédio da Univali, na chegada da trilha. */
	public final entidades.PredioUnivali predio;

	public Mundo(long seed, PerfilTerreno perfil, Mapa3D terreno, ConsultaAltura chao, GradeColisao colisao,
			Arvores arvores, Arvores3D arvores3d, PersonagemMd2 monstro, GeradorFase.Fase fase, Pedras pedras,
			int texturaDetalhes, entidades.PredioUnivali predio) {
		this.seed = seed;
		this.perfil = perfil;
		this.terreno = terreno;
		this.chao = chao;
		this.colisao = colisao;
		this.arvores = arvores;
		this.arvores3d = arvores3d;
		this.monstro = monstro;
		this.fase = fase;
		float[] dentro = predio.inicioJogador();
		this.inicio = new float[] { dentro[0], dentro[2] };
		this.provas = fase.layout().provas().stream().map(Mundo::xz).toList();
		this.caixas = fase.layout().caixas().stream().map(Mundo::xz).toList();
		this.univali = xz(fase.layout().univali());
		this.placas = fase.placas();
		this.pedras = pedras;
		this.texturaDetalhes = texturaDetalhes;
		this.predio = predio;
	}

	private static float[] xz(Ponto p) {
		return new float[] { p.x(), p.z() };
	}

	/** Quantas árvores a floresta tem, planas ou 3D. */
	public int quantidadeArvores() {
		return arvores3d != null ? arvores3d.quantidade() : arvores.quantidade();
	}

	/** Lado do mapa em metros. */
	public float tamanho() {
		return chao.tamanho();
	}

	/** Apaga da placa de vídeo o que este mundo usa. Só na thread principal. */
	public void liberar() {
		terreno.model.liberar();
		if (arvores != null) arvores.liberar();
		if (arvores3d != null) arvores3d.liberar();
		if (monstro != null) monstro.liberar();
		if (pedras != null) pedras.liberar();
		org.lwjgl.opengl.GL11.glDeleteTextures(texturaDetalhes);
	}
}
