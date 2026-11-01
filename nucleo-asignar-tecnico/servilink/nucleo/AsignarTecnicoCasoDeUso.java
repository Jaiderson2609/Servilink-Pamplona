package servilink.nucleo;

/** Puerto de entrada: lo que el mundo exterior puede pedirle al núcleo. */
public interface AsignarTecnicoCasoDeUso {
    Resultado ejecutar(String solicitudId, String tecnicoId);

    record Resultado(String solicitudId, Estado estado, java.math.BigDecimal retencion, boolean repetida) { }
}
