#!/usr/bin/env bash
# Sobe o ENEM 2027 Timão em http://127.0.0.1:7077
# Usa o JDK 21 e o Maven instalados em ~/.local/opt (sem sudo).
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21}"
export PATH="$JAVA_HOME/bin:$HOME/.local/opt/maven/bin:$PATH"

JAR="target/enem-2027-timao.jar"
PRECISA_COMPILAR=0

if [ ! -f "$JAR" ]; then
  PRECISA_COMPILAR=1
elif [ -n "$(find src pom.xml -newer "$JAR" -print -quit)" ]; then
  PRECISA_COMPILAR=1
fi

if [ "$PRECISA_COMPILAR" = "1" ]; then
  echo "Fontes mais novos que o jar — compilando..."
  mvn -q -DskipTests package
fi

exec java -jar "$JAR" "$@"
