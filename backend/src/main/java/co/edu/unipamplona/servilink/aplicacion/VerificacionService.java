package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.AuditoriaAdmin;
import co.edu.unipamplona.servilink.dominio.DocumentoVerificacion;
import co.edu.unipamplona.servilink.dominio.NotificacionPendiente;
import co.edu.unipamplona.servilink.dominio.Usuario;
import co.edu.unipamplona.servilink.repositorio.AuditoriaRepositorio;
import co.edu.unipamplona.servilink.repositorio.DocumentoVerificacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.NotificacionRepositorio;
import co.edu.unipamplona.servilink.repositorio.UsuarioRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación de identidad de los técnicos (RF-010 a RF-012). Los documentos son privados: solo se guardan en la
 * base, solo los lee el administrador y se borran en cuanto él decide (Ley 1581 de 2012: mínimo dato necesario).
 */
@Service
public class VerificacionService {

    public record Estado(String estado, String motivo) {}

    public record Pendiente(Long id, String nombre, String correo, String verificacion, Instant enviadaEn) {}

    public record Documento(String tipoMime, byte[] contenido) {}

    private final UsuarioRepositorio usuarios;
    private final DocumentoVerificacionRepositorio documentos;
    private final AuditoriaRepositorio auditoria;
    private final NotificacionRepositorio avisos;
    private final Clock reloj;

    public VerificacionService(UsuarioRepositorio usuarios, DocumentoVerificacionRepositorio documentos,
                               AuditoriaRepositorio auditoria, NotificacionRepositorio avisos, Clock reloj) {
        this.usuarios = usuarios;
        this.documentos = documentos;
        this.auditoria = auditoria;
        this.avisos = avisos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public Estado estado(Long tecnicoId) {
        Usuario u = tecnico(tecnicoId);
        return new Estado(u.getVerificacion(), u.getMotivoVerificacion());
    }

    /** Recibe los tres documentos a la vez; reemplaza un envío anterior y deja la solicitud en revisión. */
    @Transactional
    public Estado enviar(Long tecnicoId, Map<String, byte[]> archivos) {
        Usuario u = tecnico(tecnicoId);
        if ("APROBADA".equals(u.getVerificacion())) {
            throw new ErrorNegocio(Codigo.OPERATION_NOT_ALLOWED, "Tu identidad ya está verificada");
        }
        for (String tipo : DocumentoVerificacion.TIPOS) {
            byte[] b = archivos.get(tipo);
            if (b == null || b.length == 0) {
                throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "Falta el documento " + tipo);
            }
            if (b.length > FotoService.MAX_BYTES) {
                throw new ErrorNegocio(Codigo.PAYLOAD_TOO_LARGE, "Cada documento puede pesar hasta 2 MB");
            }
            if (FotoService.tipoPorContenido(b) == null) {
                throw new ErrorNegocio(Codigo.UNSUPPORTED_MEDIA_TYPE, "Solo se aceptan imágenes JPEG, PNG o WebP");
            }
        }
        Instant ahora = reloj.instant();
        documentos.borrarDe(tecnicoId);
        documentos.flush();
        for (String tipo : DocumentoVerificacion.TIPOS) {
            byte[] b = archivos.get(tipo);
            documentos.save(new DocumentoVerificacion(tecnicoId, tipo, FotoService.tipoPorContenido(b), b, ahora));
        }
        u.enviarVerificacion(ahora);
        return new Estado(u.getVerificacion(), null);
    }

    @Transactional(readOnly = true)
    public AdministracionService.Pagina<Pendiente> listar(String estado, int pagina, int tamano) {
        String filtro = estado == null ? "PENDIENTE" : estado;
        if (!java.util.List.of("SIN_ENVIAR", "PENDIENTE", "APROBADA", "RECHAZADA").contains(filtro)) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "estado no es válido");
        }
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "pagina debe ser 0 o más y tamano de 1 a 100");
        }
        var p = usuarios.findByRolAndVerificacion("TECNICO", filtro,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.ASC, "verificacionEnviadaEn", "id")));
        return new AdministracionService.Pagina<>(p.getContent().stream().map(u -> new Pendiente(u.getId(), u.getNombre(),
                u.getCorreo(), u.getVerificacion(), u.getVerificacionEnviadaEn())).toList(), p.getNumber(), p.getSize(),
                p.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Documento documento(Long tecnicoId, String tipo) {
        tecnico(tecnicoId);
        if (!DocumentoVerificacion.TIPOS.contains(tipo)) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "tipo debe ser CEDULA_FRENTE, CEDULA_REVERSO o SELFIE");
        }
        var d = documentos.findByTecnicoIdAndTipo(tecnicoId, tipo)
                .orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Documento inexistente"));
        return new Documento(d.getTipoMime(), d.getContenido());
    }

    /** Aprueba o rechaza (con motivo). Solo se decide sobre envíos en revisión; la decisión queda auditada. */
    @Transactional
    public Efecto<Estado> decidir(Long adminId, Long tecnicoId, boolean aprobada, String motivo) {
        Usuario u = tecnico(tecnicoId);
        if (!"PENDIENTE".equals(u.getVerificacion())) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "Este técnico no tiene una verificación en revisión");
        }
        String razon = motivo == null ? null : motivo.trim();
        if (!aprobada && (razon == null || razon.length() < 5 || razon.length() > 200)) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "El motivo del rechazo debe tener entre 5 y 200 caracteres");
        }
        Instant ahora = reloj.instant();
        u.resolverVerificacion(aprobada, razon);
        documentos.borrarDe(tecnicoId);
        auditoria.save(new AuditoriaAdmin(adminId, aprobada ? "APROBAR_TECNICO" : "RECHAZAR_TECNICO", "USUARIO", tecnicoId,
                aprobada ? null : razon, ahora));
        var aviso = avisos.save(new NotificacionPendiente("VERIF-" + tecnicoId + "-" + ahora.toEpochMilli(), tecnicoId,
                aprobada ? "Tu identidad fue verificada: ya puedes postularte" : "Tu verificación fue rechazada: " + razon, ahora));
        return new Efecto<>(new Estado(u.getVerificacion(), u.getMotivoVerificacion()), aviso.getId());
    }

    private Usuario tecnico(Long id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new ErrorNegocio(Codigo.NOT_FOUND, "Usuario inexistente"));
        if (!"TECNICO".equals(u.getRol())) {
            throw new ErrorNegocio(Codigo.NOT_FOUND, "El usuario no es un técnico");
        }
        return u;
    }
}
