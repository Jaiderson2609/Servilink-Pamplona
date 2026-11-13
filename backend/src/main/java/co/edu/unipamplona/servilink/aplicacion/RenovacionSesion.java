package co.edu.unipamplona.servilink.aplicacion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Tokens de renovación de sesión (DAD 11.1: «JWT de corta duración y renovación controlada»).
 * <ul>
 *   <li>Son opacos y aleatorios; en la base solo se guarda su SHA-256, nunca el valor que tiene el navegador.</li>
 *   <li>Son de un solo uso: cada renovación gasta el token y entrega otro de la misma familia (rotación).</li>
 *   <li>Si llega un token ya gastado se revoca toda la familia (posible robo), salvo en una ventana de gracia corta
 *       que cubre dos pestañas renovando a la vez.</li>
 *   <li>Vencen a los {@code renovacion-vigencia-seg} y la familia no puede superar {@code renovacion-maxima-seg}.</li>
 * </ul>
 */
@Component
public class RenovacionSesion {

    /** Un token recién gastado que se repite dentro de este lapso no es robo: es una segunda pestaña. */
    static final Duration GRACIA = Duration.ofSeconds(10);

    /** Resultado de validar un token: el usuario y, si hubo rotación, el token nuevo. */
    public record Renovada(Long usuarioId, String tokenNuevo, long vigenciaSeg) {}

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final Clock reloj;
    private final Duration vigencia;
    private final Duration maxima;

    public RenovacionSesion(JdbcTemplate jdbc, Clock reloj,
                            @Value("${servilink.renovacion-vigencia-seg:604800}") long vigenciaSeg,
                            @Value("${servilink.renovacion-maxima-seg:2592000}") long maximaSeg) {
        this.jdbc = jdbc;
        this.reloj = reloj;
        this.vigencia = Duration.ofSeconds(vigenciaSeg);
        this.maxima = Duration.ofSeconds(maximaSeg);
    }

    public long vigenciaSeg() {
        return vigencia.toSeconds();
    }

    /** Abre una familia nueva para un inicio de sesión y devuelve el token que se entrega al navegador. */
    public String abrir(Long usuarioId) {
        Instant ahora = reloj.instant();
        // Limpieza de pasada: lo vencido de este usuario ya no sirve para nada.
        jdbc.update("DELETE FROM sesiones_renovacion WHERE usuario_id = ? AND expira_en < ?", usuarioId, Timestamp.from(ahora));
        return insertar(usuarioId, UUID.randomUUID().toString(), ahora, ahora);
    }

    /**
     * Gasta el token y entrega otro. Lanza UNAUTHENTICATED si no existe, venció o fue reutilizado.
     * {@code tokenNuevo} es nulo solo dentro de la ventana de gracia: el navegador ya recibió el nuevo de la otra pestaña.
     */
    public Renovada renovar(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            throw sinSesion();
        }
        Instant ahora = reloj.instant();
        List<Fila> filas = jdbc.query("SELECT id, usuario_id, familia, familia_inicio, expira_en, usada_en "
                        + "FROM sesiones_renovacion WHERE token_hash = ? FOR UPDATE",
                (rs, n) -> new Fila(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("familia"),
                        rs.getTimestamp("familia_inicio").toInstant(), rs.getTimestamp("expira_en").toInstant(),
                        rs.getTimestamp("usada_en") == null ? null : rs.getTimestamp("usada_en").toInstant()),
                hash(token));
        if (filas.isEmpty()) {
            throw sinSesion();
        }
        Fila f = filas.get(0);
        if (!f.expira().isAfter(ahora)) {
            revocarFamilia(f.familia());
            throw sinSesion();
        }
        if (f.usada() != null) {
            if (Duration.between(f.usada(), ahora).compareTo(GRACIA) <= 0) {
                return new Renovada(f.usuarioId(), null, vigencia.toSeconds());
            }
            revocarFamilia(f.familia()); // reutilización fuera de la gracia: se cierra toda la familia
            throw sinSesion();
        }
        jdbc.update("UPDATE sesiones_renovacion SET usada_en = ? WHERE id = ?", Timestamp.from(ahora), f.id());
        return new Renovada(f.usuarioId(), insertar(f.usuarioId(), f.familia(), f.inicio(), ahora), vigencia.toSeconds());
    }

    /** Cierra la sesión: revoca la familia del token, si existe. Nunca falla. */
    public void cerrar(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return;
        }
        List<String> familias = jdbc.queryForList("SELECT familia FROM sesiones_renovacion WHERE token_hash = ?",
                String.class, hash(token));
        familias.forEach(this::revocarFamilia);
    }

    /** Revoca todas las sesiones de un usuario (p. ej. al suspenderlo). */
    public void revocarDe(Long usuarioId) {
        jdbc.update("DELETE FROM sesiones_renovacion WHERE usuario_id = ?", usuarioId);
    }

    public void revocarFamilia(String familia) {
        jdbc.update("DELETE FROM sesiones_renovacion WHERE familia = ?", familia);
    }

    private String insertar(Long usuarioId, String familia, Instant inicio, Instant ahora) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = ahora.plus(vigencia);
        Instant tope = inicio.plus(maxima);
        if (expira.isAfter(tope)) {
            expira = tope;
        }
        jdbc.update("INSERT INTO sesiones_renovacion (usuario_id, familia, token_hash, familia_inicio, creada_en, expira_en) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", usuarioId, familia, hash(token), Timestamp.from(inicio),
                Timestamp.from(ahora), Timestamp.from(expira));
        return token;
    }

    private static ErrorNegocio sinSesion() {
        return new ErrorNegocio(Codigo.UNAUTHENTICATED, "Tu sesión venció, ingresa de nuevo");
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private record Fila(long id, long usuarioId, String familia, Instant inicio, Instant expira, Instant usada) {}
}
