package visual;

import java.awt.Graphics2D;

/** Sabe desenhar um tipo de indivíduo. Cada problema fornece o seu. */
public interface Desenho<T> {

    /** Desenha o indivíduo dentro da área (x, y, largura, altura). */
    void desenhar(Graphics2D g2, T individuo, int x, int y, int largura, int altura);

    /** Um texto curto sobre o indivíduo, mostrado no topo da tela. */
    String descrever(T individuo);
}
