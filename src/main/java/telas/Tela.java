package telas;

/**
 * Uma tela do jogo: menu, carregamento, partida, pausa, vitória, derrota...
 *
 * O Main3D não sabe o que cada tela faz: a cada quadro ele só pede ao GerenciadorTelas
 * para atualizar e desenhar a tela atual.
 */
public interface Tela {

	/** Chamado quando a tela passa a ser a atual (por exemplo, para prender o cursor). */
	void entrar();

	/** Chamado quando a tela deixa de ser a atual (por exemplo, para soltar o cursor). */
	void sair();

	/** Lógica do quadro. diftime em milissegundos. */
	void atualizar(long diftime);

	/** Desenho do quadro. A troca de buffers é feita pelo Main3D. */
	void desenhar();
}
