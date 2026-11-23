package obj;

import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers; // ALTERADO: usado no liberar()
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;

import Model.Model;

public class ObjHTGsrtm extends Model {

    int vbo_vertex_handle;
    int vbo_color_handle;
    int vbo_texture_handle;
    int vbo_normal_handle;

    int vertices = 0;
    int vertex_size = 3; // X, Y, Z,
    int color_size = 3; // R, G, B,
    int normal_size = 3;
    int texture_size = 2;

    public int data[][];
    public int wh;

    /** Comportamento original: lê o relevo real do arquivo S28W049.hgt. */
    public ObjHTGsrtm() {
    }

    /**
     * Usa alturas prontas (em metros, água <= 0), por exemplo geradas pelo
     * algoritmo genético.
     */
    public ObjHTGsrtm(int[][] alturas) {
        this.data = alturas;
        this.wh = alturas.length;
    }

    @Override
    public void load() {
        if (data == null) {
            lerArquivoHgt();
        }
        construirMalha();
    }

    private void lerArquivoHgt() {
        // ADIQUIRIDO DE: https://search.earthdata.nasa.gov/search?q=altimetry
        // https://search.earthdata.nasa.gov/search/granules?p=C2763264762-LPCLOUD&pg[0][v]=f&pg[0][gsk]=-start_date&g=G2816782542-LPCLOUD&sb[0]=-48.85833%2C-28.05259%2C-48.09463%2C-27.22687&fi=SRTM&lat=-27.5&long=-48.47858994434932&zoom=9.681276930185463
        File f = new File("S28W049.hgt");
        int l = (int) f.length();
        System.out.println("l " + l);
        wh = (int) Math.sqrt(l / 2);

        System.out.println("wh " + wh);

        try {
            BufferedInputStream bin = new BufferedInputStream(new FileInputStream(f), 1024000);
            DataInputStream din = new DataInputStream(bin);

            data = new int[wh][wh];
            for (int j = 0; j < wh; j++) {
                for (int i = 0; i < wh; i++) {
                    data[j][i] = din.readShort();
                    // System.out.println(""+data[j][i]);
                }
            }

            int dataB[][] = new int[wh / 4][wh / 4];
            for (int j = 0; j < (wh - 4); j += 4) {
                for (int i = 0; i < (wh - 4); i += 4) {
                    int soma = 0;
                    for (int jy = 0; jy < 4; jy++) {
                        for (int ix = 0; ix < 4; ix++) {
                            soma += data[j + jy][i + ix];
                        }
                    }
                    dataB[j / 4][i / 4] = (soma / 16);
                }
            }

            data = dataB;
            wh = wh / 4;
            // TIRAR DEPOIS
            // wh = 1000;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // escala da malha (iguais aos valores originais do professor)
    private static final float PASSO_XZ = 0.04f;
    private static final float FATOR_ALTURA = 0.004f; // era 0.002f

    /**
     * Monta a malha do terreno. Cada vértice leva:
     * - posição: com a água achatada no nível 0;
     * - cor.x: a altura REAL em metros / 1000, para o shader saber a profundidade
     * da água
     * e escolher as texturas;
     * - normal suave, calculada a partir dos vizinhos;
     * - textuv: a coordenada (i, j) da célula, que o shader usa para repetir as
     * texturas.
     */
    private void construirMalha() {
        vertices = (wh - 1) * (wh - 1) * 6;
        FloatBuffer vertex_data = BufferUtils.createFloatBuffer(vertices * vertex_size);
        FloatBuffer texture_data = BufferUtils.createFloatBuffer(vertices * texture_size);
        FloatBuffer color_data = BufferUtils.createFloatBuffer(vertices * color_size);
        FloatBuffer normal_data = BufferUtils.createFloatBuffer(vertices * normal_size);

        for (int j = 0; j < wh - 1; j++) {
            for (int i = 0; i < wh - 1; i++) {
                // dois triângulos por célula, na mesma ordem do código original
                adicionarVertice(i, j, vertex_data, color_data, normal_data, texture_data);
                adicionarVertice(i + 1, j, vertex_data, color_data, normal_data, texture_data);
                adicionarVertice(i + 1, j + 1, vertex_data, color_data, normal_data, texture_data);

                adicionarVertice(i + 1, j + 1, vertex_data, color_data, normal_data, texture_data);
                adicionarVertice(i, j + 1, vertex_data, color_data, normal_data, texture_data);
                adicionarVertice(i, j, vertex_data, color_data, normal_data, texture_data);
            }
        }
        System.out.println("vcount " + vertices);

        vertex_data.flip();
        color_data.flip();
        texture_data.flip();
        normal_data.flip();

        vbo_vertex_handle = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo_vertex_handle);
        glBufferData(GL_ARRAY_BUFFER, vertex_data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        vbo_color_handle = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo_color_handle);
        glBufferData(GL_ARRAY_BUFFER, color_data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        vbo_texture_handle = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo_texture_handle);
        glBufferData(GL_ARRAY_BUFFER, texture_data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        vbo_normal_handle = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo_normal_handle);
        glBufferData(GL_ARRAY_BUFFER, normal_data, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void adicionarVertice(int i, int j, FloatBuffer vertex_data, FloatBuffer color_data,
            FloatBuffer normal_data, FloatBuffer texture_data) {
        int metros = data[j][i];

        vertex_data.put(i * PASSO_XZ);
        vertex_data.put(alturaVisual(i, j));
        vertex_data.put(j * PASSO_XZ);

        color_data.put(metros / 1000.0f);
        color_data.put(0f);
        color_data.put(0f);

        normal_data.put(normalEm(i, j));

        texture_data.put(i);
        texture_data.put(j);
    }

    /** Altura do vértice na malha: abaixo da água fica no nível 0 (água plana). */
    private float alturaVisual(int i, int j) {
        i = Math.max(0, Math.min(wh - 1, i));
        j = Math.max(0, Math.min(wh - 1, j));
        return Math.max(data[j][i], 0) * FATOR_ALTURA;
    }

    /** Normal suave do vértice, por diferenças centrais entre os vizinhos. */
    private float[] normalEm(int i, int j) {
        float nx = alturaVisual(i - 1, j) - alturaVisual(i + 1, j);
        float ny = 2 * PASSO_XZ;
        float nz = alturaVisual(i, j - 1) - alturaVisual(i, j + 1);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new float[] { nx / len, ny / len, nz / len };
    }

    @Override
    public void draw() {
        glBindBuffer(GL_ARRAY_BUFFER, vbo_vertex_handle);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, vertex_size, GL_FLOAT, false, 0, 0);

        glBindBuffer(GL_ARRAY_BUFFER, vbo_color_handle);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, color_size, GL_FLOAT, false, 0, 0);
        // glColorPointer(color_size, GL_FLOAT, 0, 0l);

        glBindBuffer(GL_ARRAY_BUFFER, vbo_normal_handle);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, normal_size, GL_FLOAT, false, 0, 0);

        glBindBuffer(GL_ARRAY_BUFFER, vbo_texture_handle);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, texture_size, GL_FLOAT, false, 0, 0);
        // glColorPointer(color_size, GL_FLOAT, 0, 0l);

        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);

        glDrawArrays(GL_TRIANGLES, 0, vertices);

        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
    }

    // ALTERADO: apaga os VBOs da malha, para gerar outro mapa sem acumular memória na placa de vídeo
    public void liberar() {
        glDeleteBuffers(vbo_vertex_handle);
        glDeleteBuffers(vbo_color_handle);
        glDeleteBuffers(vbo_texture_handle);
        glDeleteBuffers(vbo_normal_handle);
    }

    public float[] calculaVetorNormal(float p1[], float p2[], float p3[]) {
        float v1[] = new float[3];
        float v2[] = new float[3];
        float n[] = new float[3];

        v1[0] = p2[0] - p1[0];
        v1[1] = p2[1] - p1[1];
        v1[2] = p2[2] - p1[2];

        /* Encontra vetor v2 */
        v2[0] = p3[0] - p1[0];
        v2[1] = p3[1] - p1[1];
        v2[2] = p3[2] - p1[2];

        /* Calculo do produto vetorial de v1 e v2 */
        n[0] = (v1[1] * v2[2]) - (v1[2] * v2[1]);
        n[1] = (v1[2] * v2[0]) - (v1[0] * v2[2]);
        n[2] = (v1[0] * v2[1]) - (v1[1] * v2[0]);

        /* normalizacao de n */
        float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        n[0] /= -len;
        n[1] /= -len;
        n[2] /= -len;

        return n;
    }

}
