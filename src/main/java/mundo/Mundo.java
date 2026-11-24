package mundo;

import md2.PersonagemMd2;
import obj.Mapa3D;
import terreno.PerfilTerreno;

/**
 * Tudo o que forma o lugar da partida: o terreno (malha do OpenGL), as perguntas sobre
 * o chão, as árvores, a grade de colisão com os troncos, o modelo do monstro, o ponto de início do
 * jogador e os lugares das provas e da Univali.
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
	/** Onde o jogador começa (x, z), já com a clareira em volta. */
	public final float[] inicio;
	/** Onde ficam as provas M1, M2 e M3 (x, z), cada uma numa clareira. */
	public final java.util.List<float[]> provas;
	/** Onde fica a Univali (x, z). */
	public final float[] univali;

	public Mundo(long seed, PerfilTerreno perfil, Mapa3D terreno, ConsultaAltura chao, GradeColisao colisao,
			Arvores arvores, Arvores3D arvores3d, PersonagemMd2 monstro, float[] inicio, java.util.List<float[]> provas,
			float[] univali) {
		this.seed = seed;
		this.perfil = perfil;
		this.terreno = terreno;
		this.chao = chao;
		this.colisao = colisao;
		this.arvores = arvores;
		this.arvores3d = arvores3d;
		this.monstro = monstro;
		this.inicio = inicio;
		this.provas = provas;
		this.univali = univali;
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
	}
}
