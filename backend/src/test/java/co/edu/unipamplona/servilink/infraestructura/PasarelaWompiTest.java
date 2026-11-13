package co.edu.unipamplona.servilink.infraestructura;

import static co.edu.unipamplona.servilink.apoyo.EventoWompi.SECRETO;
import static co.edu.unipamplona.servilink.apoyo.EventoWompi.TIMESTAMP;
import static co.edu.unipamplona.servilink.apoyo.EventoWompi.checksum;
import static co.edu.unipamplona.servilink.apoyo.EventoWompi.evento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PasarelaWompiTest {

    private static final String INTEGRIDAD = "prod_integrity_Z5mMke9x0k8gpErbDqwrJXMqsI6SFli6";

    private static PasarelaWompi con(String publica, String integridad, String eventos, String publicaUrl) {
        return new PasarelaWompi(publica, integridad, eventos, "https://checkout.wompi.co/p/", publicaUrl);
    }

    private static PasarelaWompi normal() {
        return con("pub_test_abc", INTEGRIDAD, SECRETO, "https://servilink.ejemplo.co/");
    }

    private static Map<String, String> parametros(String url) {
        var m = new LinkedHashMap<String, String>();
        Arrays.stream(URI.create(url).getRawQuery().split("&")).forEach(p -> {
            String[] kv = p.split("=", 2);
            m.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        });
        return m;
    }

    private static void rechaza(Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ErrorNegocio.class,
                e -> assertThat(e.codigo()).isEqualTo(Codigo.INVALID_SIGNATURE));
    }

    @Test
    void firmaDeIntegridadCoincideConElEjemploOficialDeWompi() {
        assertThat(normal().firmaIntegridad("sk8-438k4-xmxm392-sn2m", 2490000))
                .isEqualTo("37c8407747e595535433ef8f6a811d853cd943046624a0ec04662b17bbf33bf5");
    }

    @Test
    void elCobroLlevaMontoEnCentavosReferenciaFirmaYRegreso() {
        var cobro = normal().iniciar("SL-abc", 50_000);
        assertThat(cobro.urlPago()).startsWith("https://checkout.wompi.co/p/?");
        assertThat(cobro.montoCentavos()).isEqualTo(5_000_000);
        var p = parametros(cobro.urlPago());
        assertThat(p).containsEntry("public-key", "pub_test_abc").containsEntry("currency", "COP")
                .containsEntry("amount-in-cents", "5000000").containsEntry("reference", "SL-abc")
                .containsEntry("signature:integrity", normal().firmaIntegridad("SL-abc", 5_000_000))
                .containsEntry("redirect-url", "https://servilink.ejemplo.co/?recarga=SL-abc");
        assertThat(cobro.urlPago()).doesNotContain(INTEGRIDAD).doesNotContain(SECRETO);
    }

    @Test
    void sinDireccionPublicaNoSeEnviaRegreso() {
        var cobro = con("pub_test_abc", INTEGRIDAD, SECRETO, "").iniciar("SL-abc", 1000);
        assertThat(parametros(cobro.urlPago())).doesNotContainKey("redirect-url");
    }

    @Test
    void sinLlavesNoHayPagoYSeDiceClaramente() {
        for (var p : new PasarelaWompi[] {con("", INTEGRIDAD, SECRETO, ""), con("pub", "", SECRETO, ""),
                con("  ", "  ", "  ", "")}) {
            assertThat(p.disponible()).isFalse();
            assertThatThrownBy(() -> p.iniciar("SL-abc", 1000)).isInstanceOfSatisfying(ErrorNegocio.class,
                    e -> assertThat(e.codigo()).isEqualTo(Codigo.PAYMENT_UNAVAILABLE));
        }
        assertThat(normal().disponible()).isTrue();
    }

    @Test
    void eventoFirmadoSeInterpreta() {
        var e = normal().verificar(evento("SL-abc", "1234-5678-91", "APPROVED", 5_000_000), null);
        assertThat(e.tipo()).isEqualTo("transaction.updated");
        assertThat(e.referencia()).isEqualTo("SL-abc");
        assertThat(e.transaccionId()).isEqualTo("1234-5678-91");
        assertThat(e.estado()).isEqualTo("APPROVED");
        assertThat(e.montoCentavos()).isEqualTo(5_000_000);
        assertThat(e.moneda()).isEqualTo("COP");
    }

    @Test
    void laCabeceraSolaAlcanzaYLasMayusculasNoImportan() {
        String sum = checksum("T1", "APPROVED", 100, TIMESTAMP, SECRETO).toUpperCase();
        String cuerpo = evento("SL-abc", "T1", "APPROVED", 100).replaceAll("\"checksum\":\"[0-9a-f]+\"", "\"checksum\":\"\"");
        assertThat(normal().verificar(cuerpo, sum).estado()).isEqualTo("APPROVED");
    }

    @Test
    void unMontoAlteradoInvalidaLaFirma() {
        String firmaOriginal = checksum("T1", "APPROVED", 100, TIMESTAMP, SECRETO);
        rechaza(() -> normal().verificar(evento("SL-abc", "T1", "APPROVED", 9_999_900, "COP", firmaOriginal), null));
    }

    @Test
    void unEstadoAlteradoInvalidaLaFirma() {
        String firmaDeUnRechazo = checksum("T1", "DECLINED", 100, TIMESTAMP, SECRETO);
        rechaza(() -> normal().verificar(evento("SL-abc", "T1", "APPROVED", 100, "COP", firmaDeUnRechazo), null));
    }

    @Test
    void unTimestampAlteradoInvalidaLaFirma() {
        String cuerpo = evento("SL-abc", "T1", "APPROVED", 100).replace("\"timestamp\":" + TIMESTAMP, "\"timestamp\":1");
        rechaza(() -> normal().verificar(cuerpo, null));
    }

    @Test
    void unSecretoDistintoInvalidaLaFirma() {
        rechaza(() -> con("pub", INTEGRIDAD, "otro-secreto", "").verificar(evento("SL-abc", "T1", "APPROVED", 100), null));
    }

    @Test
    void unaCabeceraDistintaDeLaDelCuerpoEsManipulacion() {
        rechaza(() -> normal().verificar(evento("SL-abc", "T1", "APPROVED", 100), "0".repeat(64)));
    }

    @Test
    void sinChecksumNoHayEvento() {
        String cuerpo = evento("SL-abc", "T1", "APPROVED", 100).replaceAll("\"checksum\":\"[0-9a-f]+\"", "\"checksum\":\"\"");
        rechaza(() -> normal().verificar(cuerpo, null));
        rechaza(() -> normal().verificar(cuerpo, "  "));
    }

    @Test
    void sinSecretoDeEventosSeRechazaTodo() {
        rechaza(() -> con("pub", INTEGRIDAD, "", "").verificar(evento("SL-abc", "T1", "APPROVED", 100), null));
    }

    @Test
    void cuerposMalFormadosSeRechazanSinExplotar() {
        for (String cuerpo : new String[] {"", "no es json", "[]", "{}", "{\"signature\":{},\"data\":{}}",
                "{\"signature\":{\"properties\":[],\"checksum\":\"x\"},\"data\":{},\"timestamp\":1}",
                "{\"signature\":{\"properties\":[\"transaction.id\"],\"checksum\":\"x\"},\"data\":{\"transaction\":{}},\"timestamp\":1}",
                "{\"signature\":{\"properties\":[\"transaction\"],\"checksum\":\"x\"},\"data\":{\"transaction\":{\"id\":1}},\"timestamp\":1}",
                "{\"signature\":{\"properties\":[7],\"checksum\":\"x\"},\"data\":{},\"timestamp\":1}",
                "{\"signature\":{\"properties\":[\"transaction.id\"],\"checksum\":\"x\"},\"data\":{\"transaction\":{\"id\":1}}}"}) {
            rechaza(() -> normal().verificar(cuerpo, null));
        }
    }
}
