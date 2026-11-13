package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.PasarelaPago;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador de Wompi (Web Checkout por redirección). Los datos de pago nunca pasan por este servidor: el técnico
 * paga en Wompi y Wompi avisa con un evento firmado.
 * <ul>
 *   <li>Firma de integridad del cobro: SHA-256 de referencia + monto en centavos + moneda + secreto de integridad.</li>
 *   <li>Firma del evento: SHA-256 de los valores de {@code signature.properties} (en ese orden, tomados de
 *       {@code data}) + {@code timestamp} + secreto de eventos, comparada con {@code signature.checksum} y con la
 *       cabecera {@code X-Event-Checksum}.</li>
 * </ul>
 */
@Component
public class PasarelaWompi implements PasarelaPago {

    static final String MONEDA = "COP";

    private final String llavePublica;
    private final String secretoIntegridad;
    private final String secretoEventos;
    private final String urlCheckout;
    private final String urlPublica;

    public PasarelaWompi(@Value("${servilink.wompi-llave-publica:}") String llavePublica,
                         @Value("${servilink.wompi-secreto-integridad:}") String secretoIntegridad,
                         @Value("${servilink.wompi-secreto-eventos:}") String secretoEventos,
                         @Value("${servilink.wompi-checkout-url:https://checkout.wompi.co/p/}") String urlCheckout,
                         @Value("${servilink.url-publica:}") String urlPublica) {
        this.llavePublica = limpiar(llavePublica);
        this.secretoIntegridad = limpiar(secretoIntegridad);
        this.secretoEventos = limpiar(secretoEventos);
        this.urlCheckout = limpiar(urlCheckout);
        this.urlPublica = limpiar(urlPublica).replaceAll("/+$", "");
    }

    @Override
    public boolean disponible() {
        return !llavePublica.isEmpty() && !secretoIntegridad.isEmpty() && !urlCheckout.isEmpty();
    }

    @Override
    public Cobro iniciar(String referencia, long valorCop) {
        if (!disponible()) {
            throw new ErrorNegocio(Codigo.PAYMENT_UNAVAILABLE, "Los pagos con Wompi no están disponibles por ahora");
        }
        long centavos = Math.multiplyExact(valorCop, 100L);
        var url = new StringBuilder(urlCheckout)
                .append(urlCheckout.contains("?") ? '&' : '?')
                .append("public-key=").append(codificar(llavePublica))
                .append("&currency=").append(MONEDA)
                .append("&amount-in-cents=").append(centavos)
                .append("&reference=").append(codificar(referencia))
                .append("&signature:integrity=").append(firmaIntegridad(referencia, centavos));
        if (!urlPublica.isEmpty()) {
            url.append("&redirect-url=").append(codificar(urlPublica + "/?recarga=" + referencia));
        }
        return new Cobro(url.toString(), referencia, centavos, MONEDA);
    }

    String firmaIntegridad(String referencia, long centavos) {
        return sha256(referencia + centavos + MONEDA + secretoIntegridad);
    }

    @Override
    public EventoPago verificar(String cuerpo, String checksumCabecera) {
        if (secretoEventos.isEmpty()) {
            throw invalida("La verificación de eventos no está configurada");
        }
        Map<String, Object> evento;
        try {
            evento = JsonParserFactory.getJsonParser().parseMap(cuerpo);
        } catch (RuntimeException e) {
            throw invalida("El evento no es un JSON válido");
        }
        Map<?, ?> firma = como(evento.get("signature"), Map.class);
        Map<?, ?> datos = como(evento.get("data"), Map.class);
        List<?> propiedades = como(firma.get("properties"), List.class);
        Object marca = evento.get("timestamp");
        if (propiedades.isEmpty() || marca == null) {
            throw invalida("El evento no trae los campos de la firma");
        }
        var cadena = new StringBuilder();
        for (Object p : propiedades) {
            Object valor = buscar(datos, como(p, String.class));
            if (valor == null) {
                throw invalida("El evento no trae el campo firmado " + p);
            }
            cadena.append(valor);
        }
        String esperado = sha256(cadena.append(marca).append(secretoEventos).toString());

        List<String> recibidos = new ArrayList<>();
        if (firma.get("checksum") instanceof String s && !s.isBlank()) {
            recibidos.add(s);
        }
        if (checksumCabecera != null && !checksumCabecera.isBlank()) {
            recibidos.add(checksumCabecera);
        }
        // Todo checksum que llegue debe coincidir: una cabecera distinta de la del cuerpo es señal de manipulación.
        if (recibidos.isEmpty() || !recibidos.stream().allMatch(r -> iguales(esperado, r.trim()))) {
            throw invalida("La firma del evento no coincide");
        }

        Map<?, ?> transaccion = datos.get("transaction") instanceof Map<?, ?> t ? t : Map.of();
        return new EventoPago(texto(evento.get("event")), texto(transaccion.get("reference")),
                texto(transaccion.get("id")), texto(transaccion.get("status")), numero(transaccion.get("amount_in_cents")),
                texto(transaccion.get("currency")));
    }

    private static Object buscar(Map<?, ?> datos, String ruta) {
        Object actual = datos;
        for (String clave : ruta.split("\\.")) {
            if (!(actual instanceof Map<?, ?> m)) {
                return null;
            }
            actual = m.get(clave);
        }
        return actual instanceof Map || actual instanceof List ? null : actual;
    }

    private static <T> T como(Object valor, Class<T> tipo) {
        if (!tipo.isInstance(valor)) {
            throw invalida("El evento no tiene la forma esperada");
        }
        return tipo.cast(valor);
    }

    private static boolean iguales(String esperado, String recibido) {
        return MessageDigest.isEqual(esperado.toLowerCase().getBytes(StandardCharsets.UTF_8),
                recibido.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString();
    }

    private static long numero(Object o) {
        return o instanceof Number n ? n.longValue() : -1;
    }

    private static String sha256(String texto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String codificar(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String limpiar(String s) {
        return s == null ? "" : s.trim();
    }

    private static ErrorNegocio invalida(String mensaje) {
        return new ErrorNegocio(Codigo.INVALID_SIGNATURE, mensaje);
    }
}
