-- Datos de demostración para comprobar la persistencia del volumen (FPI-26).
-- No contiene datos personales reales.
INSERT INTO billeteras (tecnico_id, disponible, retenido, nombre, telefono)
VALUES (5, 50000, 0, 'Tecnico Demo', '3000000000')
ON CONFLICT DO NOTHING;

INSERT INTO solicitudes (id, hogar_id, estado, expira_en)
VALUES (1, 7, 'E02', now() + interval '1 day')
ON CONFLICT DO NOTHING;

INSERT INTO postulaciones (id, solicitud_id, tecnico_id, precio)
VALUES (1, 1, 5, 20000)
ON CONFLICT DO NOTHING;
