package co.edu.unipamplona.servilink.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Protege el catálogo único de estados (E01 a E07) acordado en los documentos 2.0. */
class EstadoSolicitudTest {

    @Test
    void elCatalogoTieneExactamenteSieteEstadosEnOrden() {
        assertThat(EstadoSolicitud.values()).extracting(EstadoSolicitud::codigo)
                .containsExactly("E01", "E02", "E03", "E04", "E05", "E06", "E07");
    }

    @Test
    void reconoceCodigosValidosEInvalidos() {
        assertThat(EstadoSolicitud.existe("E07")).isTrue();
        assertThat(EstadoSolicitud.existe("E08")).isFalse();
        assertThat(EstadoSolicitud.existe("E10")).isFalse(); // el DDS v1.0 llegaba a E10
        assertThat(EstadoSolicitud.existe(null)).isFalse();
        assertThat(EstadoSolicitud.existe("e01")).isFalse();
    }

    @Test
    void comparaConElCodigoGuardadoEnLaEntidad() {
        assertThat(EstadoSolicitud.E03.es("E03")).isTrue();
        assertThat(EstadoSolicitud.E03.es("E04")).isFalse();
        assertThat(EstadoSolicitud.E03.es(null)).isFalse();
        assertThat(EstadoSolicitud.esUnoDe("E04", EstadoSolicitud.E03, EstadoSolicitud.E04)).isTrue();
        assertThat(EstadoSolicitud.esUnoDe("E05", EstadoSolicitud.E03, EstadoSolicitud.E04)).isFalse();
    }

    @Test
    void laNuevaEntidadNaceAbiertaYLosGruposConservanSuContenido() {
        assertThat(EstadoSolicitud.codigos(EstadoSolicitud.E03, EstadoSolicitud.E04)).containsExactly("E03", "E04");
        assertThat(co.edu.unipamplona.servilink.aplicacion.SolicitudService.ACTIVAS)
                .containsExactly("E01", "E02", "E03", "E04");
        assertThat(co.edu.unipamplona.servilink.aplicacion.AdministracionService.ESTADOS)
                .containsExactly("E01", "E02", "E03", "E04", "E05", "E06", "E07");
    }

    /** Guarda contra la deuda original: ningún código de estado vuelve a escribirse como texto suelto. */
    @Test
    void elCodigoFuenteNoEscribeEstadosComoTextoSuelto() throws IOException {
        Pattern literal = Pattern.compile("\"E0[1-7]\"");
        try (Stream<Path> archivos = Files.walk(Path.of("src/main/java"))) {
            List<String> infractores = archivos
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("EstadoSolicitud.java"))
                    .filter(p -> {
                        try {
                            return literal.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(Path::toString).toList();
            assertThat(infractores).as("archivos con estados escritos como texto").isEmpty();
        }
    }
}
