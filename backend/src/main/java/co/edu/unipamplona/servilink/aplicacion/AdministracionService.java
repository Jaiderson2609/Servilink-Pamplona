package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.AuditoriaAdmin;
import co.edu.unipamplona.servilink.dominio.Movimiento;
import co.edu.unipamplona.servilink.dominio.Solicitud;
import co.edu.unipamplona.servilink.dominio.Usuario;
import co.edu.unipamplona.servilink.repositorio.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Funciones del administrador (RF-045 a RF-050). Cada acción que cambia algo queda en la auditoría. */
@Service
public class AdministracionService {

    public static final List<String> ROLES = List.of("HOGAR", "TECNICO", "ADMIN");
    public static final List<String> ESTADOS = List.of("E01", "E02", "E03", "E04", "E05", "E06", "E07");

    private final UsuarioRepositorio usuarios;
    private final SolicitudRepositorio solicitudes;
    private final MovimientoRepositorio libro;
    private final BilleteraRepositorio billeteras;
    private final AuditoriaRepositorio auditoria;
    private final EjecucionService ejecucion;
    private final Clock reloj;

    public AdministracionService(UsuarioRepositorio usuarios, SolicitudRepositorio solicitudes, MovimientoRepositorio libro,
                                 BilleteraRepositorio billeteras, AuditoriaRepositorio auditoria,
                                 EjecucionService ejecucion, Clock reloj) {
        this.usuarios = usuarios;
        this.solicitudes = solicitudes;
        this.libro = libro;
        this.billeteras = billeteras;
        this.auditoria = auditoria;
        this.ejecucion = ejecucion;
        this.reloj = reloj;
    }

    public record Pagina<T>(List<T> items, int pagina, int tamano, long total) {}

    public record UsuarioAdmin(Long id, String rol, String nombre, String correo, String telefono, boolean suspendido,
                               String motivoSuspension) {}

    public record SolicitudAdmin(Long id, String estado, Integer oficioId, String descripcion, Long hogarId, Long tecnicoId,
                                 Integer precioAcordadoCop, Integer comisionCop, Instant expiraEn, String canceladaPor,
                                 String motivoCancelacion) {}

    public record MovimientoAdmin(Long id, Long tecnicoId, Long solicitudId, String tipo, long valorCop,
                                  long saldoPosteriorCop, String referencia, Instant creadoEn) {}

    public record Auditoria(Long id, Long adminId, String accion, String objetivoTipo, Long objetivoId, String detalle,
                            Instant creadaEn) {}

    @Transactional(readOnly = true)
    public Map<String, Object> resumen() {
        var porEstado = new LinkedHashMap<String, Long>();
        ESTADOS.forEach(e -> porEstado.put(e, 0L));
        solicitudes.contarPorEstado().forEach(f -> porEstado.put((String) f[0], (Long) f[1]));
        var porRol = new LinkedHashMap<String, Long>();
        ROLES.forEach(r -> porRol.put(r, usuarios.countByRol(r)));
        var m = new LinkedHashMap<String, Object>();
        m.put("solicitudesPorEstado", porEstado);
        m.put("usuariosPorRol", porRol);
        m.put("cuentasSuspendidas", usuarios.countBySuspendidoTrue());
        m.put("comisionesCobradasCop", libro.comisionesCobradas());
        m.put("comisionesRetenidasCop", billeteras.totalRetenido());
        return m;
    }

    @Transactional(readOnly = true)
    public Pagina<UsuarioAdmin> usuarios(String rol, String q, int pagina, int tamano) {
        if (rol != null && !ROLES.contains(rol)) {
            throw invalida("rol debe ser HOGAR, TECNICO o ADMIN");
        }
        String patron = "%" + (q == null ? "" : q.strip().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "")) + "%";
        Page<Usuario> p = usuarios.buscar(rol == null ? ROLES : List.of(rol), patron, pedido(pagina, tamano, "id"));
        return paginar(p, this::vista);
    }

    @Transactional
    public UsuarioAdmin suspender(Long adminId, Long usuarioId, String motivo) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> noEncontrado("Usuario inexistente"));
        if ("ADMIN".equals(u.getRol())) {
            throw new ErrorNegocio(Codigo.OPERATION_NOT_ALLOWED, "No se puede suspender a un administrador");
        }
        if (!u.isSuspendido()) {
            u.suspender(motivo.trim(), reloj.instant());
            registrar(adminId, "SUSPENDER_USUARIO", "USUARIO", usuarioId, motivo.trim());
        }
        return vista(u);
    }

    @Transactional
    public UsuarioAdmin reactivar(Long adminId, Long usuarioId) {
        Usuario u = usuarios.findById(usuarioId).orElseThrow(() -> noEncontrado("Usuario inexistente"));
        if (u.isSuspendido()) {
            u.reactivar();
            registrar(adminId, "REACTIVAR_USUARIO", "USUARIO", usuarioId, null);
        }
        return vista(u);
    }

    @Transactional(readOnly = true)
    public Pagina<SolicitudAdmin> solicitudes(String estado, int pagina, int tamano) {
        if (estado != null && !ESTADOS.contains(estado)) {
            throw invalida("estado debe ser E01 a E07");
        }
        var pedido = pedido(pagina, tamano, "id");
        Page<Solicitud> p = estado == null ? solicitudes.findAll(pedido) : solicitudes.findByEstado(estado, pedido);
        return paginar(p, this::vista);
    }

    @Transactional
    public Efecto<SolicitudAdmin> cancelarSolicitud(Long adminId, Long solicitudId, String motivo) {
        Efecto<Solicitud> e = ejecucion.cancelarAdministrativa(solicitudId, motivo);
        registrar(adminId, "CANCELAR_SOLICITUD", "SOLICITUD", solicitudId, motivo.trim());
        return new Efecto<>(vista(e.valor()), e.avisoId());
    }

    @Transactional(readOnly = true)
    public List<MovimientoAdmin> libro(Long solicitudId) {
        if (!solicitudes.existsById(solicitudId)) {
            throw noEncontrado("Solicitud inexistente");
        }
        return libro.findBySolicitudIdOrderByIdAsc(solicitudId).stream().map(this::vista).toList();
    }

    @Transactional(readOnly = true)
    public Pagina<Auditoria> auditoria(int pagina, int tamano) {
        return paginar(auditoria.findAll(pedido(pagina, tamano, "id")), a -> new Auditoria(a.getId(), a.getAdminId(),
                a.getAccion(), a.getObjetivoTipo(), a.getObjetivoId(), a.getDetalle(), a.getCreadaEn()));
    }

    private void registrar(Long adminId, String accion, String tipo, Long objetivoId, String detalle) {
        auditoria.save(new AuditoriaAdmin(adminId, accion, tipo, objetivoId, detalle, reloj.instant()));
    }

    /** Las páginas más recientes primero; el tamaño se acota para que nadie pida la base entera de una vez. */
    private static PageRequest pedido(int pagina, int tamano, String campo) {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw invalida("pagina debe ser 0 o más y tamano de 1 a 100");
        }
        return PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, campo));
    }

    private static <A, B> Pagina<B> paginar(Page<A> p, Function<A, B> f) {
        return new Pagina<>(p.getContent().stream().map(f).toList(), p.getNumber(), p.getSize(), p.getTotalElements());
    }

    private UsuarioAdmin vista(Usuario u) {
        return new UsuarioAdmin(u.getId(), u.getRol(), u.getNombre(), u.getCorreo(), u.getTelefono(), u.isSuspendido(),
                u.getMotivoSuspension());
    }

    private SolicitudAdmin vista(Solicitud s) {
        return new SolicitudAdmin(s.getId(), s.getEstado(), s.getOficioId(), s.getDescripcion(), s.getHogarId(),
                s.getTecnicoId(), s.getPrecioAcordado(), s.getComisionCop(), s.getExpiraEn(), s.getCanceladaPor(),
                s.getMotivoCancelacion());
    }

    private MovimientoAdmin vista(Movimiento m) {
        return new MovimientoAdmin(m.getId(), m.getTecnicoId(), m.getSolicitudId(), m.getTipo(), m.getValor(),
                m.getSaldoPosterior(), m.getReferencia(), m.getCreadoEn());
    }

    private static ErrorNegocio invalida(String detalle) {
        return new ErrorNegocio(Codigo.VALIDATION_ERROR, detalle);
    }

    private static ErrorNegocio noEncontrado(String detalle) {
        return new ErrorNegocio(Codigo.NOT_FOUND, detalle);
    }
}
