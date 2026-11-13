package co.edu.unipamplona.servilink.web;

import co.edu.unipamplona.servilink.aplicacion.CalificacionService;
import co.edu.unipamplona.servilink.aplicacion.ChatService;
import co.edu.unipamplona.servilink.aplicacion.FotoService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Fotos, chat y calificaciones de una solicitud. Los permisos finos (quién es parte) los decide el servicio. */
@RestController
@RequestMapping("/api/v1")
public class ParticipantesController {

    private final FotoService fotos;
    private final ChatService chat;
    private final CalificacionService calificaciones;

    public ParticipantesController(FotoService fotos, ChatService chat, CalificacionService calificaciones) {
        this.fotos = fotos;
        this.chat = chat;
        this.calificaciones = calificaciones;
    }

    private static Long id(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private static String rol(Jwt jwt) { return jwt.getClaimAsString("rol"); }

    @PostMapping(path = "/service-requests/{solicitudId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoService.Resumen> subirFoto(@PathVariable Long solicitudId,
            @RequestParam("foto") MultipartFile foto, @AuthenticationPrincipal Jwt jwt) throws IOException {
        var r = fotos.subir(id(jwt), solicitudId, foto.getBytes());
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + solicitudId + "/photos/" + r.id())).body(r);
    }

    /** Firma para que el navegador suba la foto directo a Cloudinary (DDS: /uploads/signature). */
    @PostMapping("/uploads/signature")
    public co.edu.unipamplona.servilink.aplicacion.AlmacenImagenes.Firma firmarSubida(
            @Valid @RequestBody Peticiones.FirmaSubida p, @AuthenticationPrincipal Jwt jwt) {
        return fotos.firmarSubida(id(jwt), p.solicitudId());
    }

    /** Registra la foto que el navegador ya subió a Cloudinary, verificando la firma de su respuesta. */
    @PostMapping("/service-requests/{solicitudId}/photos/cloudinary")
    public ResponseEntity<FotoService.Resumen> registrarFotoNube(@PathVariable Long solicitudId,
            @Valid @RequestBody Peticiones.FotoNube f, @AuthenticationPrincipal Jwt jwt) {
        var r = fotos.registrarNube(id(jwt), solicitudId, f.publicId(), f.version(), f.signature(), f.format());
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + solicitudId + "/photos/" + r.id())).body(r);
    }

    @GetMapping("/service-requests/{solicitudId}/photos")
    public List<FotoService.Resumen> listarFotos(@PathVariable Long solicitudId, @AuthenticationPrincipal Jwt jwt) {
        return fotos.listar(id(jwt), rol(jwt), solicitudId);
    }

    @GetMapping("/service-requests/{solicitudId}/photos/{fotoId}")
    public ResponseEntity<byte[]> descargarFoto(@PathVariable Long solicitudId, @PathVariable Long fotoId,
            @AuthenticationPrincipal Jwt jwt) {
        var f = fotos.obtener(id(jwt), rol(jwt), solicitudId, fotoId);
        if (f.getContenido() == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FOUND).location(URI.create(f.getUrl())).build();
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.getTipoMime()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff").body(f.getContenido());
    }

    @PostMapping("/service-requests/{solicitudId}/messages")
    public ResponseEntity<ChatService.Vista> enviarMensaje(@PathVariable Long solicitudId,
            @Valid @RequestBody Peticiones.MensajeCrear m, @AuthenticationPrincipal Jwt jwt) {
        var v = chat.enviar(id(jwt), solicitudId, m.texto());
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + solicitudId + "/messages/" + v.id())).body(v);
    }

    @GetMapping("/service-requests/{solicitudId}/messages")
    public List<ChatService.Vista> mensajes(@PathVariable Long solicitudId,
            @RequestParam(defaultValue = "0") long despues, @AuthenticationPrincipal Jwt jwt) {
        return chat.listar(id(jwt), solicitudId, despues);
    }

    @PostMapping("/service-requests/{solicitudId}/ratings")
    public ResponseEntity<CalificacionService.Vista> calificar(@PathVariable Long solicitudId,
            @Valid @RequestBody Peticiones.CalificacionCrear c, @AuthenticationPrincipal Jwt jwt) {
        var v = calificaciones.calificar(id(jwt), solicitudId, c.estrellas(), c.comentario());
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + solicitudId + "/ratings")).body(v);
    }

    @GetMapping("/technicians/{tecnicoId}/reputation")
    public CalificacionService.Reputacion reputacion(@PathVariable Long tecnicoId) {
        return calificaciones.reputacion(tecnicoId);
    }
}
