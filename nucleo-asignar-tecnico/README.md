# Núcleo del caso de uso «Asignar técnico» (ServiLink)

Refactorización hacia arquitectura hexagonal (puertos y adaptadores).

- `servilink/nucleo`: reglas de negocio, puertos de entrada y de salida. Solo importa `java.math`, `java.time` y `java.util`.
- `servilink/prueba`: 6 pruebas con dobles en memoria (sin base de datos).

## Compilar y ejecutar las pruebas (Java 21)

    javac -d out servilink/nucleo/*.java servilink/prueba/*.java
    java -cp out servilink.prueba.AsignarTecnicoPrueba

Resultado esperado: `6 pasaron, 0 fallaron`.
