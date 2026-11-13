package co.edu.unipamplona.servilink.config;

import co.edu.unipamplona.servilink.infraestructura.ManejadorEventos;
import jakarta.servlet.ServletContext;
import jakarta.websocket.server.ServerContainer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Canal de eventos en tiempo real. Sin orígenes extra: solo acepta el mismo origen que sirve la aplicación. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String RUTA = "/ws/v1/eventos";

    private final ManejadorEventos manejador;

    public WebSocketConfig(ManejadorEventos manejador) {
        this.manejador = manejador;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registro) {
        registro.addHandler(manejador, RUTA);
    }

    /**
     * Mensajes del cliente pequeños (autenticación y latido) y cierre de conexiones mudas. Se aplica solo si hay un
     * contenedor WebSocket real: en las pruebas con MockMvc no existe y no debe impedir que arranque el contexto.
     */
    @Bean
    SmartInitializingSingleton limitesWebSocket(ServletContext servlet) {
        return () -> {
            if (servlet.getAttribute("jakarta.websocket.server.ServerContainer") instanceof ServerContainer c) {
                c.setDefaultMaxTextMessageBufferSize(8 * 1024);
                c.setDefaultMaxSessionIdleTimeout(120_000L);
            }
        };
    }
}
