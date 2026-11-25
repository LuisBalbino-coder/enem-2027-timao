#!/usr/bin/env bash
# Roda os testes do backup e os testes Java do projeto.
set -euo pipefail
cd "$(dirname "$0")"

echo "Testes de validação e mesclagem do backup:"
node --test tests/backup-core.test.js

echo
echo "Testes Java:"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21}"
export PATH="$JAVA_HOME/bin:$HOME/.local/opt/maven/bin:$PATH"
mvn -q test

echo
echo "Todos os testes passaram."
