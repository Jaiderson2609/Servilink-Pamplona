package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.aplicacion.AlmacenImagenes;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Adaptador de Cloudinary para subida directa firmada. El servidor solo firma y verifica: nunca llama a Cloudinary.
 * <ul>
 *   <li>Firma de la subida: SHA-256 de los parámetros firmados ordenados por nombre ({@code k=v&k=v}) + secreto de la API.
 *       Cloudinary acepta SHA-1 y SHA-256.</li>
 *   <li>Firma de la respuesta: hash de {@code public_id=…&version=…} + secreto. Según la configuración de la cuenta
 *       Cloudinary firma con SHA-1 o con SHA-256, así que se aceptan los dos (se distinguen por su longitud).</li>
 * </ul>
 */
@Component
public class AlmacenCloudinary implements AlmacenImagenes {

    /** Se firma junto con la subida: Cloudinary rechaza cualquier otro formato. */
    static final String FORMATOS = "jpg,png,webp";

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String urlSubida;
    private final String urlEntrega;
    private final Clock reloj;

    public AlmacenCloudinary(@Value("${servilink.cloudinary-cloud-name:}") String cloudName,
                             @Value("${servilink.cloudinary-api-key:}") String apiKey,
                             @Value("${servilink.cloudinary-api-secret:}") String apiSecret,
                             @Value("${servilink.cloudinary-upload-url:https://api.cloudinary.com/v1_1}") String urlSubida,
                             @Value("${servilink.cloudinary-delivery-url:https://res.cloudinary.com}") String urlEntrega,
                             Clock reloj) {
        this.cloudName = limpiar(cloudName);
        this.apiKey = limpiar(apiKey);
        this.apiSecret = limpiar(apiSecret);
        this.urlSubida = limpiar(urlSubida).replaceAll("/+$", "");
        this.urlEntrega = limpiar(urlEntrega).replaceAll("/+$", "");
        this.reloj = reloj;
    }

    @Override
    public boolean disponible() {
        return !cloudName.isEmpty() && !apiKey.isEmpty() && !apiSecret.isEmpty();
    }

    @Override
    public Firma firmar(String publicId) {
        long marca = reloj.instant().getEpochSecond();
        // Los parámetros van ordenados alfabéticamente por nombre: allowed_formats, public_id, timestamp.
        String firmado = "allowed_formats=" + FORMATOS + "&public_id=" + publicId + "&timestamp=" + marca;
        return new Firma(cloudName, apiKey, marca, publicId, FORMATOS, hash("SHA-256", firmado + apiSecret),
                urlSubida + "/" + cloudName + "/image/upload");
    }

    @Override
    public boolean respuestaValida(String publicId, String version, String firma) {
        if (!disponible() || firma == null) {
            return false;
        }
        String base = "public_id=" + publicId + "&version=" + version + apiSecret;
        String recibida = firma.trim().toLowerCase();
        String esperada = switch (recibida.length()) {
            case 64 -> hash("SHA-256", base);
            case 40 -> hash("SHA-1", base); // NOSONAR: Cloudinary firma sus respuestas con SHA-1 en las cuentas que no cambiaron el algoritmo
            default -> null;
        };
        return esperada != null && MessageDigest.isEqual(esperada.getBytes(StandardCharsets.UTF_8),
                recibida.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String urlDe(String publicId, String version) {
        return urlEntrega + "/" + cloudName + "/image/upload/v" + version + "/" + publicId;
    }

    @Override
    public String origenSubida() {
        return origen(urlSubida);
    }

    @Override
    public String origenEntrega() {
        return origen(urlEntrega);
    }

    private static String origen(String url) {
        URI u = URI.create(url);
        return u.getScheme() + "://" + u.getAuthority();
    }

    private static String hash(String algoritmo, String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algoritmo).digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }
}
