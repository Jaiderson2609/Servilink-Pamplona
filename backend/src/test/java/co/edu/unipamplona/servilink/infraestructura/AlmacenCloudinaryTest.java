package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AlmacenCloudinaryTest {

    static final Clock RELOJ = Clock.fixed(Instant.ofEpochSecond(1_315_060_510L), ZoneOffset.UTC);

    static AlmacenCloudinary con(String nube, String clave, String secreto) {
        return new AlmacenCloudinary(nube, clave, secreto, "https://api.cloudinary.com/v1_1/", "https://res.cloudinary.com/", RELOJ);
    }

    static String hash(String alg, String t) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance(alg).digest(t.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void estaDisponibleSoloConLasTresCredenciales() {
        assertThat(con("n", "k", "s").disponible()).isTrue();
        assertThat(con("", "k", "s").disponible()).isFalse();
        assertThat(con("n", " ", "s").disponible()).isFalse();
        assertThat(con("n", "k", null).disponible()).isFalse();
    }

    @Test
    void firmaConElRelojInyectadoYLosParametrosOrdenados() throws Exception {
        var f = con(" nube ", "clave", "abcd").firmar("servilink/x/1");
        assertThat(f.timestamp()).isEqualTo(1_315_060_510L);
        assertThat(f.cloudName()).isEqualTo("nube");
        assertThat(f.uploadUrl()).isEqualTo("https://api.cloudinary.com/v1_1/nube/image/upload");
        assertThat(f.signature()).isEqualTo(hash("SHA-256",
                "allowed_formats=jpg,png,webp&public_id=servilink/x/1&timestamp=1315060510abcd"));
    }

    @Test
    void verificaLaRespuestaConSha1ConSha256YRechazaElResto() throws Exception {
        var a = con("n", "k", "abcd");
        String base = "public_id=p/1&version=77abcd";
        assertThat(a.respuestaValida("p/1", "77", hash("SHA-1", base))).isTrue();
        assertThat(a.respuestaValida("p/1", "77", hash("SHA-256", base).toUpperCase())).isTrue();
        assertThat(a.respuestaValida("p/1", "78", hash("SHA-1", base))).isFalse();
        assertThat(a.respuestaValida("p/2", "77", hash("SHA-1", base))).isFalse();
        assertThat(a.respuestaValida("p/1", "77", "corta")).isFalse();
        assertThat(a.respuestaValida("p/1", "77", null)).isFalse();
        assertThat(con("", "", "").respuestaValida("p/1", "77", hash("SHA-1", "public_id=p/1&version=77"))).isFalse();
    }

    @Test
    void construyeLasDireccionesYLosOrigenes() {
        var a = con("nube", "k", "s");
        assertThat(a.urlDe("a/b", "5")).isEqualTo("https://res.cloudinary.com/nube/image/upload/v5/a/b");
        assertThat(a.origenSubida()).isEqualTo("https://api.cloudinary.com");
        assertThat(a.origenEntrega()).isEqualTo("https://res.cloudinary.com");
    }
}
