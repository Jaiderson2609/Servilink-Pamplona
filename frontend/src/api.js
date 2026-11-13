import { nuevaLlave } from './util.js';

/** Con VITE_API_URL el cliente puede vivir en otro dominio (p. ej. un sitio estático en Render). */
const BASE = (import.meta.env?.VITE_API_URL || '') + '/api/v1';

let token = null;
let alVencer = null;

export const setToken = (t) => { token = t; };
export const alSesionVencida = (fn) => { alVencer = fn; };
export { nuevaLlave };

/** Mensajes en español según el código estable del servidor (DDS 10 y 11.1). */
const MENSAJES = {
  SERVICE_EXPIRED: 'El plazo de esta solicitud ya terminó.',
  TECHNICIAN_UNAVAILABLE: 'Ese técnico acaba de tomar otro servicio. Elige otra oferta.',
  INSUFFICIENT_BALANCE: 'El técnico ya no tiene saldo suficiente para este servicio. Elige otra oferta.',
  WALLET_BELOW_MINIMUM: 'Necesitas al menos $ 40.000 de saldo para postularte. Recarga tu billetera.',
  ACTIVE_REQUEST_EXISTS: 'Ya tienes una solicitud en curso. Termínala o cancélala para crear otra.',
  DUPLICATE_APPLICATION: 'Ya enviaste una oferta a esta solicitud.',
  CHAT_CLOSED: 'El chat solo está abierto mientras el servicio está en curso.',
  ACCOUNT_SUSPENDED: 'Tu cuenta está suspendida. Contacta al administrador.',
  EMAIL_ALREADY_REGISTERED: 'Ya existe una cuenta con ese correo.',
  UNAUTHENTICATED: 'Tu sesión venció o los datos son incorrectos.',
  RATE_LIMITED: 'Hay muchas peticiones seguidas. Espera un poco e intenta de nuevo.',
  PAYMENT_UNAVAILABLE: 'Los pagos con Wompi no están disponibles por ahora. Intenta de nuevo más tarde.',
  UPLOAD_UNAVAILABLE: 'La subida de fotos no está disponible por ahora. Intenta de nuevo más tarde.',
  GEOCODING_UNAVAILABLE: 'El buscador de direcciones no está disponible. Toca el mapa o escribe las coordenadas.',
};

export function traducir(error) {
  return MENSAJES[error.code] && error.code !== 'UNAUTHENTICATED' ? MENSAJES[error.code] : error.message;
}

/**
 * Llama al API. Devuelve el JSON (o un Blob con `blob: true`). Si falla lanza {status, code, message, traceId}.
 * `llave` agrega Idempotency-Key: úsala en las operaciones que crean o mueven dinero o estado.
 */
export async function api(ruta, { metodo = 'GET', json, form, blob, llave } = {}) {
  const cab = {};
  if (token) cab.Authorization = 'Bearer ' + token;
  if (llave) cab['Idempotency-Key'] = llave;
  let body;
  if (form) body = form;
  else if (json !== undefined) { cab['Content-Type'] = 'application/json'; body = JSON.stringify(json); }
  let r;
  try {
    r = await fetch(BASE + ruta, { method: metodo, headers: cab, body });
  } catch {
    throw { status: 0, code: 'NETWORK', message: 'No se pudo conectar con el servidor. Revisa tu conexión.' };
  }
  if (blob && r.ok) return r.blob();
  const txt = await r.text();
  let datos = null;
  if (txt) { try { datos = JSON.parse(txt); } catch { /* respuesta sin JSON */ } }
  if (!r.ok) {
    if (r.status === 401 && token && datos?.code === 'UNAUTHENTICATED' && alVencer) alVencer();
    const e = { status: r.status, code: datos?.code || 'ERROR', message: datos?.message || 'Ocurrió un error inesperado', traceId: datos?.traceId };
    throw e;
  }
  return datos;
}
