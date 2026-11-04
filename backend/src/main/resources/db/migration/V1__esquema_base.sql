-- Esquema base de ServiLink (ERS 2.0). Identificadores numéricos para respetar el contrato OpenAPI vigente.
CREATE TABLE usuarios (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    rol           VARCHAR(10)  NOT NULL CHECK (rol IN ('HOGAR', 'TECNICO', 'ADMIN')),
    nombre        VARCHAR(80)  NOT NULL,
    correo        VARCHAR(120) NOT NULL UNIQUE,
    telefono      VARCHAR(20)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    token_push    VARCHAR(200),
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE solicitudes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hogar_id        BIGINT       NOT NULL REFERENCES usuarios (id),
    oficio          VARCHAR(40)  NOT NULL,
    descripcion     VARCHAR(500) NOT NULL,
    estado          VARCHAR(3)   NOT NULL CHECK (estado IN ('E01','E02','E03','E04','E05','E06','E07')),
    expira_en       TIMESTAMPTZ  NOT NULL,
    tecnico_id      BIGINT REFERENCES usuarios (id),
    precio_acordado INTEGER CHECK (precio_acordado > 0),
    comision_pct    NUMERIC(5,4),
    comision_cop    INTEGER CHECK (comision_cop >= 0),
    creada_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- RN-06: un hogar solo puede tener una solicitud activa (E01 a E04).
CREATE UNIQUE INDEX ux_hogar_solicitud_activa ON solicitudes (hogar_id) WHERE estado IN ('E01','E02','E03','E04');
-- RN-07: un técnico no puede tener más de un servicio en E03 o E04.
CREATE UNIQUE INDEX ux_tecnico_servicio_activo ON solicitudes (tecnico_id) WHERE estado IN ('E03','E04');

CREATE TABLE postulaciones (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitud_id BIGINT      NOT NULL REFERENCES solicitudes (id),
    tecnico_id   BIGINT      NOT NULL REFERENCES usuarios (id),
    precio       INTEGER     NOT NULL CHECK (precio > 0),
    mensaje      VARCHAR(200),
    estado       VARCHAR(10) NOT NULL DEFAULT 'ACTIVA' CHECK (estado IN ('ACTIVA','ELEGIDA','DESCARTADA','RETIRADA')),
    creada_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_postulacion_unica UNIQUE (solicitud_id, tecnico_id)  -- RN-08
);

CREATE TABLE billeteras (
    tecnico_id BIGINT PRIMARY KEY REFERENCES usuarios (id),
    disponible BIGINT NOT NULL DEFAULT 0 CHECK (disponible >= 0),
    retenido   BIGINT NOT NULL DEFAULT 0 CHECK (retenido >= 0)
);

-- RF-042: libro de movimientos inmutable. La referencia única hace idempotente cada operación.
CREATE TABLE libro_contable (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tecnico_id      BIGINT      NOT NULL REFERENCES usuarios (id),
    solicitud_id    BIGINT REFERENCES solicitudes (id),
    tipo            VARCHAR(12) NOT NULL CHECK (tipo IN ('RECARGA','RETENCION','LIBERACION','COMISION')),
    valor           BIGINT      NOT NULL CHECK (valor > 0),
    saldo_posterior BIGINT      NOT NULL,
    referencia      VARCHAR(40) NOT NULL UNIQUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE FUNCTION libro_inmutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'libro_contable es inmutable: solo admite inserciones';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_libro_inmutable BEFORE UPDATE OR DELETE ON libro_contable
    FOR EACH ROW EXECUTE FUNCTION libro_inmutable();

-- Bandeja de salida (outbox): el aviso se guarda en la misma transacción que la asignación.
CREATE TABLE notificaciones_pendientes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    clave           VARCHAR(40)  NOT NULL UNIQUE,
    destinatario_id BIGINT       NOT NULL REFERENCES usuarios (id),
    texto           VARCHAR(200) NOT NULL,
    estado          VARCHAR(10)  NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','ENVIADA')),
    intentos        INTEGER      NOT NULL DEFAULT 0,
    creada_en       TIMESTAMPTZ  NOT NULL DEFAULT now()
);
