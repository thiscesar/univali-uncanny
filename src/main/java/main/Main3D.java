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

    StaticShaderTerreno shaderTerreno;
    int texAgua, texAreia, texGrama, texPedra;

    public static ArrayList<Object3D> listaObjetos = new ArrayList<>();
    public static ArrayList<Object3D> listaProjeteis = new ArrayList<>();
    public static Personagem personagem;

    public static int ttverde = -1;

    float angle = 0;

    public void run() {
        System.out.println("Hello LWJGL " + Version.getVersion() + "!");

        init();
        loop();

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
        window = glfwCreateWindow(1500, 1000, "Hello World!", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        // Setup a key callback. It will be called every time a key is pressed, repeated
        // or released.

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true); // We will detect this in the rendering loop

            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_W) {
                    // viewAngX+=5;
                    personagem.UP = true;
                }
                if (key == GLFW_KEY_S) {
                    // viewAngX-=5;
                    personagem.DOWN = true;
                }
                if (key == GLFW_KEY_A) {
                    // viewAngY+=5;
                    personagem.LEFT = true;
                }
                if (key == GLFW_KEY_D) {
                    // viewAngY-=5;
                    personagem.RIGHT = true;
                }

                if (key == GLFW_KEY_Q) {
                    // viewAngY+=5;
                    personagem.QBu = true;
                }
                if (key == GLFW_KEY_E) {
                    // viewAngY-=5;
                    personagem.EBu = true;
                }

                if (key == GLFW_KEY_SPACE) {
                    // viewAngY-=5;
                    personagem.SHOOT = true;
                }

                if (key == GLFW_KEY_Z) {
                    scale = scale * 1.1f;
                }
                if (key == GLFW_KEY_X) {
                    scale = scale * 0.9f;
                }
            }

            if (action == GLFW_RELEASE) {
                if (key == GLFW_KEY_W) {
                    // viewAngX+=5;
                    personagem.UP = false;
                    // System.out.println("personagem.UP"+personagem.UP);
                }
                if (key == GLFW_KEY_S) {
                    // viewAngX-=5;
                    personagem.DOWN = false;
                }
                if (key == GLFW_KEY_A) {
                    // viewAngY+=5;
                    personagem.LEFT = false;
                }
                if (key == GLFW_KEY_D) {
                    // viewAngY-=5;
                    personagem.RIGHT = false;
                }
                if (key == GLFW_KEY_Q) {
                    // viewAngY+=5;
                    personagem.QBu = false;
                }
                if (key == GLFW_KEY_E) {
                    // viewAngY-=5;
                    personagem.EBu = false;
                }

                if (key == GLFW_KEY_Z) {
                    scale = scale * 1.1f;
                }
                if (key == GLFW_KEY_X) {
                    scale = scale * 0.9f;
                }

                if (key == GLFW_KEY_SPACE) {
                    personagem.SHOOT = false;
                }
            }

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

        shaderTerreno = new StaticShaderTerreno();
        texAgua = TexturaTerreno.carregar("texturas/agua.png");
        texAreia = TexturaTerreno.carregar("texturas/areia.png");
        texGrama = TexturaTerreno.carregar("texturas/grama.png");
        texPedra = TexturaTerreno.carregar("texturas/pedra.png");

        Constantes.vboBilbord = new VboBilboard();
        Constantes.vboBilbord.load();

        Constantes.tgato = TextureLoader.loadTexture_from_image("texturaGato.jpeg");
        // BufferedImage imgf104 =
        // TextureLoader.loadTexture_from_image("texturaf104.jpg");
        // BufferedImage imgsr71 = TextureLoader.loadTexture_from_image("sr71.jpg");

        Constantes.tmult = TextureLoader.loadTexture_from_image("multtexture.png");
        System.out.println("tmult " + Constantes.tmult);

        Constantes.txtmig = TextureLoader.loadTexture_from_image("TexturaMig01.png");
        System.out.println("tmult " + Constantes.tmult);

        Constantes.texturaTiro = TextureLoader.loadTexture_from_image("texturaTiro.png");
        System.out.println("texturaTiro " + Constantes.texturaTiro);

        Constantes.texturaExplosao = TextureLoader.loadTexture_from_image("texturaExplosao.png");
        System.out.println("texturaExplosao " + Constantes.texturaExplosao);

        // Cubo3D cubo = new Cubo3D(0.0f, 0.0f, -1.0f, 0.2f);
        // cubo.vbocube = vboc;

        // ObjModel x35 = new ObjModel();
        // x35.loadObj("x-35_obj.obj");
        // x35.load();

        System.out.println("f104starfighter.obj");
        ObjModel f101 = new ObjModel();
        f101.loadObj("x-35_obj.obj"); // caminho ajustado: funciona no Windows e no Linux
        f101.load();
        System.out.println("Faces: " + f101.f.size());

        System.out.println("AIM120D");
        ObjModel sr71 = new ObjModel();
        // sr71.loadObj("..\\OBJ\\SR71.obj");
        sr71.loadObj("SR71.obj"); // caminho ajustado: funciona no Windows e no Linux
        sr71.load();
        System.out.println("Faces: " + sr71.f.size());

        // Relevo real (arquivo .hgt da NASA), como no original:
        // ObjHTGsrtm mapmodel = new ObjHTGsrtm();

        // Relevo gerado pelo algoritmo genético (troque a seed para outro terreno):
        int[][] alturas = GeradorTerreno.gerar(42, 512);
        ObjHTGsrtm mapmodel = new ObjHTGsrtm(alturas);
        mapmodel.load();

        Constantes.mapa = new Mapa3D(-10.0f, 0.0f, -10.0f, 10);
        Constantes.mapa.model = mapmodel;

        for (int i = 0; i < 1000; i++) {
            // Cubo3D cubo = new Cubo3D(rnd.nextFloat()*2-1,rnd.nextFloat()*2-1,
            // rnd.nextFloat()*2-1, rnd.nextFloat()*0.005f+0.0001f);
            // cubo.model = x35;
            Cubo3D cubo = new Cubo3D(rnd.nextFloat() * 40 - 20, rnd.nextFloat() * 40 - 20, rnd.nextFloat() * 40 - 20,
                    0.4f);
            cubo.model = Constantes.vboc;
            cubo.vx = 0;
            cubo.vy = 0;
            cubo.vz = 0;
            cubo.rotvel = rnd.nextFloat() * 9;
            listaObjetos.add(cubo);
        }

        personagem = new Personagem(0, 0, 0, 0.01f);
        personagem.model = f101;

        BufferedImage imgcube = TextureLoader.loadImage("texturecube.png");
        int tcube = TextureLoader.loadTexture(imgcube);

        BufferedImage imgtverde = TextureLoader.loadImage("tiro_verde.png");
        ttverde = TextureLoader.loadTexture(imgtverde);

        // Run the rendering loop until the user has attempted to close
        // the window or has pressed the ESCAPE key.

        int frame = 0;
        long lasttime = System.currentTimeMillis();

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(45, 600f / 800f, 0.5f, 100);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glClearColor(0.55f, 0.75f, 0.95f, 1.0f);

        long ultimoTempo = System.currentTimeMillis();
        while (!glfwWindowShouldClose(window)) {

            long diftime = System.currentTimeMillis() - ultimoTempo;
            ultimoTempo = System.currentTimeMillis();

            SimulaMundo(diftime);

            DesenhaMundo(tcube);

            // Poll for window events. The key callback above will only be
            // invoked during this call.
            glfwPollEvents();
            frame++;
            long actualTime = System.currentTimeMillis();
            if ((lasttime / 1000) != (actualTime / 1000)) {
                System.out.println("FPS " + frame);
                frame = 0;
                lasttime = actualTime;
            }

        }
    }

    private void DesenhaMundo(int tcube) {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT); // clear the framebuffer

        glEnable(GL_LIGHTING);
        glShadeModel(GL_SMOOTH);

        // float senoang = (float) Math.sin(Math.toRadians(angle));
        // float cosang = (float) Math.cos(Math.toRadians(angle));
        glLoadIdentity();

        float[] lightAmbient = { 0.1f, 0.1f, 0.1f, 0.5f };
        float[] lightDiffuse = { 0.5f, 0.5f, 0.5f, 0.5f };
        float[] lightPosition = { 0.0f, 0.0f, 0.0f, 1.0f };
        // float[] lightPosition = { 0.0f, senoang * 600f, cosang * 600f, 1.0f };

        glLightfv(GL_LIGHT0, GL_AMBIENT, lightAmbient);
        glLightfv(GL_LIGHT0, GL_DIFFUSE, lightDiffuse);
        glLightfv(GL_LIGHT0, GL_POSITION, lightPosition);

        float[] lightAmbient2 = { 0.0f, 0.0f, 0.0f, 1.0f };
        float[] lightDiffuse2 = { 1.0f, 1.0f, 1.0f, 1.0f };
        float[] lightPosition2 = { -5f, -5f, 0f, 1.0f };

        glEnable(GL_DEPTH_TEST);

        shader.start();

        int projectionloc = glGetUniformLocation(shader.programID, "projection");
        int loctexture = glGetUniformLocation(shader.programID, "tex");
        int modellocation = glGetUniformLocation(shader.programID, "model");
        int viewlocation = glGetUniformLocation(shader.programID, "view");
        int corgeralloc = glGetUniformLocation(shader.programID, "corgeral");
        int angpos = glGetUniformLocation(shader.programID, "ang");

        shader2.start();
        int viewlocation2 = glGetUniformLocation(shader2.programID, "view");
        int modellocation2 = glGetUniformLocation(shader2.programID, "model");
        int projectionloc2 = glGetUniformLocation(shader2.programID, "projection");
        int loctexture2 = glGetUniformLocation(shader2.programID, "tex");
        // System.out.println("viewlocation2 "+viewlocation2);
        // System.out.println("modellocation2 "+modellocation2);
        // System.out.println("projectionloc2 "+projectionloc2);
        // System.out.println("tex2 "+loctexture2);

        float fov = (float) Math.toRadians(60.0f); // vertical FOV, in radians
        float aspect = (float) 1500 / 1000;
        float zNear = 0.01f;
        float zFar = 10000.0f;

        Matrix4f projection = setFrustum(-0.11f, 0.11f, -0.11f, 0.11f, 0.1f, 100.0f);
        FloatBuffer matrixProjection = MemoryUtil.memAllocFloat(16);
        projection.storeTranspose(matrixProjection);
        matrixProjection.flip();

        // Matrix4f view = new Matrix4f();
        // view.setIdentity();

        Matrix4f transf = new Matrix4f();
        transf.setIdentity();
        transf.translate(new Vector3f(-1, 0.5f, -3));
        transf.rotate((float) Math.PI, new Vector3f(0, 1, 0));

        // view.scale(new Vector3f(scale,scale,scale));
        // view.rotate(viewAngX*0.0174532f, new Vector3f(1,0,0));
        // view.rotate(viewAngY*0.0174532f, new Vector3f(0,1,0));
        // view.translate(new
        // Vector3f(-personagem.x,-personagem.y-0.5f,-personagem.z-1.5f));
        Vector4f pos = new Vector4f(personagem.x, personagem.y, personagem.z, 1);
        Vector4f t = new Vector4f(pos.dot(pos, personagem.right), pos.dot(pos, personagem.up),
                pos.dot(pos, personagem.front), 1.0f);
        Matrix4f view = Utils3D.setLookAtMatrix(t, personagem.front, personagem.up, personagem.right);
        view.mul(transf, view, view);

        FloatBuffer matrixView = MemoryUtil.memAllocFloat(16);
        view.storeTranspose(matrixView);
        matrixView.flip();

        shader2.start();
        glUniformMatrix4fv(projectionloc2, false, matrixProjection);
        glUniform1i(loctexture2, 0);
        glUniformMatrix4fv(viewlocation2, false, matrixView);

        for (int i = 0; i < listaProjeteis.size(); i++) {
            listaProjeteis.get(i).DesenhaSe(shader2);
        }

        shader.start();

        glUniformMatrix4fv(projectionloc, false, matrixProjection);
        glUniform1i(loctexture, 0);
        glUniformMatrix4fv(viewlocation, false, matrixView);

        desenharTerreno(matrixProjection, matrixView);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tcube);

        for (int i = 0; i < listaObjetos.size(); i++) {
            listaObjetos.get(i).DesenhaSe(shader);
        }

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, Constantes.tgato);

        personagem.DesenhaSe(shader);

        shader.stop();

        glfwSwapBuffers(window); // swap the color buffers
    }

    private void SimulaMundo(long diftime) {
        for (int i = 0; i < listaProjeteis.size(); i++) {
            Projetil proj = (Projetil) listaProjeteis.get(i);
            proj.setRotation(personagem.front, personagem.up, personagem.right);
            proj.SimulaSe(diftime);
            if (proj.vivo == false) {
                listaProjeteis.remove(i);
                i--;
            }

        }

        for (int i = 0; i < listaObjetos.size(); i++) {
            Object3D obj = listaObjetos.get(i);
            obj.SimulaSe(diftime);
            if (obj.vivo == false) {
                listaObjetos.remove(i);
                i--;
            }
        }
        personagem.SimulaSe(diftime);

        angle += 0.1;
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

    private void desenharTerreno(FloatBuffer projection, FloatBuffer view) {
        shaderTerreno.start();
        int prog = shaderTerreno.programID;
        glUniformMatrix4fv(glGetUniformLocation(prog, "projection"), false, projection);
        glUniformMatrix4fv(glGetUniformLocation(prog, "view"), false, view);
        glUniform1f(glGetUniformLocation(prog, "tempo"), (float) glfwGetTime());

        // cada textura vai numa "unidade" diferente: 0, 1, 2 e 3
        int[] texturas = { texAgua, texAreia, texGrama, texPedra };
        String[] nomes = { "texAgua", "texAreia", "texGrama", "texPedra" };
        for (int i = 0; i < 4; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, texturas[i]);
            glUniform1i(glGetUniformLocation(prog, nomes[i]), i);
        }

        Constantes.mapa.DesenhaSe(shaderTerreno);

        glActiveTexture(GL_TEXTURE0); // volta ao normal para o resto da cena
        shader.start(); // o resto da cena continua com o shader antigo
    }

}
