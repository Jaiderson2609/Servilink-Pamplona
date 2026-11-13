import { useEffect, useRef, useState } from 'react';
import { api, nuevaLlave } from './api.js';
import { Cargando, useUI } from './ui.jsx';
import { usePolling } from './hooks.js';
import { TIPOS_MOV, cop, fecha, irA } from './util.js';

const CADA_MS = 3000;
const INTENTOS_MAX = 20; // un minuto: si Wompi no confirma antes, el saldo se actualizará solo cuando llegue el evento

/** Referencia de la recarga a la que vuelve el técnico después de pagar en Wompi (…/?recarga=SL-…), o null. */
export function referenciaDeRetorno() {
  const r = new URLSearchParams(window.location.search).get('recarga');
  return r && REF_VALIDA.test(r) ? r : null;
}

const REF_VALIDA = /^[A-Za-z0-9_-]{1,40}$/;
const CLAVE_PENDIENTE = 'servilink-recarga-pendiente';
const VIGENCIA_PENDIENTE_MS = 24 * 60 * 60 * 1000;

/**
 * La referencia se guarda antes de salir a Wompi: si Wompi no devuelve el parámetro ?recarga= (o el técnico entra por
 * su cuenta), igual se puede mostrar cómo terminó el pago. Es solo una pista del navegador: el servidor es quien manda.
 */
function guardarPendiente(referencia) {
  try { window.localStorage.setItem(CLAVE_PENDIENTE, JSON.stringify({ referencia, en: Date.now() })); } catch { /* sin almacenamiento: queda el parámetro de la dirección */ }
}
function olvidarPendiente() {
  try { window.localStorage.removeItem(CLAVE_PENDIENTE); } catch { /* nada que borrar */ }
}
export function referenciaPendiente() {
  try {
    const g = JSON.parse(window.localStorage.getItem(CLAVE_PENDIENTE) || 'null');
    return g && REF_VALIDA.test(g.referencia || '') && Date.now() - g.en < VIGENCIA_PENDIENTE_MS ? g.referencia : null;
  } catch { return null; }
}

const quitarRetornoDeLaDireccion = () => {
  try { window.history.replaceState(null, '', window.location.pathname + window.location.hash); } catch { /* sin historial: no pasa nada */ }
};

const MENSAJES = {
  APPROVED: ['ok', 'Recarga acreditada. Tu saldo ya está actualizado.'],
  FAILED: ['error', 'El pago no fue aprobado y tu saldo no cambió. Puedes intentarlo de nuevo.'],
  REVIEW: ['error', 'Recibimos tu pago, pero hay que revisarlo y todavía no se acredita. Un administrador lo revisará.'],
};

/** Consulta la recarga hasta que deja de estar pendiente: Wompi puede tardar unos segundos en avisar al servidor. */
function ResultadoRecarga({ referencia, silenciosa, alTerminar }) {
  const [estado, setEstado] = useState('PENDING');
  const [agotado, setAgotado] = useState(false);
  useEffect(() => {
    let vivo = true;
    let intentos = 0;
    let temporizador;
    const consultar = async () => {
      try {
        const r = await api(`/wallet/topups/${encodeURIComponent(referencia)}`);
        if (!vivo) return;
        setEstado(r.estado);
        if (r.estado !== 'PENDING') { quitarRetornoDeLaDireccion(); olvidarPendiente(); alTerminar(r.estado); return; }
      } catch { /* un fallo de red no es el resultado del pago: se vuelve a intentar */ }
      intentos += 1;
      if (intentos >= INTENTOS_MAX) { if (vivo) setAgotado(true); return; }
      temporizador = setTimeout(consultar, CADA_MS);
    };
    consultar();
    return () => { vivo = false; clearTimeout(temporizador); };
  }, [referencia]); // eslint-disable-line react-hooks/exhaustive-deps

  // Si la referencia salió del almacenamiento (no de la dirección) y el pago sigue pendiente, quizá el técnico nunca
  // pagó: no se le muestra un "confirmando" que no le corresponde.
  if (silenciosa && !MENSAJES[estado]) return null;
  const [tipo, texto] = MENSAJES[estado] || ['info', agotado
    ? 'Wompi todavía no confirma tu pago. Cuando lo haga, tu saldo se actualizará solo; puedes volver a esta pantalla más tarde.'
    : 'Estamos confirmando tu pago con Wompi…'];
  return <div className={'tarjeta resultado-pago ' + tipo} role="status">{texto}</div>;
}

export default function Billetera() {
  const { avisar, intentar } = useUI();
  const { datos, error, cargando, recargar } = usePolling(() => api('/wallet'), 20000, [], (e) => e.tipo === 'solicitud');
  const [valor, setValor] = useState(50000);
  const [enviando, setEnviando] = useState(false);
  const [retorno] = useState(() => referenciaDeRetorno() || referenciaPendiente());
  const [desdeDireccion] = useState(() => referenciaDeRetorno() !== null);
  const llaveDemo = useRef(nuevaLlave());
  const llavePago = useRef(nuevaLlave());

  if (cargando) return <Cargando />;
  if (error || !datos) return <div className="vacio">No pudimos cargar tu billetera. {error?.message}</div>;

  const pagar = async (ev) => {
    ev.preventDefault();
    setEnviando(true);
    const r = await intentar(() => api('/wallet/topups', { metodo: 'POST', json: { valorCop: Number(valor) }, llave: llavePago.current }));
    setEnviando(false);
    if (r?.urlPago) { llavePago.current = nuevaLlave(); guardarPendiente(r.referencia); irA(r.urlPago); }
  };

  const recargarDemo = async () => {
    const r = await intentar(() => api('/wallet/topups/demo', { metodo: 'POST', json: { valorCop: Number(valor) }, llave: llaveDemo.current }));
    if (r) { llaveDemo.current = nuevaLlave(); avisar('Recarga de demostración acreditada', 'ok'); recargar(); }
  };

  const hayFormaDeRecargar = datos.pagoDisponible || datos.recargaDemoDisponible;

  return (
    <>
      {retorno && <ResultadoRecarga referencia={retorno} silenciosa={!desdeDireccion} alTerminar={() => recargar()} />}
      <div className="rejilla">
        <div className="tarjeta estadistica"><div className="suave">Saldo disponible</div><div className="dinero">{cop(datos.saldoDisponibleCop)}</div>
          <div className="pequeno suave">Necesitas al menos $ 40.000 para postularte</div></div>
        <div className="tarjeta estadistica"><div className="suave">Comisión retenida</div><div className="dinero">{cop(datos.saldoRetenidoCop)}</div>
          <div className="pequeno suave">Se cobra al finalizar y se libera si cancelan</div></div>
      </div>
      <form className="tarjeta" onSubmit={pagar}>
        <h3>Recargar saldo</h3>
        {hayFormaDeRecargar ? (
          <>
            {datos.pagoDisponible && <p className="pequeno suave">Pagas en Wompi (Nequi, PSE o tarjeta, en modo de pruebas). Tus datos de pago no pasan por ServiLink.</p>}
            <div className="fila">
              <div className="campo"><label htmlFor="rec-valor">Valor (COP)</label>
                <input id="rec-valor" type="number" min={1000} max={1000000} step={1000} required value={valor} onChange={(e) => setValor(e.target.value)} /></div>
              {datos.pagoDisponible && <button className="btn" type="submit" disabled={enviando}>{enviando ? 'Abriendo Wompi…' : 'Pagar con Wompi'}</button>}
              {datos.recargaDemoDisponible && <button className="btn sec" type="button" onClick={recargarDemo}>Recarga de demostración</button>}
            </div>
          </>
        ) : <p className="suave">Las recargas no están disponibles por ahora. Intenta de nuevo más tarde.</p>}
      </form>
      <div className="tarjeta">
        <h3>Últimos movimientos</h3>
        {datos.movimientos.length ? (
          <div className="tabla-scroll"><table>
            <thead><tr><th>Fecha</th><th>Movimiento</th><th>Valor</th><th>Saldo</th></tr></thead>
            <tbody>{datos.movimientos.map((m) => (
              <tr key={m.id}><td>{fecha(m.fecha)}</td><td>{TIPOS_MOV[m.tipo] || m.tipo}</td><td>{cop(m.valorCop)}</td><td>{cop(m.saldoPosteriorCop)}</td></tr>
            ))}</tbody>
          </table></div>
        ) : <div className="vacio">Sin movimientos todavía.</div>}
      </div>
    </>
  );
}
