package main;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import org.lwjgl.util.vector.Vector4f;

import Model.VboBilboard;
import Model.VboCube;
import dados.Constantes;
import dados.Recursos; // ALTERADO: recursos comuns do jogo
import obj.Cubo3D;
import obj.Mapa3D;
import obj.ObjHTGsrtm;
import obj.ObjModel;
import obj.Object3D;
import obj.Personagem;
import obj.Projetil;
import shaders.StaticShader;
import shaders.StaticShader2;
import shaders.StaticShaderTerreno;
import util.TexturaTerreno;
import static org.lwjgl.opengl.GL13.*;
import terreno.GeradorTerreno;
import util.TextureLoader;
import util.Utils3D;
import audio.Audio; // ALTERADO: som do jogo
import dados.Opcoes;
import entrada.Entrada; // ALTERADO: entrada centralizada
import telas.GerenciadorTelas; // ALTERADO: o laço delega para as telas
import telas.TelaMenu;

import java.awt.image.BufferedImage;

//import com.sun.org.apache.xerces.internal.dom.DeepNodeListImpl;

import java.nio.*;
import java.util.ArrayList;
import java.util.Random;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform4f;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniformMatrix4fv;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Main3D {

    // The window handle
    private long window;

    float viewAngX = 0;
    float viewAngY = 0;
    float scale = 1.0f;

    public Random rnd = new Random();

    StaticShader shader;
    StaticShader2 shader2;

    // ALTERADO: o shader e as texturas do terreno passaram para dados.Recursos e telas.TelaJogo

    public static ArrayList<Object3D> listaObjetos = new ArrayList<>();
    public static ArrayList<Object3D> listaProjeteis = new ArrayList<>();
    public static Personagem personagem;

    public static int ttverde = -1;

    // ALTERADO: teclado/mouse centralizados e a máquina de telas
    Entrada entrada;
    GerenciadorTelas gerenciador;
    Audio audio; // ALTERADO: som do jogo (OpenAL)

    float angle = 0;

    public void run() {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        init();
        loop();

        // ALTERADO: libera texturas e shaders comuns enquanto o contexto OpenGL ainda existe
        Recursos.liberar();
        audio.encerrar(); // ALTERADO: fecha o OpenAL

        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        // Setup an error callback. The default implementation
        // will print the error message in System.err.
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW. Most GLFW functions will not work before doing this.
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        // Configure GLFW
        glfwDefaultWindowHints(); // optional, the current window hints are already the default
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

        // Create the window
        // ALTERADO: com a opção de tela cheia (o padrão), a janela já nasce ocupando o monitor
        // principal, na resolução que ele usa; F11 ou as Opções voltam para a janela de 1500x1000.
        long monitor = glfwGetPrimaryMonitor();
        if (Opcoes.atual().telaCheia && monitor != NULL) {
            GLFWVidMode modo = glfwGetVideoMode(monitor);
            window = glfwCreateWindow(modo.width(), modo.height(), "Hello World!", monitor, NULL);
        } else {
            window = glfwCreateWindow(1500, 1000, "Hello World!", NULL, NULL);
        }
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // ALTERADO: o callback de teclado original (que ligava flags do Personagem e fechava
        // o jogo no ESC) foi substituído pelos callbacks da classe Entrada. Agora o ESC é
        // tratado por cada tela, e o jogo só fecha pelo X da janela ou pelo menu.
        entrada = new Entrada();
        entrada.registrar(window);

        // ALTERADO: som do jogo; sem placa de som, o jogo continua mudo
        audio = new Audio();
        audio.iniciar();
        Opcoes opcoes = Opcoes.atual();
        audio.definirVolumes(opcoes.volumeGeral, opcoes.volumeMusica, opcoes.volumeEfeitos);

        gerenciador = new GerenciadorTelas(window, entrada, audio);

        // ALTERADO: quando a janela muda de tamanho, a área de desenho e a proporção da
        // projeção acompanham (antes a proporção era fixa e a imagem distorcia).
        glfwSetFramebufferSizeCallback(window, (janela, largura, altura) -> {
            gerenciador.redimensionar(largura, altura);
            glViewport(0, 0, Math.max(1, largura), Math.max(1, altura));
        });

        // Get the thread stack and push a new frame
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1); // int*
            IntBuffer pHeight = stack.mallocInt(1); // int*

            // Get the window size passed to glfwCreateWindow
            glfwGetWindowSize(window, pWidth, pHeight);

            // Get the resolution of the primary monitor
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

            // Center the window
            glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
        } // the stack frame is popped automatically

        // Make the OpenGL context current
        glfwMakeContextCurrent(window);
        // Enable v-sync
        glfwSwapInterval(1);

        // Make the window visible
        glfwShowWindow(window);
    }

    private void loop() {
        // This line is critical for LWJGL's interoperation with GLFW's
        // OpenGL context, or any context that is managed externally.
        // LWJGL detects the context that is current in the current thread,
        // creates the GLCapabilities instance and makes the OpenGL
        // bindings available for use.

        GL.createCapabilities();

        Constantes.vboc = new VboCube();
        Constantes.vboc.load();
        shader = new StaticShader();
        shader2 = new StaticShader2();

        // ALTERADO: shader e texturas do terreno agora são carregados por dados.Recursos

        Constantes.vboBilbord = new VboBilboard();
        Constantes.vboBilbord.load();

        // ALTERADO: as texturas do gato, da multtexture, do Mig, dos tiros, da explosão, do cubo e
        // do tiro verde não são usadas pelo jogo (o personagem original não atira mais) e os
        // arquivos saíram do projeto. O código fica comentado para mostrar como era.
        // Constantes.tgato = TextureLoader.loadTexture_from_image("texturaGato.jpeg");
        // BufferedImage imgf104 =
        // TextureLoader.loadTexture_from_image("texturaf104.jpg");
        // BufferedImage imgsr71 = TextureLoader.loadTexture_from_image("sr71.jpg");

        // Constantes.tmult = TextureLoader.loadTexture_from_image("multtexture.png");
        // System.out.println("tmult " + Constantes.tmult);

        // Constantes.txtmig = TextureLoader.loadTexture_from_image("TexturaMig01.png");
        // System.out.println("tmult " + Constantes.tmult);

        // Constantes.texturaTiro = TextureLoader.loadTexture_from_image("texturaTiro.png");
        // System.out.println("texturaTiro " + Constantes.texturaTiro);

        // Constantes.texturaExplosao = TextureLoader.loadTexture_from_image("texturaExplosao.png");
        // System.out.println("texturaExplosao " + Constantes.texturaExplosao);

        // Cubo3D cubo = new Cubo3D(0.0f, 0.0f, -1.0f, 0.2f);
        // cubo.vbocube = vboc;

        // ObjModel x35 = new ObjModel();
        // x35.loadObj("x-35_obj.obj");
        // x35.load();

        // ALTERADO: o avião e o SR71 saíram da cena. As classes continuam no projeto.
        // System.out.println("f104starfighter.obj");
        // ObjModel f101 = new ObjModel();
        // f101.loadObj("x-35_obj.obj"); // caminho ajustado: funciona no Windows e no Linux
        // f101.load();
        // System.out.println("Faces: " + f101.f.size());
        //
        // System.out.println("AIM120D");
        // ObjModel sr71 = new ObjModel();
        // // sr71.loadObj("..\\OBJ\\SR71.obj");
        // sr71.loadObj("SR71.obj"); // caminho ajustado: funciona no Windows e no Linux
        // sr71.load();
        // System.out.println("Faces: " + sr71.f.size());

        // ALTERADO: a geração do terreno passou para a tela de carregamento (telas.TelaCarregando)
        // Relevo real (arquivo .hgt da NASA), como no original:
        // ObjHTGsrtm mapmodel = new ObjHTGsrtm();

        // ALTERADO: os 1000 cubos saíram da cena. A lista continua existindo (vazia),
        // porque o Projetil percorre Main3D.listaObjetos.
        // for (int i = 0; i < 1000; i++) {
        //     Cubo3D cubo = new Cubo3D(rnd.nextFloat() * 40 - 20, rnd.nextFloat() * 40 - 20, rnd.nextFloat() * 40 - 20,
        //             0.4f);
        //     cubo.model = Constantes.vboc;
        //     cubo.vx = 0;
        //     cubo.vy = 0;
        //     cubo.vz = 0;
        //     cubo.rotvel = rnd.nextFloat() * 9;
        //     listaObjetos.add(cubo);
        // }

        // ALTERADO: o personagem continua sendo criado para o campo estático não ficar vazio,
        // mas não tem mais modelo e não é simulado nem desenhado.
        personagem = new Personagem(0, 0, 0, 0.01f);
        // personagem.model = f101;

        // ALTERADO: texturas sem uso, ver acima
        // BufferedImage imgcube = TextureLoader.loadImage("texturecube.png");
        // int tcube = TextureLoader.loadTexture(imgcube);

        // BufferedImage imgtverde = TextureLoader.loadImage("tiro_verde.png");
        // ttverde = TextureLoader.loadTexture(imgtverde);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.

        int frame = 0;
        long lasttime = System.currentTimeMillis();

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(45, 600f / 800f, 0.5f, 100);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // ALTERADO: o laço agora só mede o tempo, lê os eventos e troca os buffers.
        // O que atualizar e o que desenhar é decidido pela tela atual (pacote telas).
        // O SimulaMundo e o DesenhaMundo saíram: o desenho do terreno está em telas.TelaJogo.
        try (MemoryStack stack = stackPush()) {
            IntBuffer largura = stack.mallocInt(1);
            IntBuffer altura = stack.mallocInt(1);
            glfwGetFramebufferSize(window, largura, altura);
            gerenciador.redimensionar(largura.get(0), altura.get(0));
            glViewport(0, 0, gerenciador.largura(), gerenciador.altura());
        }

        gerenciador.trocarPara(new TelaMenu(gerenciador));
        gerenciador.aplicarTrocas();

        long ultimoTempo = System.currentTimeMillis();
        while (!glfwWindowShouldClose(window)) {

            long diftime = System.currentTimeMillis() - ultimoTempo;
            ultimoTempo = System.currentTimeMillis();

            // Poll for window events. The callbacks will only be invoked during this call.
            glfwPollEvents();

            gerenciador.atualizar(diftime);
            gerenciador.desenhar();
            glfwSwapBuffers(window); // swap the color buffers

            entrada.fimDoQuadro();
            gerenciador.aplicarTrocas(); // trocas de tela só no fim do quadro

            frame++;
            long actualTime = System.currentTimeMillis();
            if ((lasttime / 1000) != (actualTime / 1000)) {
                System.out.println("FPS " + frame);
                frame = 0;
                lasttime = actualTime;
            }

        }

        gerenciador.encerrar();
    }

    public static void main(String[] args) {
        new Main3D().run();
    }

    public static void gluPerspective(float fovy, float aspect, float near, float far) {
        float bottom = -near * (float) Math.tan(fovy / 2);
        float top = -bottom;
        float left = aspect * bottom;
        float right = -left;
        glFrustum(left, right, bottom, top, near, far);
    }

    public static Matrix4f setFrustum(float l, float r, float b, float t, float n, float f) {
        Matrix4f m = new Matrix4f();
        m.m00 = 2 * n / (r - l);
        m.m01 = 0.0f;
        m.m02 = 0.0f;
        m.m03 = 0.0f;

        m.m10 = 0.0f;
        m.m11 = 2 * n / (t - b);
        m.m12 = 0.0f;
        m.m13 = 0.0f;

        m.m20 = (r + l) / (r - l);
        m.m21 = (t + b) / (t - b);
        m.m22 = -(f + n) / (f - n);
        m.m23 = -1;

        m.m30 = 0.0f;
        m.m31 = 0.0f;
        m.m32 = -(2 * f * n) / (f - n);
        m.m33 = 0;

        return m;
    }

}
