package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.config.Configuracion;
import java.time.Duration;
import java.util.Map;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Es el único punto del sistema que sale a la red hacia Firebase (API HTTP v1 de FCM).
 * Con la cuenta de servicio configurada firma cada llamada con un token de acceso; sin ella no hay a quién
 * ni cómo avisar y el envío queda apagado. El mensaje solo lleva datos: el service worker del navegador arma la notificación.
 */
@Component
public class NotificadorFcm implements Notificador {

    private static final Logger LOG = LoggerFactory.getLogger(NotificadorFcm.class);

    private final RestClient cliente;
    private final String url;
    private final CuentaServicioGoogle cuenta; // nulo en las pruebas que apuntan a un servidor falso sin autenticación

    @Autowired
    public NotificadorFcm(Configuracion config, CuentaServicioGoogle cuenta) {
        this(config, cuenta, cuenta.habilitada() ? config.fcmUrl().replace("{proyecto}", cuenta.proyectoId()) : config.fcmUrl());
    }

    /** Sin cuenta de servicio: llama a la URL tal cual, sin autenticación (servidor falso de las pruebas). */
    public NotificadorFcm(Configuracion config) {
        this(config, null, config.fcmUrl());
    }

    private NotificadorFcm(Configuracion config, CuentaServicioGoogle cuenta, String url) {
        var fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
        this.cuenta = cuenta;
        this.url = url;
    }

    @Override
    public boolean enviar(String tokenPush, String texto) {
        if (tokenPush == null || tokenPush.isBlank()) {
            return true; // sin token no hay a quién avisar: nada que reintentar
        }
        if (cuenta != null && !cuenta.habilitada()) {
            return true; // Firebase sin configurar: no se puede avisar y reintentar no lo arregla
        }
        String acceso = null;
        if (cuenta != null) {
            try {
                acceso = cuenta.tokenAcceso();
            } catch (RestClientException | IllegalStateException e) { // Google no dio el token: nada que ver con el del celular
                LOG.warn("No se pudo obtener el token de acceso de Firebase: {}", e.getClass().getSimpleName());
                return false;
            }
        }
        try {
            var peticion = cliente.post().uri(url);
            if (acceso != null) {
                peticion = peticion.header("Authorization", "Bearer " + acceso);
            }
            peticion.body(Map.of("message", Map.of("token", tokenPush, "data",
                            Map.of("titulo", "ServiLink Pamplona", "cuerpo", texto))))
                    .retrieve().toBodilessEntity(); // 4xx y 5xx lanzan RestClientException
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            LOG.warn("FCM respondió 404: el token ya no existe (o el proyecto de Firebase no es el correcto); el aviso se descarta");
            return true; // el navegador ya no está registrado: reintentar nunca lo entregará
        } catch (RestClientException e) {
            LOG.warn("No se pudo enviar el aviso push: {}", e.getClass().getSimpleName());
            return false; // no revierte la asignación: el aviso queda PENDIENTE en la bandeja
        }
    }
}
