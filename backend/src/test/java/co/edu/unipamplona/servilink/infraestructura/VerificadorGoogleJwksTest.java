package co.edu.unipamplona.servilink.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** La verificación real del ID token, con una llave RSA propia en lugar de las de Google. */
class VerificadorGoogleJwksTest {

    static final String CLIENTE = "cliente.apps.googleusercontent.com";
    static KeyPair llaves;
    static KeyPair otras;

    @BeforeAll
    static void generar() throws Exception {
        var g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        llaves = g.generateKeyPair();
        otras = g.generateKeyPair();
    }

    VerificadorGoogleJwks verificador() {
        return new VerificadorGoogleJwks(CLIENTE, NimbusJwtDecoder.withPublicKey((RSAPublicKey) llaves.getPublic()).build());
    }

    JWTClaimsSet.Builder validos() {
        return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").audience(CLIENTE).subject("123")
                .claim("email", "ana@gmail.com").claim("email_verified", true).claim("name", "Ana Gómez")
                .issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(600)));
    }

    String firmar(JWTClaimsSet claims, KeyPair con) throws Exception {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
        jwt.sign(new RSASSASigner(con.getPrivate()));
        return jwt.serialize();
    }

    void rechaza(String credencial) {
        assertThatThrownBy(() -> verificador().verificar(credencial)).isInstanceOfSatisfying(ErrorNegocio.class,
                e -> assertThat(e.codigo()).isEqualTo(Codigo.UNAUTHENTICATED));
    }

    @Test
    void aceptaUnTokenValidoYDevuelveCorreoYNombre() throws Exception {
        var i = verificador().verificar(firmar(validos().build(), llaves));
        assertThat(i.correo()).isEqualTo("ana@gmail.com");
        assertThat(i.nombre()).isEqualTo("Ana Gómez");
    }

    @Test
    void aceptaElEmisorSinProtocoloQueGoogleTambienUsa() throws Exception {
        assertThat(verificador().verificar(firmar(validos().issuer("accounts.google.com").build(), llaves)).correo()).isEqualTo("ana@gmail.com");
    }

    @Test
    void sinNombreUsaLaParteLocalDelCorreo() throws Exception {
        var claims = new JWTClaimsSet.Builder(validos().build()).claim("name", null).build();
        assertThat(verificador().verificar(firmar(claims, llaves)).nombre()).isEqualTo("ana");
    }

    @Test
    void rechazaFirmaAjena() throws Exception { rechaza(firmar(validos().build(), otras)); }

    @Test
    void rechazaOtraAudiencia() throws Exception { rechaza(firmar(validos().audience("otra-app").build(), llaves)); }

    @Test
    void rechazaTokenSinAudiencia() throws Exception {
        rechaza(firmar(new JWTClaimsSet.Builder(validos().build()).audience((String) null).build(), llaves));
    }

    @Test
    void rechazaOtroEmisor() throws Exception { rechaza(firmar(validos().issuer("https://falso.example").build(), llaves)); }

    @Test
    void rechazaTokenVencido() throws Exception {
        rechaza(firmar(validos().expirationTime(Date.from(Instant.now().minusSeconds(3600))).build(), llaves));
    }

    @Test
    void rechazaCorreoSinVerificarOAusente() throws Exception {
        rechaza(firmar(validos().claim("email_verified", false).build(), llaves));
        rechaza(firmar(new JWTClaimsSet.Builder(validos().build()).claim("email", null).build(), llaves));
    }

    @Test
    void rechazaTextoQueNoEsUnToken() {
        rechaza("esto-no-es-un-jwt");
    }

    @Test
    void sinIdDeClienteNoEstaDisponible() {
        var v = new VerificadorGoogleJwks("");
        assertThat(v.disponible()).isFalse();
        assertThat(v.clienteId()).isEmpty();
        assertThatThrownBy(() -> v.verificar("x")).isInstanceOfSatisfying(ErrorNegocio.class,
                e -> assertThat(e.codigo()).isEqualTo(Codigo.GOOGLE_UNAVAILABLE));
        assertThat(new VerificadorGoogleJwks(null, null).clienteId()).isEmpty();
    }

    @Test
    void conIdDeClienteQuedaDisponible() {
        var v = new VerificadorGoogleJwks(" " + CLIENTE + " ");
        assertThat(v.disponible()).isTrue();
        assertThat(v.clienteId()).isEqualTo(CLIENTE);
    }
}
