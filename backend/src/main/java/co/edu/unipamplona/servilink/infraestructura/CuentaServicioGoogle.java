package co.edu.unipamplona.servilink.infraestructura;

import com.nimbusds.jose.util.JSONObjectUtils;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Cuenta de servicio de Google (Firebase): firma una aserción JWT con su clave privada y la cambia por un token de
 * acceso de corta vida para llamar a la API de FCM. Las credenciales llegan solo por la variable de entorno
 * FIREBASE_CREDENCIALES_JSON; si no está definida (o no es válida) el envío de avisos push queda apagado.
 */
@Component
public class CuentaServicioGoogle {

    private static final Logger LOG = LoggerFactory.getLogger(CuentaServicioGoogle.class);
    static final String ALCANCE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String TOKEN_URI_POR_DEFECTO = "https://oauth2.googleapis.com/token";
    private static final Duration MARGEN = Duration.ofMinutes(5);

    private final String proyectoId;
    private final String correo;
    private final String tokenUri;
    private final PrivateKey clave;
    private final Clock reloj;
    private final RestClient cliente;

    private String tokenActual;
    private Instant venceEn = Instant.EPOCH;

    @Autowired
    public CuentaServicioGoogle(@Value("${servilink.firebase-credenciales:}") String json) {
        this(json, Clock.systemUTC());
    }

    CuentaServicioGoogle(String json, Clock reloj) {
        String p = null, c = null, t = TOKEN_URI_POR_DEFECTO;
        PrivateKey k = null;
        if (json != null && !json.isBlank()) {
            try {
                Map<String, Object> m = JSONObjectUtils.parse(json.trim());
                p = (String) m.get("project_id");
                c = (String) m.get("client_email");
                if (m.get("token_uri") instanceof String u && !u.isBlank()) {
                    t = u;
                }
                k = leerClave((String) m.get("private_key"));
                if (p == null || p.isBlank() || c == null || c.isBlank()) {
                    k = null;
                }
            } catch (Exception e) {
                LOG.warn("FIREBASE_CREDENCIALES_JSON no es válida: los avisos push quedan apagados ({})", e.getClass().getSimpleName());
                k = null;
            }
        }
        this.proyectoId = k == null ? null : p;
        this.correo = c;
        this.tokenUri = t;
        this.clave = k;
        this.reloj = reloj;
        var fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    private static PrivateKey leerClave(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    /** Hay credenciales válidas: se puede firmar y pedir tokens de acceso. */
    public boolean habilitada() {
        return clave != null;
    }

    public String proyectoId() {
        return proyectoId;
    }

    /** Token de acceso vigente; se renueva solo cuando faltan menos de 5 minutos. Lanza si Google no responde. */
    public synchronized String tokenAcceso() {
        Instant ahora = reloj.instant();
        if (tokenActual == null || !ahora.plus(MARGEN).isBefore(venceEn)) {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            form.add("assertion", asercion(ahora));
            Map<?, ?> r = cliente.post().uri(tokenUri).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(Map.class);
            if (r == null || !(r.get("access_token") instanceof String t)) {
                throw new IllegalStateException("Google no devolvió un token de acceso");
            }
            long segundos = r.get("expires_in") instanceof Number n ? n.longValue() : 3000;
            tokenActual = t;
            venceEn = ahora.plusSeconds(segundos);
        }
        return tokenActual;
    }

    private String asercion(Instant ahora) {
        try {
            var b64 = Base64.getUrlEncoder().withoutPadding();
            String cabecera = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String cuerpo = b64.encodeToString(("{\"iss\":\"" + correo + "\",\"scope\":\"" + ALCANCE + "\",\"aud\":\""
                    + tokenUri + "\",\"iat\":" + ahora.getEpochSecond() + ",\"exp\":" + (ahora.getEpochSecond() + 3600) + "}")
                    .getBytes(StandardCharsets.UTF_8));
            var firma = Signature.getInstance("SHA256withRSA");
            firma.initSign(clave);
            firma.update((cabecera + "." + cuerpo).getBytes(StandardCharsets.US_ASCII));
            return cabecera + "." + cuerpo + "." + b64.encodeToString(firma.sign());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la aserción de la cuenta de servicio", e);
        }
    }
}
