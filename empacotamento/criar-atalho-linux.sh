#!/bin/bash
# Cria o atalho do Univali Uncanny no menu de aplicativos (com o ícone), apontando para esta
# pasta. Rode uma vez depois de extrair o jogo:  ./criar-atalho-linux.sh
set -e
PASTA="$(cd "$(dirname "$0")" && pwd)"
DESTINO="$HOME/.local/share/applications"
mkdir -p "$DESTINO"
cat > "$DESTINO/univali-uncanny.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Univali Uncanny
Comment=Jogo de terror em primeira pessoa
Exec="$PASTA/bin/UnivaliUncanny"
Icon=$PASTA/lib/UnivaliUncanny.png
Terminal=false
Categories=Game;
EOF
chmod +x "$DESTINO/univali-uncanny.desktop"
echo "Atalho criado: procure \"Univali Uncanny\" no menu de aplicativos."
