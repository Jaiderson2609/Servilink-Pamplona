-- Avisos push (Firebase): el token de registro de un navegador puede pasar de 200 caracteres.
ALTER TABLE usuarios ALTER COLUMN token_push TYPE TEXT;
-- Un mismo token no puede pertenecer a dos cuentas (dos personas que usan el mismo celular).
CREATE UNIQUE INDEX ux_usuarios_token_push ON usuarios (token_push) WHERE token_push IS NOT NULL;
