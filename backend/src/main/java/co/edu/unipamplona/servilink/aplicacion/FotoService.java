package co.edu.unipamplona.servilink.aplicacion;

import co.edu.unipamplona.servilink.dominio.EstadoSolicitud;
import co.edu.unipamplona.servilink.dominio.FotoSolicitud;
import co.edu.unipamplona.servilink.dominio.Solicitud;
import co.edu.unipamplona.servilink.repositorio.FotoRepositorio;
import co.edu.unipamplona.servilink.repositorio.SolicitudRepositorio;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fotos de la solicitud (RF-022): hasta 3, de 2 MB como máximo, JPEG, PNG o WebP verificados por su contenido. */
@Service
public class FotoService {

    public static final int MAX_FOTOS = 3;
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    private final SolicitudRepositorio solicitudes;
    private final FotoRepositorio fotos;
    private final Clock reloj;

    public FotoService(SolicitudRepositorio solicitudes, FotoRepositorio fotos, Clock reloj) {
        this.solicitudes = solicitudes;
        this.fotos = fotos;
        this.reloj = reloj;
    }

    public record Resumen(Long id, String tipoMime, Integer tamanoBytes, java.time.Instant creadaEn) {}

    @Transactional
    public Resumen subir(Long hogarId, Long solicitudId, byte[] contenido) {
        Solicitud s = solicitudes.bloquear(solicitudId).orElseThrow(SolicitudService::noEncontrada);
        if (!s.getHogarId().equals(hogarId)) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "Solo el hogar de la solicitud agrega fotos");
        }
        if (!EstadoSolicitud.esUnoDe(s.getEstado(), EstadoSolicitud.E01, EstadoSolicitud.E02, EstadoSolicitud.E03)) {
            throw new ErrorNegocio(Codigo.INVALID_STATE_TRANSITION, "Ya no se pueden agregar fotos a esta solicitud");
        }
        if (contenido == null || contenido.length == 0) {
            throw new ErrorNegocio(Codigo.VALIDATION_ERROR, "La foto está vacía");
        }
        if (contenido.length > MAX_BYTES) {
            throw new ErrorNegocio(Codigo.PAYLOAD_TOO_LARGE, "Cada foto puede pesar hasta 2 MB");
        }
        String tipo = tipoPorContenido(contenido);
        if (tipo == null) {
            throw new ErrorNegocio(Codigo.UNSUPPORTED_MEDIA_TYPE, "Solo se aceptan imágenes JPEG, PNG o WebP");
        }
        if (fotos.countBySolicitudId(solicitudId) >= MAX_FOTOS) {
            throw new ErrorNegocio(Codigo.PHOTO_LIMIT_REACHED, "Cada solicitud admite hasta 3 fotos");
        }
        FotoSolicitud f = fotos.save(new FotoSolicitud(solicitudId, hogarId, tipo, contenido, reloj.instant()));
        return new Resumen(f.getId(), f.getTipoMime(), f.getTamanoBytes(), f.getCreadaEn());
    }

    @Transactional(readOnly = true)
    public List<Resumen> listar(Long usuarioId, String rol, Long solicitudId) {
        exigirVisibilidad(solicitudes.findById(solicitudId).orElseThrow(SolicitudService::noEncontrada), usuarioId, rol);
        return fotos.findBySolicitudIdOrderByIdAsc(solicitudId).stream()
                .map(r -> new Resumen(r.getId(), r.getTipoMime(), r.getTamanoBytes(), r.getCreadaEn())).toList();
    }

    @Transactional(readOnly = true)
    public FotoSolicitud obtener(Long usuarioId, String rol, Long solicitudId, Long fotoId) {
        exigirVisibilidad(solicitudes.findById(solicitudId).orElseThrow(SolicitudService::noEncontrada), usuarioId, rol);
        return fotos.findByIdAndSolicitudId(fotoId, solicitudId).orElseThrow(
                () -> new ErrorNegocio(Codigo.NOT_FOUND, "Foto inexistente"));
    }

    /** Las ven el hogar, el técnico asignado y, mientras la solicitud está abierta, cualquier técnico que pueda cotizarla. */
    private void exigirVisibilidad(Solicitud s, Long usuarioId, String rol) {
        boolean abiertaParaTecnicos = "TECNICO".equals(rol) && EstadoSolicitud.esUnoDe(s.getEstado(), EstadoSolicitud.E01, EstadoSolicitud.E02);
        if (!Participacion.esParte(s, usuarioId) && !abiertaParaTecnicos) {
            throw new ErrorNegocio(Codigo.FORBIDDEN, "No puedes ver las fotos de esta solicitud");
        }
    }

    /** No se confía en el Content-Type que declara el cliente: se reconoce la imagen por sus primeros bytes. */
    static String tipoPorContenido(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G' && b[4] == 0x0D
                && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
