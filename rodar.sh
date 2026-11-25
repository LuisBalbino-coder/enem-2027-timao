#!/usr/bin/env bash
# Sobe o ENEM 2027 Timão em http://localhost:7077
# Usa o JDK 21 e o Maven instalados em ~/.local/opt (sem sudo).
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21}"
export PATH="$JAVA_HOME/bin:$HOME/.local/opt/maven/bin:$PATH"

if [ ! -f target/enem-2027-timao.jar ]; then
  echo "Compilando (primeira execução)..."
  mvn -q -DskipTests package
fi

exec java -jar target/enem-2027-timao.jar "$@"
