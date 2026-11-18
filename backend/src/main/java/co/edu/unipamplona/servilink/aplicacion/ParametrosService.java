package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.config.Configuracion;
import co.edu.unipamplona.servilink.dominio.AuditoriaAdmin;
import co.edu.unipamplona.servilink.dominio.Parametro;
import co.edu.unipamplona.servilink.repositorio.AuditoriaRepositorio;
import co.edu.unipamplona.servilink.repositorio.ParametroRepositorio;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parámetros que el administrador ajusta desde su panel (ERS 4). Cada cambio queda auditado y aplica solo a lo que
 * nace después: la comisión se copia al asignar y las ventanas se fijan al crear o al recibir la primera postulación.
 * Sin fila en la base rige el valor por defecto de la configuración.
 */
@Service
public class ParametrosService {

    public static final String APPLY_WINDOW_S = "APPLY_WINDOW_S";
    public static final String SELECT_WINDOW_S = "SELECT_WINDOW_S";
    public static final String MAX_TOTAL_S = "MAX_TOTAL_S";
    public static final String COMMISSION_PCT = "COMMISSION_PCT";
    public static final String MIN_BALANCE = "MIN_BALANCE";
    public static final String SEARCH_RADIUS_M = "SEARCH_RADIUS_M";

    public record Definicion(String clave, String descripcion, String unidad, BigDecimal minimo, BigDecimal maximo) {}

    public record Vista(String clave, String descripcion, String unidad, BigDecimal valor, BigDecimal defecto,
                        BigDecimal minimo, BigDecimal maximo, Instant actualizadoEn) {}

    private static BigDecimal n(long v) { return BigDecimal.valueOf(v); }

    static final List<Definicion> DEFINICIONES = List.of(
            new Definicion(SEARCH_RADIUS_M, "Radio de búsqueda por defecto de la zona de trabajo", "m", n(1000), n(50_000)),
            new Definicion(APPLY_WINDOW_S, "Ventana de postulación (desde la creación de la solicitud)", "s", n(60), n(3600)),
            new Definicion(SELECT_WINDOW_S, "Ventana de elección del hogar (desde la primera postulación)", "s", n(60), n(1800)),
            new Definicion(MAX_TOTAL_S, "Tiempo máximo total antes de asignar", "s", n(120), n(7200)),
            new Definicion(COMMISSION_PCT, "Comisión sobre el precio acordado", "%", n(1), n(50)),
            new Definicion(MIN_BALANCE, "Saldo mínimo para postularse", "COP", n(0), n(1_000_000)));

    private final ParametroRepositorio parametros;
    private final AuditoriaRepositorio auditoria;
    private final Configuracion config;
    private final Clock reloj;

    public ParametrosService(ParametroRepositorio parametros, AuditoriaRepositorio auditoria, Configuracion config, Clock reloj) {
        this.parametros = parametros;
        this.auditoria = auditoria;
        this.config = config;
        this.reloj = reloj;
    }

    private Map<String, BigDecimal> defectos() {
        return Map.of(
                SEARCH_RADIUS_M, n(5000),
                APPLY_WINDOW_S, n(config.ventanaPostulacionMin() * 60L),
                SELECT_WINDOW_S, n(config.ventanaEleccionMin() * 60L),
                MAX_TOTAL_S, n((config.ventanaPostulacionMin() + config.ventanaEleccionMin()) * 60L),
                COMMISSION_PCT, config.comision().movePointRight(2).stripTrailingZeros(),
                MIN_BALANCE, n(config.saldoMinimo()));
    }

    @Transactional(readOnly = true)
    public BigDecimal valor(String clave) {
        return parametros.findById(clave).map(Parametro::getValor).orElseGet(() -> defectos().get(clave));
    }

    @Transactional(readOnly = true)
    public Duration ventanaPostulacion() { return Duration.ofSeconds(valor(APPLY_WINDOW_S).longValue()); }

    @Transactional(readOnly = true)
    public Duration ventanaEleccion() { return Duration.ofSeconds(valor(SELECT_WINDOW_S).longValue()); }

    @Transactional(readOnly = true)
    public Duration tiempoMaximoTotal() { return Duration.ofSeconds(valor(MAX_TOTAL_S).longValue()); }

    /** Comisión como fracción (10 % es 0,10), con la que se calcula y se congela en cada servicio. */
    @Transactional(readOnly = true)
    public BigDecimal comision() { return valor(COMMISSION_PCT).movePointLeft(2); }

    @Transactional(readOnly = true)
    public long saldoMinimo() { return valor(MIN_BALANCE).longValue(); }

    @Transactional(readOnly = true)
    public int radioPorDefectoKm() { return Math.max(1, Math.round(valor(SEARCH_RADIUS_M).floatValue() / 1000f)); }

    @Transactional(readOnly = true)
    public List<Vista> listar() {
        var defectos = defectos();
        return DEFINICIONES.stream().map(d -> {
            var fila = parametros.findById(d.clave());
            return new Vista(d.clave(), d.descripcion(), d.unidad(), fila.map(Parametro::getValor).orElse(defectos.get(d.clave())),
                    defectos.get(d.clave()), d.minimo(), d.maximo(), fila.map(Parametro::getActualizadoEn).orElse(null));
        }).toList();
    }

    @Transactional
    public Vista cambiar(Long adminId, String clave, BigDecimal nuevo) {
        Definicion d = DEFINICIONES.stream().filter(x -> x.clave().equals(clave)).findFirst()
                .orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Parámetro inexistente"));
        if (nuevo.compareTo(d.minimo()) < 0 || nuevo.compareTo(d.maximo()) > 0) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "El valor debe estar entre " + d.minimo().toPlainString() + " y "
                    + d.maximo().toPlainString() + " " + d.unidad());
        }
        if (!COMMISSION_PCT.equals(clave) && nuevo.stripTrailingZeros().scale() > 0) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "El valor debe ser un número entero");
        }
        BigDecimal antes = valor(clave);
        // Coherencia: la ventana de postulación debe caber con holgura dentro del tiempo máximo total.
        BigDecimal aplicar = APPLY_WINDOW_S.equals(clave) ? nuevo : valor(APPLY_WINDOW_S);
        BigDecimal total = MAX_TOTAL_S.equals(clave) ? nuevo : valor(MAX_TOTAL_S);
        if ((APPLY_WINDOW_S.equals(clave) || MAX_TOTAL_S.equals(clave)) && aplicar.add(n(60)).compareTo(total) > 0) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR,
                    "El tiempo máximo total debe superar la ventana de postulación en al menos 60 s");
        }
        Instant ahora = reloj.instant();
        Parametro fila = parametros.findById(clave).orElse(null);
        if (fila == null) {
            parametros.save(new Parametro(clave, nuevo, ahora, adminId));
        } else {
            fila.cambiar(nuevo, ahora, adminId);
        }
        auditoria.save(new AuditoriaAdmin(adminId, "CAMBIAR_PARAMETRO", "PARAMETRO", null,
                clave + ": " + antes.stripTrailingZeros().toPlainString() + " → " + nuevo.stripTrailingZeros().toPlainString(), ahora));
        return listar().stream().filter(v -> v.clave().equals(clave)).findFirst().orElseThrow();
    }
}
