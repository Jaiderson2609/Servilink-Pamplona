/**
 * Canal de eventos en tiempo real (WebSocket /ws/v1/eventos). El servidor solo avisa que algo cambió, sin datos:
 * quien escucha vuelve a pedir lo que necesita por la API. Si el canal se cae, las pantallas siguen consultando
 * por intervalos como antes (el sondeo es el respaldo), y al volver la conexión se emite 'reconectado' para
 * que recarguen lo que pudieron perderse.
 */
export const RESPALDO_MS = 30000; // con el canal vivo, el sondeo baja a una consulta cada 30 s
const LATIDO_MS = 25000;
const ESPERA_MIN_MS = 1000;
const ESPERA_MAX_MS = 30000;

let token = null;
let activo = false;
let socket = null;
let conectado = false;
let cayo = false;
let espera = ESPERA_MIN_MS;
let temporizadorReconexion = null;
let temporizadorLatido = null;
const oyentes = new Set();
const oyentesEstado = new Set();

/** El WebSocket usa el mismo origen que la API; con VITE_API_URL, el de ese servidor. */
export function urlEventos() {
  const base = import.meta.env?.VITE_API_URL;
  if (base) return base.replace(/^http/, 'ws') + '/ws/v1/eventos';
  const { protocol, host } = window.location;
  return (protocol === 'https:' ? 'wss:' : 'ws:') + '//' + host + '/ws/v1/eventos';
}

export const eventosConectados = () => conectado;

/** Recibe cada evento del servidor ({tipo:'solicitud'|'chat'|'abiertas', id?}) y {tipo:'reconectado'}. Devuelve cómo dejar de escuchar. */
export function suscribirEventos(fn) {
  oyentes.add(fn);
  return () => oyentes.delete(fn);
}

export function suscribirEstadoEventos(fn) {
  oyentesEstado.add(fn);
  return () => oyentesEstado.delete(fn);
}

function emitir(evento) {
  for (const fn of [...oyentes]) {
    try { fn(evento); } catch { /* un oyente roto no debe afectar a los demás */ }
  }
}

function fijarConectado(valor) {
  if (conectado === valor) return;
  conectado = valor;
  for (const fn of [...oyentesEstado]) {
    try { fn(valor); } catch { /* idem */ }
  }
}

export function iniciarEventos(nuevoToken) {
  if (activo && token === nuevoToken) return;
  detenerEventos();
  token = nuevoToken;
  activo = true;
  cayo = false;
  espera = ESPERA_MIN_MS;
  conectar();
}

export function detenerEventos() {
  activo = false;
  token = null;
  clearTimeout(temporizadorReconexion);
  clearInterval(temporizadorLatido);
  const s = socket;
  socket = null;
  if (s) {
    s.onopen = s.onmessage = s.onclose = s.onerror = null;
    try { s.close(); } catch { /* ya cerrado */ }
  }
  fijarConectado(false);
}

function programarReconexion() {
  if (!activo) return;
  const espera_ms = espera * (1 + Math.random() * 0.3); // un poco de azar evita que todos reconecten a la vez
  temporizadorReconexion = setTimeout(conectar, espera_ms);
  espera = Math.min(espera * 2, ESPERA_MAX_MS);
}

function conectar() {
  if (!activo || typeof WebSocket === 'undefined') return;
  let s;
  try {
    s = new WebSocket(urlEventos());
  } catch {
    cayo = true;
    programarReconexion();
    return;
  }
  socket = s;
  s.onopen = () => s.send(JSON.stringify({ tipo: 'auth', token })); // el token no viaja en la URL
  s.onmessage = (m) => {
    let e;
    try { e = JSON.parse(m.data); } catch { return; }
    if (!e || typeof e.tipo !== 'string') return;
    if (e.tipo === 'listo') {
      espera = ESPERA_MIN_MS;
      clearInterval(temporizadorLatido);
      temporizadorLatido = setInterval(() => { if (s.readyState === 1) s.send('{"tipo":"ping"}'); }, LATIDO_MS);
      fijarConectado(true);
      if (cayo) { cayo = false; emitir({ tipo: 'reconectado' }); } // lo ocurrido mientras estuvo caído se recarga
    } else if (e.tipo !== 'pong') {
      emitir(e);
    }
  };
  s.onclose = () => {
    if (socket !== s) return;
    socket = null;
    clearInterval(temporizadorLatido);
    fijarConectado(false);
    cayo = true;
    programarReconexion();
  };
  s.onerror = () => { /* el cierre posterior se encarga de reconectar */ };
}
