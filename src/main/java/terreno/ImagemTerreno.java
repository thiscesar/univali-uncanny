package terreno;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Transforma um terreno em imagens:
 *  - alturas(): heightmap em tons de cinza (preto = baixo, branco = alto),
 *    o formato padrão para deslocar uma malha 3D em computação gráfica;
 *  - colorida(): cores por faixa de altura + sombreamento, para usar como textura ou prévia.
 */
public final class ImagemTerreno {
    private ImagemTerreno() {}

    public static BufferedImage alturas(Terreno t) {
        BufferedImage img = new BufferedImage(t.largura(), t.comprimento(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < t.comprimento(); y++) {
            for (int x = 0; x < t.largura(); x++) {
                int v = Math.round(t.alturaEm(x, y) * 255);
                img.setRGB(x, y, new Color(v, v, v).getRGB());
            }
        }
        return img;
    }

    public static BufferedImage colorida(Terreno t) {
        BufferedImage img = new BufferedImage(t.largura(), t.comprimento(), BufferedImage.TYPE_INT_RGB);
        float intensidade = t.largura() * 0.15f;

        for (int y = 0; y < t.comprimento(); y++) {
            for (int x = 0; x < t.largura(); x++) {
                float a = t.alturaEm(x, y);
                Color base = corPorAltura(a);

                // sombreamento simples: luz vindo do canto superior esquerdo
                float luz = 1f;
                if (a >= Terreno.NIVEL_AGUA) {
                    float vizinho = t.alturaEm(Math.max(x - 1, 0), Math.max(y - 1, 0));
                    luz = Math.max(0.6f, Math.min(1.4f, 1f + (a - vizinho) * intensidade));
                }
                img.setRGB(x, y, escurecer(base, luz).getRGB());
            }
        }
        return img;
    }

    public static void salvar(BufferedImage imagem, String caminho) {
        try {
            ImageIO.write(imagem, "png", new File(caminho));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Color corPorAltura(float a) {
        if (a < Terreno.NIVEL_AGUA - 0.10f) return new Color(20, 50, 120);   // água funda
        if (a < Terreno.NIVEL_AGUA)         return new Color(40, 100, 180);  // água rasa
        if (a < Terreno.NIVEL_AGUA + 0.03f) return new Color(210, 195, 140); // areia
        if (a < 0.60f)                      return new Color(80, 160, 70);   // grama
        if (a < Terreno.NIVEL_MONTANHA)     return new Color(45, 110, 50);   // floresta
        if (a < 0.88f)                      return new Color(120, 110, 100); // rocha
        return new Color(240, 240, 245);                                     // neve
    }

    private static Color escurecer(Color c, float fator) {
        return new Color(
                Math.min(255, Math.round(c.getRed() * fator)),
                Math.min(255, Math.round(c.getGreen() * fator)),
                Math.min(255, Math.round(c.getBlue() * fator)));
    }
}
