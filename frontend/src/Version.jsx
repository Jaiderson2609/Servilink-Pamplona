import { useEffect, useState } from 'react';

const REVISAR_CADA_MS = 10 * 60 * 1000;
const RUTA_SCRIPT = /\/assets\/index-[\w-]+\.js/;

/** Nombre (con hash) del archivo principal que esta pestaña tiene cargado; cambia en cada versión nueva del sitio. */
export function scriptActual() {
  const el = Array.from(document.querySelectorAll('script[src]')).find((s) => RUTA_SCRIPT.test(s.getAttribute('src')));
  return el ? el.getAttribute('src').match(RUTA_SCRIPT)[0] : null;
}

/**
 * Una pestaña abierta (sobre todo en el celular) sigue con el código viejo hasta que se recarga. Al volver a la app y cada
 * 10 minutos se pregunta cuál es el archivo principal vigente; si cambió, se ofrece actualizar (sin recargar sola para no
 * perder lo que la persona esté escribiendo).
 */
export default function Version() {
  const [nueva, setNueva] = useState(false);
  useEffect(() => {
    const actual = scriptActual();
    if (!actual) return undefined; // desarrollo o pruebas: no hay archivo con hash
    let vivo = true;
    const revisar = async () => {
      try {
        const r = await fetch('/', { cache: 'no-store' });
        const m = (await r.text()).match(RUTA_SCRIPT);
        if (vivo && m && m[0] !== actual) setNueva(true);
      } catch { /* sin red: se revisa en la próxima */ }
    };
    const alVolver = () => { if (!document.hidden) revisar(); };
    const t = setInterval(revisar, REVISAR_CADA_MS);
    document.addEventListener('visibilitychange', alVolver);
    return () => { vivo = false; clearInterval(t); document.removeEventListener('visibilitychange', alVolver); };
  }, []);
  if (!nueva) return null;
  return (
    <div className="aviso-version" role="status">
      Hay una versión nueva de ServiLink.
      <button type="button" className="btn chico" onClick={() => window.location.reload()}>Actualizar</button>
    </div>
  );
}
