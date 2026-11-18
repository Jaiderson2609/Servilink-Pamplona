-- Parámetros que el administrador ajusta sin redesplegar (ERS 4). Sin fila, rige el valor por defecto de la configuración.
CREATE TABLE parametros (
    clave           VARCHAR(30)    PRIMARY KEY,
    valor           NUMERIC(12, 2) NOT NULL,
    actualizado_en  TIMESTAMPTZ    NOT NULL,
    actualizado_por BIGINT         NOT NULL REFERENCES usuarios (id)
);

-- Los cambios de parámetros no apuntan a un objeto concreto.
ALTER TABLE auditoria_admin ALTER COLUMN objetivo_id DROP NOT NULL;
