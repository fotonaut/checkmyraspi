#!/usr/bin/env bash
# Lädt website/ nach checkmyraspi.mchwrk.de (Manitu). Alte APKs auf dem Server bleiben erhalten.
set -euo pipefail
cd "$(dirname "$0")/.."
HOST="ssh300010809@ngcobalt353.manitu.net"
DOCROOT="/home/sites/site100019747/web/mchwrk.de/checkmyraspi/"
KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"

# Ohne -p: Der Docroot gehört dem Site-User, Rechte kommen aus der Server-umask.
rsync -rltv --omit-dir-times -e "ssh -i $KEY" \
  --exclude '.DS_Store' --exclude '.claude' website/ "$HOST:$DOCROOT"
echo "==> https://checkmyraspi.mchwrk.de"
