package co.edu.unipamplona.servilink.infraestructura;

import co.edu.unipamplona.servilink.config.Configuracion;
import java.time.Duration;
import java.util.Map;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Es el único punto del sistema que sale a la red hacia Firebase. */
@Component
public class NotificadorFcm implements Notificador {

    private final RestClient cliente;
    private final String url;

    public NotificadorFcm(Configuracion config) {
        var fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(3));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
        this.url = config.fcmUrl();
    }

    @Override
    public boolean enviar(String tokenPush, String texto) {
        if (tokenPush == null || tokenPush.isBlank()) {
            return true; // sin token no hay a quién avisar: nada que reintentar
        }
        try {
            cliente.post().uri(url)
                    .body(Map.of("message", Map.of("token", tokenPush, "notification", Map.of("body", texto))))
                    .retrieve().toBodilessEntity(); // 4xx y 5xx lanzan RestClientException
            return true;
        } catch (RestClientException e) {
            return false; // no revierte la asignación: el aviso queda PENDIENTE en la bandeja
        }
    }
}
