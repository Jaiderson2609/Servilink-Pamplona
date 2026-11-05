import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { ESTADOS } from './util.js';
import { traducir } from './api.js';

const Ctx = createContext(null);
export const useUI = () => useContext(Ctx);

/** Avisos temporales y diálogo de confirmación con motivo; ambos accesibles por teclado y lector de pantalla. */
export function UIProvider({ children }) {
  const [avisos, setAvisos] = useState([]);
  const [dialogo, setDialogo] = useState(null);
  const sig = useRef(1);

  const avisar = useCallback((texto, tipo = 'info') => {
    const id = sig.current++;
    setAvisos((a) => [...a, { id, texto, tipo }]);
    setTimeout(() => setAvisos((a) => a.filter((x) => x.id !== id)), tipo === 'error' ? 6500 : 3500);
  }, []);

  const falla = useCallback((e) => avisar(e?.message ? traducir(e) : 'Ocurrió un error inesperado', 'error'), [avisar]);

  /** Ejecuta fn y muestra el error si falla. Devuelve el resultado o undefined. */
  const intentar = useCallback(async (fn) => {
    try { return await fn(); } catch (e) { if (!e?.message) console.error(e); falla(e); return undefined; }
  }, [falla]);

  const pedir = useCallback((opciones) => new Promise((resolver) => setDialogo({ ...opciones, resolver })), []);
  const cerrar = (valor) => { dialogo?.resolver(valor); setDialogo(null); };

  return (
    <Ctx.Provider value={{ avisar, falla, intentar, pedir }}>
      {children}
      <div className="aviso-zona" aria-live="polite">
        {avisos.map((a) => <div key={a.id} className={'aviso ' + a.tipo} role={a.tipo === 'error' ? 'alert' : undefined}>{a.texto}</div>)}
      </div>
      {dialogo && <Dialogo {...dialogo} cerrar={cerrar} />}
    </Ctx.Provider>
  );
}

function Dialogo({ titulo, texto, etiqueta, opcional, confirmar = 'Aceptar', peligro, cerrar }) {
  const ref = useRef(null);
  const campo = useRef(null);
  const [error, setError] = useState('');
  useEffect(() => {
    const d = ref.current;
    if (!d.open) d.showModal();
    campo.current?.focus();
  }, []);

  const enviar = (ev) => {
    ev.preventDefault();
    if (etiqueta) {
      const v = campo.current.value.trim();
      if (!opcional && v.length < 5) { setError('Escribe al menos 5 caracteres'); return; }
      cerrar(v);
    } else cerrar(true);
  };

  return (
    <dialog ref={ref} className="dialogo" aria-labelledby="dlg-titulo" onCancel={(e) => { e.preventDefault(); cerrar(null); }}>
      <form onSubmit={enviar}>
        <h3 id="dlg-titulo">{titulo}</h3>
        {texto && <p className="suave">{texto}</p>}
        {etiqueta && (
          <div className="campo">
            <label htmlFor="dlg-campo">{etiqueta}</label>
            <textarea id="dlg-campo" ref={campo} maxLength={200} />
            <div className="error-campo" role="alert">{error}</div>
          </div>
        )}
        <div className="fila entre">
          <button type="button" className="btn sec" onClick={() => cerrar(null)}>Volver</button>
          <button type="submit" className={'btn' + (peligro ? ' peligro' : '')}>{confirmar}</button>
        </div>
      </form>
    </dialog>
  );
}

export function Insignia({ estado }) {
  const [t, c] = ESTADOS[estado] || [estado, ''];
  return <span className={'insignia ' + c}>{t}</span>;
}

export function Pestanas({ items, activa, alCambiar }) {
  return (
    <div className="pestanas" role="tablist">
      {items.map(([k, t]) => (
        <button key={k} className="pestana" role="tab" aria-selected={k === activa} onClick={() => alCambiar(k)}>{t}</button>
      ))}
    </div>
  );
}

export const Cargando = ({ texto = 'Cargando…' }) => <div className="cargando" role="status">{texto}</div>;
