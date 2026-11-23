package terreno;

import visual.Desenho;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Sabe desenhar um terreno visto de cima, colorido por altura. */
public class DesenhoTerreno implements Desenho<Terreno> {

    @Override
    public String descrever(Terreno t) {
        return String.format("Água %.0f%%   Montanha %.0f%%   Rugosidade %.4f",
                t.fracaoAbaixo(Terreno.NIVEL_AGUA) * 100,
                t.fracaoAcima(Terreno.NIVEL_MONTANHA) * 100,
                t.rugosidade());
    }

    @Override
    public void desenhar(Graphics2D g2, Terreno t, int x, int y, int largura, int altura) {
        int lado = Math.min(largura, altura); // mantém o mapa quadrado
        int esquerda = x + (largura - lado) / 2;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(ImagemTerreno.colorida(t), esquerda, y, lado, lado, null);
    }
}
