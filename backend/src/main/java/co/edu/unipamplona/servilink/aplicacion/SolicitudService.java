package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.config.Configuracion;
import co.edu.unipamplona.servilink.dominio.*;
import co.edu.unipamplona.servilink.repositorio.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creación, consulta y postulación a solicitudes (RF-017, RF-020, RF-021, RF-026). */
@Service
public class SolicitudService {

    public static final List<String> ACTIVAS = EstadoSolicitud.codigos(EstadoSolicitud.E01, EstadoSolicitud.E02, EstadoSolicitud.E03, EstadoSolicitud.E04);

    public record Contacto(String nombre, String telefono) {}

    public record Vista(Long id, String estado, Integer oficioId, String oficio, String descripcion,
                        Integer precioAcordadoCop, Integer comisionCop, Instant expiraEn, Instant terminadoEn,
                        Long hogarId, Long tecnicoId, String motivoCancelacion, Contacto contacto,
                        Integer distanciaM) {}

    public record VistaPostulacion(Long id, Long tecnicoId, String tecnico, Integer precioCop, String mensaje,
                                   String estado, Double calificacionPromedio, long calificaciones) {}

    private final SolicitudRepositorio solicitudes;
    private final PostulacionRepositorio postulaciones;
    private final BilleteraRepositorio billeteras;
    private final OficioRepositorio oficios;
    private final UsuarioRepositorio usuarios;
    private final UbicacionTecnicoRepositorio ubicaciones;
    private final CalificacionRepositorio calificaciones;
    private final Configuracion config;
    private final Clock reloj;

    public SolicitudService(SolicitudRepositorio solicitudes, PostulacionRepositorio postulaciones,
                            BilleteraRepositorio billeteras, OficioRepositorio oficios, UsuarioRepositorio usuarios,
                            UbicacionTecnicoRepositorio ubicaciones, CalificacionRepositorio calificaciones,
                            Configuracion config, Clock reloj) {
        this.solicitudes = solicitudes;
        this.postulaciones = postulaciones;
        this.billeteras = billeteras;
        this.oficios = oficios;
        this.usuarios = usuarios;
        this.ubicaciones = ubicaciones;
        this.calificaciones = calificaciones;
        this.config = config;
        this.reloj = reloj;
    }

    /** TR-01: nace en E01 con una ventana de postulación; un hogar solo puede tener una solicitud activa (RN-06). */
    @Transactional
    public Vista crear(Long hogarId, Integer oficioId, String descripcion, double lat, double lng) {
        if (!oficios.existsById(oficioId)) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "El oficio no existe");
        }
        Instant ahora = reloj.instant();
        solicitudes.expirarVencidasDe(hogarId, ahora); // una solicitud vencida no debe bloquear una nueva
        if (solicitudes.existsByHogarIdAndEstadoIn(hogarId, ACTIVAS)) {
            throw new ErrorNegocio(Codigo.ACTIVE_REQUEST_EXISTS, "Ya tienes una solicitud en curso");
        }
        Solicitud s = solicitudes.save(new Solicitud(hogarId, oficioId, descripcion.trim(), lat, lng,
                ahora.plus(Duration.ofMinutes(config.ventanaPostulacionMin())), ahora));
        return vista(s, hogarId, "HOGAR");
    }

    /** TR-02: la primera postulación pasa a E02 y abre la ventana de elección del hogar. */
    @Transactional
    public VistaPostulacion postular(Long tecnicoId, Long solicitudId, int precio, String mensaje) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        if (!EstadoSolicitud.esUnoDe(s.getEstado(), EstadoSolicitud.E01, EstadoSolicitud.E02)) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "La solicitud está en " + s.getEstado());
        }
        Instant ahora = reloj.instant();
        if (!ahora.isBefore(s.getExpiraEn())) { // RN-03: decide el reloj del servidor
            throw new ErrorNegocio(Codigo.SERVICE_EXPIRED, "La ventana de postulación terminó");
        }
        if (postulaciones.existsBySolicitudIdAndTecnicoId(solicitudId, tecnicoId)) { // RN-08
            throw new ErrorNegocio(Codigo.DUPLICATE_APPLICATION, "Ya te postulaste a esta solicitud");
        }
        long saldo = billeteras.findById(tecnicoId).map(Billetera::getDisponible).orElse(0L);
        if (saldo < config.saldoMinimo()) { // RF-039
            throw new ErrorNegocio(Codigo.WALLET_BELOW_MINIMUM, "Se necesita un saldo mínimo de " + config.saldoMinimo() + " COP para postularse");
        }
        if (solicitudes.existsByTecnicoIdAndEstadoIn(tecnicoId, EstadoSolicitud.codigos(EstadoSolicitud.E03, EstadoSolicitud.E04))) { // RN-07
            throw new ErrorNegocio(Codigo.TECHNICIAN_UNAVAILABLE, "Ya tienes un servicio activo");
        }
        Postulacion p = postulaciones.save(new Postulacion(solicitudId, tecnicoId, precio, mensaje, ahora));
        if (EstadoSolicitud.E01.es(s.getEstado())) {
            s.setEstado(EstadoSolicitud.E02.codigo());
            s.setExpiraEn(ahora.plus(Duration.ofMinutes(config.ventanaEleccionMin())));
        }
        return vistaPostulacion(p);
    }

    /** RF-022: el hogar ve las postulaciones de su solicitud. */
    @Transactional(readOnly = true)
    public List<VistaPostulacion> postulaciones(Long hogarId, Long solicitudId) {
        Solicitud s = solicitudes.findById(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        if (!s.getHogarId().equals(hogarId)) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "La solicitud es de otro hogar");
        }
        return postulaciones.findBySolicitudId(solicitudId).stream().map(this::vistaPostulacion).toList();
    }

    @Transactional(readOnly = true)
    public Vista obtener(Long usuarioId, String rol, Long solicitudId) {
        Solicitud s = solicitudes.findById(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        boolean esHogar = s.getHogarId().equals(usuarioId);
        boolean esTecnico = usuarioId.equals(s.getTecnicoId())
                || (!"HOGAR".equals(rol) && postulaciones.existsBySolicitudIdAndTecnicoId(solicitudId, usuarioId));
        if (!esHogar && !esTecnico) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "No participas en esta solicitud");
        }
        return vista(s, usuarioId, rol);
    }

    @Transactional(readOnly = true)
    public List<Vista> mias(Long usuarioId, String rol) {
        var lista = "HOGAR".equals(rol) ? solicitudes.findByHogarIdOrderByIdDesc(usuarioId)
                : solicitudes.deTecnico(usuarioId);
        return lista.stream().map(s -> vista(s, usuarioId, rol)).toList();
    }

    /** Solicitudes que aún reciben postulaciones. Si el técnico definió su zona, se filtra por radio (PostGIS); el filtro por oficio sigue pendiente. */
    @Transactional(readOnly = true)
    public List<Vista> abiertas(Long tecnicoId) {
        Instant ahora = reloj.instant();
        if (ubicaciones.existsById(tecnicoId)) {
            // Con zona de trabajo definida: solo lo que cae dentro del radio, de la más cercana a la más lejana.
            var cercanas = solicitudes.cercanas(tecnicoId, ahora);
            var porId = new java.util.HashMap<Long, Solicitud>();
            solicitudes.findAllById(cercanas.stream().map(SolicitudRepositorio.Cercana::getId).toList())
                    .forEach(s -> porId.put(s.getId(), s));
            return cercanas.stream()
                    .map(c -> vista(porId.get(c.getId()), tecnicoId, "TECNICO", (int) Math.round(c.getDistancia())))
                    .toList();
        }
        return solicitudes.findByEstadoInAndExpiraEnAfterOrderByIdDesc(EstadoSolicitud.codigos(EstadoSolicitud.E01, EstadoSolicitud.E02), ahora)
                .stream().map(s -> vista(s, tecnicoId, "TECNICO")).toList();
    }

    /** RF-026: barrido de las solicitudes vencidas; lo invoca el trabajo programado. */
    @Transactional
    public int expirarVencidas() {
        return solicitudes.expirarVencidas(reloj.instant());
    }

    /** El contacto de la contraparte se muestra solo a las partes y solo desde E03 (RF-015 y RF-036). */
    Vista vista(Solicitud s, Long usuarioId, String rol) {
        return vista(s, usuarioId, rol, null);
    }

    Vista vista(Solicitud s, Long usuarioId, String rol, Integer distanciaM) {
        Contacto contacto = null;
        if (EstadoSolicitud.esUnoDe(s.getEstado(), EstadoSolicitud.E03, EstadoSolicitud.E04, EstadoSolicitud.E05) && s.getTecnicoId() != null) {
            Long otroId = "HOGAR".equals(rol) ? s.getTecnicoId() : s.getHogarId();
            contacto = usuarios.findById(otroId).map(u -> new Contacto(u.getNombre(), u.getTelefono())).orElse(null);
        }
        String oficio = oficios.findById(s.getOficioId()).map(Oficio::getNombre).orElse(null);
        return new Vista(s.getId(), s.getEstado(), s.getOficioId(), oficio, s.getDescripcion(), s.getPrecioAcordado(),
                s.getComisionCop(), s.getExpiraEn(), s.getTerminadoEn(), s.getHogarId(), s.getTecnicoId(),
                s.getMotivoCancelacion(), contacto, distanciaM);
    }

    private VistaPostulacion vistaPostulacion(Postulacion p) {
        String nombre = usuarios.findById(p.getTecnicoId()).map(Usuario::getNombre).orElse("");
        long total = calificaciones.countByDestinatarioId(p.getTecnicoId());
        Double promedio = total == 0 ? null : Math.round(calificaciones.promedio(p.getTecnicoId()) * 100.0) / 100.0;
        return new VistaPostulacion(p.getId(), p.getTecnicoId(), nombre, p.getPrecio(), p.getMensaje(), p.getEstado(),
                promedio, total);
    }

    static ErrorNegocio noEncontrada() {
        return new ErrorNegocio(Codigo.NOT_FOUND, "Solicitud inexistente");
    }
}
