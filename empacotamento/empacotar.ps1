# Gera o pacote do jogo para Windows: uma pasta com o jogo, o Java embutido e o
# UnivaliUncanny.exe com ícone (jpackage), compactada em dist\UnivaliUncanny-windows.zip.
# Quem recebe não precisa instalar Java: é só extrair e clicar duas vezes no .exe.
#
# Uso, na raiz do projeto (PowerShell):  powershell -ExecutionPolicy Bypass -File empacotamento\empacotar.ps1
# Precisa de: JDK 17 ou mais novo (com o jpackage) e Maven.
$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$Nome = 'UnivaliUncanny'
$Versao = '1.0.0'
$Pacote = 'target\pacote'
$Entrada = "$Pacote\entrada"

# para o script se um programa externo (mvn, jpackage) falhar
function Conferir($etapa) {
	if ($LASTEXITCODE -ne 0) { throw "falhou: $etapa" }
}

Write-Host '== compilando'
# os -D vão entre aspas: sem elas o PowerShell separa o argumento no ponto
mvn -q '-DskipTests' package; Conferir 'mvn package'
$Jar = (Get-ChildItem target\*.jar | Select-Object -First 1).Name

Write-Host '== juntando as bibliotecas e os arquivos do jogo'
if (Test-Path $Pacote) { Remove-Item -Recurse -Force $Pacote }
New-Item -ItemType Directory -Force $Entrada | Out-Null
Copy-Item "target\$Jar" $Entrada
mvn -q 'org.apache.maven.plugins:maven-dependency-plugin:2.8:copy-dependencies' `
	'-DincludeScope=runtime' "-DoutputDirectory=$Entrada"; Conferir 'copy-dependencies'
# a lwjgl_util (matrizes do projeto original) puxa junto a LWJGL 2 inteira; o jogo só usa as
# classes de vetor/matriz dela, e a LWJGL 2 no classpath conflita com a 3 (mesmo pacote org.lwjgl)
Remove-Item "$Entrada\lwjgl-2.*.jar", "$Entrada\lwjgl-platform-2.*.jar", "$Entrada\jinput*.jar", "$Entrada\jutils*.jar"

foreach ($pasta in 'texturas', 'modelos', 'shaders', 'fontes', 'interface', 'sprites') {
	Copy-Item -Recurse $pasta $Entrada
}
# os sons, sem os MP3 originais e sem as gravações cruas da voz do professor
$raizAudio = (Resolve-Path 'audio').Path
Get-ChildItem -Recurse -File 'audio' |
	Where-Object { $_.Extension -ne '.mp3' -and $_.FullName -notlike "$raizAudio\professor\*" } |
	ForEach-Object {
		$destino = Join-Path "$Entrada\audio" $_.FullName.Substring($raizAudio.Length + 1)
		New-Item -ItemType Directory -Force (Split-Path $destino) | Out-Null
		Copy-Item $_.FullName $destino
	}
Copy-Item 'creditos.txt' $Entrada

Write-Host '== gerando o aplicativo (jpackage)'
# $APPDIR (entre aspas simples, para o PowerShell não trocar) é a pasta do jogo dentro do pacote:
# todos os arquivos são procurados lá (util.Caminhos), de onde quer que o jogo seja aberto
jpackage --type app-image --name $Nome --app-version $Versao `
	--input $Entrada --main-jar $Jar --main-class main.Main3D `
	--icon empacotamento\icone.ico --dest $Pacote `
	--java-options '-Xmx1g' --java-options '-Dunivali.assets=$APPDIR'; Conferir 'jpackage'

Write-Host '== compactando'
New-Item -ItemType Directory -Force 'dist' | Out-Null
$Zip = "dist\$Nome-windows.zip"
if (Test-Path $Zip) { Remove-Item $Zip }
Compress-Archive -Path "$Pacote\$Nome" -DestinationPath $Zip
$Mb = [math]::Round((Get-Item $Zip).Length / 1MB)
Write-Host "Pronto: $Zip ($Mb MB)"
