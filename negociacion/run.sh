#!/bin/bash
# Compila y ejecuta las pruebas (solo requiere JDK 17+).
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir out
javac -encoding UTF-8 -d out src/servilink/negociacion/*.java test/servilink/negociacion/*.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp out servilink.negociacion.PoliticaNegociacionPrueba
