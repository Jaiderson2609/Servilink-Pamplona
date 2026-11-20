import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const getToken = vi.fn(async () => 'token-del-celular');
const initializeApp = vi.fn(() => ({ nombre: 'app' }));
vi.mock('@firebase/app', () => ({ initializeApp: (...a) => initializeApp(...a), getApps: () => [] }));
vi.mock('@firebase/messaging', () => ({ getMessaging: () => ({}), getToken: (...a) => getToken(...a) }));

import AvisosPush from '../AvisosPush.jsx';
import { activarPush, estadoPush, quitarPush, reiniciarPush } from '../push.js';

const CONFIG = { habilitado: true, apiKey: 'k', projectId: 'p', messagingSenderId: '1', appId: 'a', vapidKey: 'v' };
let llamadas;

function simularNavegador({ permiso = 'default', pedir = 'granted' } = {}) {
  Object.defineProperty(navigator, 'serviceWorker', { configurable: true, value: { register: vi.fn(async () => ({ scope: 'x' })) } });
  window.PushManager = function PushManager() {};
  window.Notification = { permission: permiso, requestPermission: vi.fn(async () => { window.Notification.permission = pedir; return pedir; }) };
}

beforeEach(() => {
  reiniciarPush();
  getToken.mockClear();
  initializeApp.mockClear();
  llamadas = [];
});

afterEach(() => {
  delete navigator.serviceWorker;
  delete window.PushManager;
  delete window.Notification;
});

function servidor(config = CONFIG) {
  globalThis.fetch = vi.fn(async (url, op = {}) => {
    llamadas.push(`${op.method || 'GET'} ${url}`);
    const cuerpo = String(url).endsWith('/push/config') ? config : null;
    return new Response(cuerpo ? JSON.stringify(cuerpo) : null, { status: cuerpo ? 200 : 204, headers: { 'Content-Type': 'application/json' } });
  });
}

describe('avisos push', () => {
  it('en un navegador sin soporte no se ofrece nada ni se llama al servidor', async () => {
    servidor();
    expect(await estadoPush()).toBe('no-disponible');
    expect(await activarPush({ pedirPermiso: true })).toBe('no-disponible');
    expect(llamadas).toEqual([]);
  });

  it('si el servidor no tiene Firebase configurado no se ofrece', async () => {
    simularNavegador();
    servidor({ habilitado: false });
    expect(await estadoPush()).toBe('no-disponible');
    const { container } = render(<AvisosPush />);
    await waitFor(() => expect(llamadas).toContain('GET /api/v1/push/config'));
    expect(container).toBeEmptyDOMElement();
  });

  it('con permiso concedido obtiene el token y lo guarda en el servidor', async () => {
    simularNavegador({ permiso: 'granted' });
    servidor();
    expect(await activarPush()).toBe('activo');
    expect(getToken).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ vapidKey: 'v' }));
    expect(initializeApp).toHaveBeenCalledWith({ apiKey: 'k', projectId: 'p', messagingSenderId: '1', appId: 'a' });
    expect(llamadas).toContain('PUT /api/v1/me/push-token');
    expect(navigator.serviceWorker.register).toHaveBeenCalledWith('/firebase-messaging-sw.js', { scope: '/firebase-cloud-messaging-push-scope' });
  });

  it('sin permiso y sin gesto de la persona no pregunta ni registra', async () => {
    simularNavegador({ permiso: 'default' });
    servidor();
    expect(await activarPush()).toBe('pendiente');
    expect(window.Notification.requestPermission).not.toHaveBeenCalled();
    expect(llamadas).not.toContain('PUT /api/v1/me/push-token');
  });

  it('si la persona bloqueó las notificaciones lo dice y no registra', async () => {
    simularNavegador({ permiso: 'denied' });
    servidor();
    expect(await activarPush({ pedirPermiso: true })).toBe('bloqueado');
    expect(getToken).not.toHaveBeenCalled();
  });

  it('el botón pide el permiso y deja los avisos activados', async () => {
    simularNavegador({ permiso: 'default' });
    servidor();
    render(<AvisosPush />);
    await userEvent.click(await screen.findByRole('button', { name: /activar avisos/i }));
    expect(await screen.findByText('Avisos activados')).toBeInTheDocument();
    expect(window.Notification.requestPermission).toHaveBeenCalledTimes(1);
    expect(llamadas).toContain('PUT /api/v1/me/push-token');
  });

  it('si la persona rechaza el permiso el botón desaparece y avisa cómo cambiarlo', async () => {
    simularNavegador({ permiso: 'default', pedir: 'denied' });
    servidor();
    render(<AvisosPush />);
    await userEvent.click(await screen.findByRole('button', { name: /activar avisos/i }));
    expect(await screen.findByText('Avisos bloqueados')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /activar avisos/i })).not.toBeInTheDocument();
  });

  it('si Firebase falla muestra el error y permite reintentar', async () => {
    simularNavegador({ permiso: 'default' });
    servidor();
    getToken.mockRejectedValueOnce(new Error('sin red'));
    render(<AvisosPush />);
    await userEvent.click(await screen.findByRole('button', { name: /activar avisos/i }));
    await userEvent.click(await screen.findByRole('button', { name: /reintentar avisos/i }));
    expect(await screen.findByText('Avisos activados')).toBeInTheDocument();
  });

  it('al salir de la sesión borra el token del servidor, pero solo si este navegador lo había registrado', async () => {
    simularNavegador({ permiso: 'granted' });
    servidor();
    quitarPush();
    expect(llamadas).toEqual([]);
    await activarPush();
    quitarPush();
    quitarPush(); // la segunda vez no hace nada
    await waitFor(() => expect(llamadas.filter((l) => l === 'DELETE /api/v1/me/push-token')).toHaveLength(1));
  });
});
