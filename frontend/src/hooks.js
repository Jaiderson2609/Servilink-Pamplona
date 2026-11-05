import { useCallback, useEffect, useRef, useState } from 'react';

/** Rutas con hash: #/ · #/s/12 (solicitud) · #/t/billetera · #/a/usuarios */
export function parsearRuta(hash) {
  const [, vista, valor] = (hash || '').split('/');
  if (vista === 's' && /^\d+$/.test(valor || '')) return { vista: 's', id: Number(valor) };
  if (vista === 't' || vista === 'a') return { vista, tab: valor || null };
  return { vista: 'inicio' };
}

export function useRuta() {
  const [ruta, setRuta] = useState(() => parsearRuta(window.location.hash));
  useEffect(() => {
    const al = () => setRuta(parsearRuta(window.location.hash));
    window.addEventListener('hashchange', al);
    return () => window.removeEventListener('hashchange', al);
  }, []);
  return ruta;
}
export const ir = (destino) => { window.location.hash = destino; };

/**
 * Pide los datos ahora y cada `ms`. Solo cambia el estado si la respuesta cambió, para no repintar sin necesidad.
 * Devuelve { datos, error, cargando, recargar }.
 */
export function usePolling(traer, ms, claves = []) {
  const [estado, setEstado] = useState({ datos: undefined, error: null, cargando: true });
  const ultimo = useRef('');
  const traerRef = useRef(traer);
  traerRef.current = traer;

  const recargar = useCallback(async () => {
    try {
      const d = await traerRef.current();
      const firma = JSON.stringify(d);
      if (firma !== ultimo.current) { ultimo.current = firma; setEstado({ datos: d, error: null, cargando: false }); }
      else setEstado((e) => (e.cargando || e.error ? { datos: d, error: null, cargando: false } : e));
    } catch (error) {
      setEstado((e) => ({ ...e, error, cargando: false }));
    }
  }, []);

  useEffect(() => {
    ultimo.current = '';
    setEstado({ datos: undefined, error: null, cargando: true });
    let vivo = true;
    recargar();
    const t = ms ? setInterval(() => { if (vivo) recargar(); }, ms) : null;
    return () => { vivo = false; if (t) clearInterval(t); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, claves);

  return { ...estado, recargar };
}

/** Reloj que se actualiza cada segundo (para las cuentas regresivas). */
export function useAhora(ms = 1000) {
  const [ahora, setAhora] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setAhora(Date.now()), ms); return () => clearInterval(t); }, [ms]);
  return ahora;
}
