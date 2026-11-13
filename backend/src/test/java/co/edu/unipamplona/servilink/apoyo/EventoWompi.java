package co.edu.unipamplona.servilink.apoyo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Construye eventos de Wompi con la firma documentada: SHA-256 de id + estado + monto + timestamp + secreto. */
public final class EventoWompi {

    public static final String SECRETO = "test_events_ficticio";
    public static final long TIMESTAMP = 1530291411L;

    private EventoWompi() {}

    public static String checksum(String id, String estado, long centavos, long timestamp, String secreto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((id + estado + centavos + timestamp + secreto).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Evento con la firma correcta para el secreto de pruebas. */
    public static String evento(String referencia, String id, String estado, long centavos) {
        return evento(referencia, id, estado, centavos, "COP", checksum(id, estado, centavos, TIMESTAMP, SECRETO));
    }

    /** Evento con el checksum que se indique (para probar firmas alteradas). */
    public static String evento(String referencia, String id, String estado, long centavos, String moneda, String checksum) {
        return "{\"event\":\"transaction.updated\",\"data\":{\"transaction\":{\"id\":\"" + id + "\",\"status\":\"" + estado
                + "\",\"amount_in_cents\":" + centavos + ",\"reference\":\"" + referencia + "\",\"currency\":\"" + moneda
                + "\"}},\"environment\":\"test\",\"signature\":{\"properties\":[\"transaction.id\",\"transaction.status\","
                + "\"transaction.amount_in_cents\"],\"checksum\":\"" + checksum + "\"},\"timestamp\":" + TIMESTAMP
                + ",\"sent_at\":\"2026-11-13T12:00:00.000Z\"}";
    }
}
