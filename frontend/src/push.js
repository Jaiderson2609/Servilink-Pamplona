import { api } from './api.js';

/**
 * Avisos push con Firebase Cloud Messaging. El SDK se carga solo cuando hace falta (import dinámico) y el service worker
 * de los avisos tiene su propio alcance para no mezclarse con el de la PWA. Si el servidor no tiene Firebase configurado
 * (/push/config → habilitado: false) o el navegador no lo soporta, nada se ofrece y nada cambia.
 */
const RUTA_SW = '/firebase-messaging-sw.js';
const ALCANCE = '/firebase-cloud-messaging-push-scope';

let config = null;
let registrado = false; // este navegador ya guardó su token en el servidor durante esta sesión
let fallo = false;

export const pushSoportado = () =>
  typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;

function configPush() {
  config ??= api('/push/config').catch(() => ({ habilitado: false }));
  return config;
}

/** Olvida lo aprendido (otra cuenta puede tener otra configuración, y las pruebas parten de cero). */
export function reiniciarPush() { config = null; registrado = false; fallo = false; }

/**
 * Registra este navegador: con permiso concedido obtiene el token de Firebase y lo guarda en el servidor.
 * `pedirPermiso` solo debe ser true dentro de un gesto de la persona (un toque al botón).
 * Devuelve 'no-disponible' | 'pendiente' (falta el permiso) | 'bloqueado' | 'activo' | 'error'.
 */
export async function activarPush({ pedirPermiso = false } = {}) {
  if (!pushSoportado()) return 'no-disponible';
  const cfg = await configPush();
  if (!cfg.habilitado) return 'no-disponible';
  let permiso = Notification.permission;
  if (permiso === 'default' && pedirPermiso) permiso = await Notification.requestPermission();
  if (permiso === 'denied') return 'bloqueado';
  if (permiso !== 'granted') return 'pendiente';
  try {
    const [{ initializeApp, getApps }, { getMessaging, getToken }] = await Promise.all([import('@firebase/app'), import('@firebase/messaging')]);
    const registro = await navigator.serviceWorker.register(RUTA_SW, { scope: ALCANCE });
    const app = getApps()[0] ?? initializeApp({ apiKey: cfg.apiKey, projectId: cfg.projectId, messagingSenderId: cfg.messagingSenderId, appId: cfg.appId });
    const token = await getToken(getMessaging(app), { vapidKey: cfg.vapidKey, serviceWorkerRegistration: registro });
    if (!token) throw new Error('sin token');
    await api('/me/push-token', { metodo: 'PUT', json: { token } });
    registrado = true;
    fallo = false;
    return 'activo';
  } catch {
    fallo = true;
    return 'error';
  }
}

/** Estado para mostrar en pantalla sin pedir nada: no-disponible | pendiente | bloqueado | activo | error. */
export async function estadoPush() {
  if (!pushSoportado()) return 'no-disponible';
  const cfg = await configPush();
  if (!cfg.habilitado) return 'no-disponible';
  if (Notification.permission === 'denied') return 'bloqueado';
  if (Notification.permission === 'granted') return fallo ? 'error' : 'activo';
  return 'pendiente';
}

/** Al salir de la sesión este dispositivo deja de recibir los avisos de la cuenta (el servidor borra el token). */
export function quitarPush() {
  if (!registrado) return;
  registrado = false; // antes de llamar: si la llamada falla por sesión vencida no se reintenta en bucle
  api('/me/push-token', { metodo: 'DELETE' }).catch(() => {});
}
