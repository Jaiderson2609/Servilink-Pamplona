import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, alSesionVencida, setToken } from './api.js';

const Ctx = createContext(null);
export const useSesion = () => useContext(Ctx);

function leer() {
  try { const g = sessionStorage.getItem('servilink'); return g ? JSON.parse(g) : null; } catch { return null; }
}

export function SesionProvider({ children, alVencer }) {
  const [sesion, setSesion] = useState(() => { const s = leer(); if (s) setToken(s.token); return s; });

  const guardar = (s) => {
    setToken(s ? s.token : null);
    setSesion(s);
    try { s ? sessionStorage.setItem('servilink', JSON.stringify(s)) : sessionStorage.removeItem('servilink'); } catch { /* sin almacenamiento */ }
  };

  const entrar = useCallback(async (correo, clave) => {
    const r = await api('/auth/login', { metodo: 'POST', json: { correo, clave } });
    guardar({ token: r.accessToken, yo: { id: r.usuarioId, rol: r.rol, nombre: r.nombre } });
  }, []);

  const registrar = useCallback(async (datos) => {
    await api('/auth/register', { metodo: 'POST', json: datos });
    await entrar(datos.correo, datos.clave);
  }, [entrar]);

  const salir = useCallback((mensaje) => {
    guardar(null);
    if (mensaje && alVencer) alVencer(mensaje);
    window.location.hash = '#/';
  }, [alVencer]);

  useEffect(() => { alSesionVencida(() => salir('Tu sesión venció, ingresa de nuevo')); }, [salir]);

  const valor = useMemo(() => ({ yo: sesion?.yo ?? null, entrar, registrar, salir }), [sesion, entrar, registrar, salir]);
  return <Ctx.Provider value={valor}>{children}</Ctx.Provider>;
}
