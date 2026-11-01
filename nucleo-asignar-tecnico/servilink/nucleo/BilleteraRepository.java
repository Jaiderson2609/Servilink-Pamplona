package servilink.nucleo;

import java.math.BigDecimal;

/** Puerto de salida: billetera prepago del técnico y libro contable. */
public interface BilleteraRepository {
    BigDecimal saldo(String tecnicoId);
    void retener(String tecnicoId, BigDecimal monto, String referencia);
}
