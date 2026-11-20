import { useEffect, useState } from 'react';
import { Icono } from './iconos.jsx';
import { activarPush, estadoPush } from './push.js';

const TEXTOS = {
  pendiente: ['Activar avisos', 'Entérate de solicitudes y ofertas al instante'],
  activo: ['Avisos activados', 'En este dispositivo'],
  bloqueado: ['Avisos bloqueados', 'Actívalos en los ajustes del navegador'],
  error: ['Reintentar avisos', 'No se pudieron activar'],
};

/**
 * Permite activar los avisos push en este dispositivo. No se muestra si el navegador no los soporta o el servidor
 * no tiene Firebase configurado. `variante`: "fila" (hoja del celular) o "lateral" (panel de pantallas anchas).
 */
export default function AvisosPush({ variante = 'fila' }) {
  const [estado, setEstado] = useState(null);
  useEffect(() => {
    let vivo = true;
    estadoPush().then((e) => vivo && setEstado(e));
    return () => { vivo = false; };
  }, []);

  if (!estado || estado === 'no-disponible') return null;
  const [titulo, sub] = TEXTOS[estado];
  const tocable = estado === 'pendiente' || estado === 'error';
  const activar = async () => { setEstado(await activarPush({ pedirPermiso: true })); };

  if (variante === 'lateral') {
    return tocable
      ? <button type="button" className="lat-salir" onClick={activar}>{titulo}</button>
      : <span className="lat-aviso" role="status">{titulo === 'Avisos activados' ? 'Avisos activados' : sub}</span>;
  }
  const cuerpo = (
    <>
      <span className="mov-fila-icono"><Icono k="campana" t={22} /></span>
      <span className="mov-fila-texto"><span className="mov-fila-titulo">{titulo}</span><span className="mov-fila-sub">{sub}</span></span>
    </>
  );
  return tocable
    ? <button type="button" className="mov-fila" onClick={activar}>{cuerpo}</button>
    : <div className="mov-fila" role="status">{cuerpo}</div>;
}
