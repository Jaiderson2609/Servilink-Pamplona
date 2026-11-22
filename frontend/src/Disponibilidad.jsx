import { api } from './api.js';
import { useUI } from './ui.jsx';
import { usePolling } from './hooks.js';

const ESTADOS = { AVAILABLE: 'Disponible', BUSY: 'Ocupado', OFFLINE: 'No disponible' };

/**
 * RF-019: solo un técnico disponible ve solicitudes y puede postularse. El interruptor alterna Disponible / No disponible.
 * En pantallas anchas es una tarjeta con "Marcar como ocupado"; en el celular (`compacta`) es la píldora de la barra superior.
 */
export default function Disponibilidad({ compacta = false }) {
  const { intentar, avisar } = useUI();
  const { datos, recargar } = usePolling(() => api('/technicians/me/availability'), 30000, [], (e) => e.tipo === 'solicitud'); // cambia sola al ser elegido, terminar o cancelar
  const poner = async (estado) => {
    const r = await intentar(() => api('/technicians/me/availability', { metodo: 'PATCH', json: { estado } }));
    if (r) { avisar('Disponibilidad actualizada', 'ok'); recargar(); }
  };
  if (!datos) return null;
  const activo = datos.estado === 'AVAILABLE';

  if (compacta) {
    return (
      <button type="button" role="switch" aria-checked={activo} aria-label="Mi disponibilidad" className="disp-pildora"
        onClick={() => poner(activo ? 'OFFLINE' : 'AVAILABLE')}>
        {ESTADOS[datos.estado] || datos.estado}
        <span className="disp-pista"><span className="disp-bola" /></span>
      </button>
    );
  }
  return (
    <div className="disp">
      <div className="disp-texto">
        <span id="t-disp-etq" className="disp-titulo">Mi disponibilidad</span>
        <span className="pequeno suave">{ESTADOS[datos.estado] || datos.estado}{activo ? ' · recibes avisos de nuevas solicitudes' : ''}</span>
        {activo && <button type="button" className="enlace pequeno" onClick={() => poner('BUSY')}>Marcar como ocupado</button>}
      </div>
      <button type="button" role="switch" aria-checked={activo} aria-labelledby="t-disp-etq" className="interruptor"
        onClick={() => poner(activo ? 'OFFLINE' : 'AVAILABLE')}><span className="perilla" /></button>
    </div>
  );
}
