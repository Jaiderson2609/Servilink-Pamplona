package co.edu.unipamplona.servilink.infraestructura;

/** Puerto de salida hacia el servicio de notificaciones push. */
public interface Notificador {
    /** @return true si el aviso se entregó (o no había a quién avisar); false si hay que reintentar. */
    boolean enviar(String tokenPush, String texto);
}
