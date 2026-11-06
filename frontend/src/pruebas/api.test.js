import { describe, expect, it, vi } from 'vitest';
import { alSesionVencida, api, setToken, traducir } from '../api.js';

const responder = (estado, cuerpo) => vi.fn(async () => new Response(cuerpo === undefined ? '' : JSON.stringify(cuerpo), { status: estado }));

describe('cliente del API', () => {
  it('envía el token y la clave de idempotencia y devuelve el JSON', async () => {
    setToken('abc');
    globalThis.fetch = responder(201, { id: 7 });
    const r = await api('/service-requests', { metodo: 'POST', json: { a: 1 }, llave: 'clave-12345678' });
    expect(r).toEqual({ id: 7 });
    const [url, init] = globalThis.fetch.mock.calls[0];
    expect(url).toBe('/api/v1/service-requests');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toBe('Bearer abc');
    expect(init.headers['Idempotency-Key']).toBe('clave-12345678');
    expect(init.headers['Content-Type']).toBe('application/json');
    expect(init.body).toBe('{"a":1}');
  });

  it('no envía Idempotency-Key si no se pide', async () => {
    setToken(null);
    globalThis.fetch = responder(200, []);
    await api('/trades');
    expect(globalThis.fetch.mock.calls[0][1].headers['Idempotency-Key']).toBeUndefined();
    expect(globalThis.fetch.mock.calls[0][1].headers.Authorization).toBeUndefined();
  });

  it('convierte el error del servidor en {status, code, message, traceId}', async () => {
    globalThis.fetch = responder(410, { code: 'SERVICE_EXPIRED', message: 'La ventana de selección terminó', traceId: 'T1' });
    await expect(api('/applications/1/select', { metodo: 'PATCH' })).rejects.toMatchObject({ status: 410, code: 'SERVICE_EXPIRED', traceId: 'T1' });
  });

  it('avisa si no hay conexión', async () => {
    globalThis.fetch = vi.fn(async () => { throw new TypeError('fallo de red'); });
    await expect(api('/trades')).rejects.toMatchObject({ status: 0, code: 'NETWORK' });
  });

  it('cierra la sesión cuando el token vence (401 UNAUTHENTICATED con sesión)', async () => {
    const vencida = vi.fn();
    alSesionVencida(vencida);
    setToken('viejo');
    globalThis.fetch = responder(401, { code: 'UNAUTHENTICATED', message: 'Token vencido', traceId: 'x' });
    await expect(api('/wallet')).rejects.toMatchObject({ status: 401 });
    expect(vencida).toHaveBeenCalledOnce();
  });

  it('un 401 sin sesión (login fallido) no cierra nada', async () => {
    const vencida = vi.fn();
    alSesionVencida(vencida);
    setToken(null);
    globalThis.fetch = responder(401, { code: 'UNAUTHENTICATED', message: 'Correo o clave incorrectos', traceId: 'x' });
    await expect(api('/auth/login', { metodo: 'POST', json: {} })).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
    expect(vencida).not.toHaveBeenCalled();
  });

  it('traduce los códigos del DDS a mensajes en español', () => {
    expect(traducir({ code: 'WALLET_BELOW_MINIMUM', message: 'x' })).toContain('$ 40.000');
    expect(traducir({ code: 'TECHNICIAN_UNAVAILABLE', message: 'x' })).toContain('otro servicio');
    expect(traducir({ code: 'OTRO', message: 'Mensaje del servidor' })).toBe('Mensaje del servidor');
  });
});
