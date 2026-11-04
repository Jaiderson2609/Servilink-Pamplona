package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.config.Configuracion;
import co.edu.unipamplona.servilink.dominio.*;
import co.edu.unipamplona.servilink.repositorio.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-024 y RF-025: el hogar elige a un técnico; el servicio pasa de E02 a E03 con la comisión retenida. */
@Service
public class AsignacionService {

    public record Resultado(Long solicitudId, long comisionCop, String nombreTecnico, String telefonoTecnico,
                            Long avisoId) {}

    private final SolicitudRepositorio solicitudes;
    private final PostulacionRepositorio postulaciones;
    private final BilleteraRepositorio billeteras;
    private final MovimientoRepositorio libro;
    private final NotificacionRepositorio avisos;
    private final UsuarioRepositorio usuarios;
    private final Configuracion config;
    private final Clock reloj;

    public AsignacionService(SolicitudRepositorio solicitudes, PostulacionRepositorio postulaciones,
                             BilleteraRepositorio billeteras, MovimientoRepositorio libro,
                             NotificacionRepositorio avisos, UsuarioRepositorio usuarios,
                             Configuracion config, Clock reloj) {
        this.solicitudes = solicitudes;
        this.postulaciones = postulaciones;
        this.billeteras = billeteras;
        this.libro = libro;
        this.avisos = avisos;
        this.usuarios = usuarios;
        this.config = config;
        this.reloj = reloj;
    }

    @Transactional
    public Resultado asignar(Long hogarId, Long solicitudId, Long postulacionId) {
        // Bloqueo pesimista: dos asignaciones simultáneas de la misma solicitud se ejecutan una tras otra.
        Solicitud sol = solicitudes.bloquear(solicitudId)
                .orElseThrow(() -> new ErrorNegocio(HttpStatus.NOT_FOUND, "No encontrado", "Solicitud inexistente"));
        if (!sol.getHogarId().equals(hogarId)) {
            throw new ErrorNegocio(HttpStatus.FORBIDDEN, "Sin permiso", "La solicitud es de otro hogar");
        }
        Postulacion pos = postulaciones.findById(postulacionId)
                .filter(p -> p.getSolicitudId().equals(sol.getId()))
                .orElseThrow(() -> new ErrorNegocio(HttpStatus.NOT_FOUND, "No encontrado", "Postulación inexistente"));
        Billetera billetera = billeteras.bloquear(pos.getTecnicoId())
                .orElseThrow(() -> new ErrorNegocio(HttpStatus.NOT_FOUND, "No encontrado", "El técnico no tiene billetera"));
        Usuario tecnico = usuarios.findById(pos.getTecnicoId()).orElseThrow();

        if ("E03".equals(sol.getEstado()) && pos.getTecnicoId().equals(sol.getTecnicoId())) {
            // Reintento idempotente: mismo técnico, misma solicitud. No se cobra ni se avisa dos veces.
            return new Resultado(sol.getId(), sol.getComisionCop(), tecnico.getNombre(), tecnico.getTelefono(), null);
        }
        if (!"E02".equals(sol.getEstado())) {
            throw new ErrorNegocio(HttpStatus.CONFLICT, "Estado invalido", "La solicitud está en " + sol.getEstado());
        }
        Instant ahora = reloj.instant();
        if (!ahora.isBefore(sol.getExpiraEn())) { // RN-02: el servidor decide con su propio reloj
            throw new ErrorNegocio(HttpStatus.CONFLICT, "Ventana expirada", "La ventana de selección terminó");
        }
        if (solicitudes.existsByTecnicoIdAndEstadoIn(pos.getTecnicoId(), List.of("E03", "E04"))) { // RN-07
            throw new ErrorNegocio(HttpStatus.CONFLICT, "Tecnico ocupado", "El técnico ya tiene un servicio activo");
        }
        long comision = comision(pos.getPrecio(), config.comision());
        if (billetera.getDisponible() < comision) {
            throw new ErrorNegocio(HttpStatus.UNPROCESSABLE_ENTITY, "Saldo insuficiente",
                    "No alcanza para retener la comisión");
        }
        billetera.retener(comision);
        libro.save(new Movimiento(billetera.getTecnicoId(), sol.getId(), "RETENCION", comision,
                billetera.getDisponible(), "ASIG-" + sol.getId(), ahora));

        sol.setEstado("E03");
        sol.setTecnicoId(pos.getTecnicoId());
        sol.setPrecioAcordado(pos.getPrecio());
        sol.setComisionPct(config.comision()); // RN-14: el porcentaje queda congelado para este servicio
        sol.setComisionCop((int) comision);
        pos.setEstado("ELEGIDA");
        for (Postulacion otra : postulaciones.findBySolicitudId(sol.getId())) { // RF-024: se descartan las demás
            if (!otra.getId().equals(pos.getId()) && "ACTIVA".equals(otra.getEstado())) {
                otra.setEstado("DESCARTADA");
            }
        }
        NotificacionPendiente aviso = avisos.save(new NotificacionPendiente("ASIG-" + sol.getId(),
                pos.getTecnicoId(), "Fuiste asignado a una solicitud", ahora));
        return new Resultado(sol.getId(), comision, tecnico.getNombre(), tecnico.getTelefono(), aviso.getId());
    }

    /** Comisión = precio × porcentaje, redondeada hacia arriba a partir de la mitad (ROUND_HALF_UP). */
    static long comision(int precio, BigDecimal porcentaje) {
        return BigDecimal.valueOf(precio).multiply(porcentaje).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
