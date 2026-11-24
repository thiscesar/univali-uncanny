#!/bin/bash
# Gera o pacote do jogo para Linux: uma pasta com o jogo, o Java embutido e o executável
# (jpackage), compactada em dist/UnivaliUncanny-linux.tar.gz. Quem recebe não precisa instalar Java.
#
# Uso, na raiz do projeto:  bash empacotamento/empacotar.sh
# Precisa de: JDK 17 ou mais novo (com o jpackage) e Maven.
set -e
cd "$(dirname "$0")/.."

NOME=UnivaliUncanny
VERSAO=1.0.0
PACOTE=target/pacote
ENTRADA=$PACOTE/entrada

echo "== compilando"
mvn -q -DskipTests package
JAR=$(basename "$(ls target/*.jar | head -1)")

echo "== juntando as bibliotecas e os arquivos do jogo"
rm -rf "$PACOTE"
mkdir -p "$ENTRADA"
cp "target/$JAR" "$ENTRADA/"
mvn -q org.apache.maven.plugins:maven-dependency-plugin:2.8:copy-dependencies \
	-DincludeScope=runtime -DoutputDirectory="$ENTRADA"
# a lwjgl_util (matrizes do projeto original) puxa junto a LWJGL 2 inteira; o jogo só usa as
# classes de vetor/matriz dela, e a LWJGL 2 no classpath conflita com a 3 (mesmo pacote org.lwjgl)
rm -f "$ENTRADA"/lwjgl-2.*.jar "$ENTRADA"/lwjgl-platform-2.*.jar "$ENTRADA"/jinput*.jar "$ENTRADA"/jutils*.jar
for pasta in texturas modelos shaders fontes interface sprites; do
	cp -r "$pasta" "$ENTRADA/"
done
# os sons, sem os MP3 originais e sem as gravações cruas da voz do professor
mkdir -p "$ENTRADA/audio"
(cd audio && find . -type f ! -name '*.mp3' ! -path './professor/*' -exec cp --parents {} "../$ENTRADA/audio/" \;)
cp creditos.txt "$ENTRADA/"

echo "== gerando o aplicativo (jpackage)"
# $APPDIR é a pasta do jogo dentro do pacote: todos os arquivos são procurados lá (util.Caminhos),
# de onde quer que o jogo seja aberto
jpackage --type app-image --name "$NOME" --app-version "$VERSAO" \
	--input "$ENTRADA" --main-jar "$JAR" --main-class main.Main3D \
	--icon empacotamento/icone.png --dest "$PACOTE" \
	--java-options '-Xmx1g' --java-options '-Dunivali.assets=$APPDIR'

cp empacotamento/criar-atalho-linux.sh "$PACOTE/$NOME/"
chmod +x "$PACOTE/$NOME/criar-atalho-linux.sh"

echo "== compactando"
mkdir -p dist
tar -czf "dist/$NOME-linux.tar.gz" -C "$PACOTE" "$NOME"
echo "Pronto: dist/$NOME-linux.tar.gz ($(du -h "dist/$NOME-linux.tar.gz" | cut -f1))"
