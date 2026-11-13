package co.edu.unipamplona.servilink.aplicacion;

/** Puerto de la pasarela de pagos (DAD 3.2): el dominio no conoce a Wompi, solo este contrato. */
public interface PasarelaPago {

    /** Datos para abrir el pago: la dirección completa del checkout y lo que la compone. */
    record Cobro(String urlPago, String referencia, long montoCentavos, String moneda) {}

    /** Resultado de un evento cuya firma ya fue verificada. */
    record EventoPago(String tipo, String referencia, String transaccionId, String estado, long montoCentavos,
                      String moneda) {}

    /** Falso si faltan las llaves: el servidor lo informa en lugar de fingir un pago. */
    boolean disponible();

    Cobro iniciar(String referencia, long valorCop);

    /**
     * Verifica la firma del evento y lo interpreta.
     * @throws ErrorNegocio INVALID_SIGNATURE si no se puede comprobar que lo envió la pasarela.
     */
    EventoPago verificar(String cuerpo, String checksumCabecera);
}
