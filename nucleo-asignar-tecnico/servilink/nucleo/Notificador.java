package servilink.nucleo;

/** Puerto de salida: avisos a las personas (hoy FCM, mañana cualquier otro medio). */
public interface Notificador {
    void asignacionConfirmada(String hogarId, String tecnicoId, String solicitudId);
}
