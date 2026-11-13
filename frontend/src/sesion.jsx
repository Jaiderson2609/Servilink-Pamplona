import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { api, alSesionRenovar, alSesionVencida, setToken } from './api.js';
import { detenerEventos, iniciarEventos } from './eventos.js';

const Ctx = createContext(null);
export const useSesion = () => useContext(Ctx);

/** Se renueva un minuto antes de que venza el token de acceso, y nunca con menos de 5 s de margen. */
const ANTICIPO_MS = 60_000;
const MARGEN_MIN_MS = 5_000;

function leer() {
  try { const g = sessionStorage.getItem('servilink'); return g ? JSON.parse(g) : null; } catch { return null; }
}

const deRespuesta = (r) => ({
  token: r.accessToken,
  expira: Date.now() + r.expiresIn * 1000,
  yo: { id: r.usuarioId, rol: r.rol, nombre: r.nombre },
});

export function SesionProvider({ children, alVencer }) {
  const [sesion, setSesion] = useState(() => { const s = leer(); if (s) setToken(s.token); return s; });
  // Sin sesión en esta pestaña, se intenta recuperarla con la cookie de renovación antes de mostrar el ingreso.
  const [restaurando, setRestaurando] = useState(() => !leer());
  const enCurso = useRef(null);

  const guardar = (s) => {
    setToken(s ? s.token : null);
    setSesion(s);
    try { s ? sessionStorage.setItem('servilink', JSON.stringify(s)) : sessionStorage.removeItem('servilink'); } catch { /* sin almacenamiento */ }
  };

  const entrar = useCallback(async (correo, clave) => {
    const r = await api('/auth/login', { metodo: 'POST', json: { correo, clave } });
    guardar(deRespuesta(r));
  }, []);

  const registrar = useCallback(async (datos) => {
    await api('/auth/register', { metodo: 'POST', json: datos });
    await entrar(datos.correo, datos.clave);
  }, [entrar]);

  /** Renueva el token de acceso con la cookie de renovación. Varias llamadas a la vez comparten una sola petición. */
  const renovar = useCallback(() => {
    if (!enCurso.current) {
      enCurso.current = api('/auth/refresh', { metodo: 'POST' })
        .then((r) => { guardar(deRespuesta(r)); return true; })
        .catch(() => false)
        .finally(() => { enCurso.current = null; });
    }
    return enCurso.current;
  }, []);

  const salir = useCallback((mensaje) => {
    api('/auth/logout', { metodo: 'POST' }).catch(() => {}); // revoca la renovación en el servidor; si falla, igual se sale
    guardar(null);
    if (mensaje && alVencer) alVencer(mensaje);
    window.location.hash = '#/';
  }, [alVencer]);

  useEffect(() => { alSesionVencida(() => salir('Tu sesión venció, ingresa de nuevo')); }, [salir]);
  useEffect(() => { alSesionRenovar(renovar); }, [renovar]);

  useEffect(() => {
    if (!restaurando) return;
    renovar().finally(() => setRestaurando(false));
  }, [restaurando, renovar]);

  // Renovación anticipada: la sesión sigue mientras la persona usa la plataforma, sin cortes a los 15 minutos.
  const expira = sesion?.expira;
  useEffect(() => {
    if (!expira) return undefined;
    const espera = Math.max(expira - Date.now() - ANTICIPO_MS, MARGEN_MIN_MS);
    const t = setTimeout(renovar, espera);
    return () => clearTimeout(t);
  }, [expira, renovar]);

  // El canal en tiempo real vive mientras haya sesión; sin él, las pantallas siguen consultando por intervalos.
  const token = sesion?.token;
  useEffect(() => {
    if (token) iniciarEventos(token); else detenerEventos();
    return detenerEventos;
  }, [token]);

  const valor = useMemo(() => ({ yo: sesion?.yo ?? null, restaurando, entrar, registrar, salir }),
    [sesion, restaurando, entrar, registrar, salir]);
  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>;
}
