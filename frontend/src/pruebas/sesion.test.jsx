import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, simularApi } from './apoyo.jsx';
import App from '../App.jsx';
import { api, setToken } from '../api.js';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> }));

const sesionDe = (token, expiresIn = 900) => ({ accessToken: token, tokenType: 'Bearer', expiresIn, usuarioId: 1, rol: 'HOGAR', nombre: 'Ana Pérez' });
const DATOS = { 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] };
const NO_AUTH = [401, { code: 'UNAUTHENTICATED', message: 'Tu sesión venció, ingresa de nuevo', traceId: 't' }];

beforeEach(() => { sessionStorage.clear(); setToken(null); });
afterEach(() => { vi.useRealTimers(); sessionStorage.clear(); setToken(null); });

describe('renovación de sesión', () => {
  it('al abrir la plataforma sin sesión en la pestaña la recupera con la cookie de renovación', async () => {
    const llamadas = simularApi({ 'POST /auth/refresh': sesionDe('tok-recuperado'), ...DATOS });
    pintar(<App />);
    expect(await screen.findByText('Hola, Ana')).toBeInTheDocument();
    expect(llamadas.filter((l) => l.ruta === '/auth/refresh')).toHaveLength(1);
    expect(JSON.parse(sessionStorage.getItem('servilink')).token).toBe('tok-recuperado');
    expect(llamadas.find((l) => l.ruta === '/service-requests/mine').cab.Authorization).toBe('Bearer tok-recuperado');
  });

  it('si no hay cookie válida muestra el ingreso, sin mensajes de error', async () => {
    simularApi({ 'POST /auth/refresh': NO_AUTH });
    pintar(<App />);
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
    expect(screen.queryByText(/venció/)).toBeNull();
  });

  it('con sesión guardada no pide renovación al abrir', async () => {
    sessionStorage.setItem('servilink', JSON.stringify({ token: 'tok', expira: Date.now() + 600000, yo: { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' } }));
    setToken('tok');
    const llamadas = simularApi({ ...DATOS });
    pintar(<App />);
    expect(await screen.findByText('Hola, Ana')).toBeInTheDocument();
    expect(llamadas.some((l) => l.ruta === '/auth/refresh')).toBe(false);
  });

  it('un 401 con sesión renueva el token y repite la petición una sola vez', async () => {
    sessionStorage.setItem('servilink', JSON.stringify({ token: 'viejo', expira: Date.now() + 600000, yo: { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' } }));
    setToken('viejo');
    let intentos = 0;
    const llamadas = simularApi({
      'GET /trades': [{ id: 2, nombre: 'Electricista' }],
      'GET /service-requests/mine': (init) => (init.headers.Authorization === 'Bearer viejo' ? (intentos++, NO_AUTH) : []),
      'POST /auth/refresh': sesionDe('nuevo'),
    });
    pintar(<App />);
    expect(await screen.findByText('Hola, Ana')).toBeInTheDocument();
    await waitFor(() => expect(llamadas.filter((l) => l.ruta === '/service-requests/mine' && l.cab.Authorization === 'Bearer nuevo').length).toBeGreaterThan(0));
    expect(intentos).toBe(1);
    expect(JSON.parse(sessionStorage.getItem('servilink')).token).toBe('nuevo');
  });

  it('varias peticiones vencidas a la vez comparten una sola renovación', async () => {
    sessionStorage.setItem('servilink', JSON.stringify({ token: 'viejo', expira: Date.now() + 600000, yo: { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' } }));
    setToken('viejo');
    const llamadas = simularApi({
      'GET /a': (init) => (init.headers.Authorization === 'Bearer viejo' ? NO_AUTH : { ok: 'a' }),
      'GET /b': (init) => (init.headers.Authorization === 'Bearer viejo' ? NO_AUTH : { ok: 'b' }),
      'GET /service-requests/mine': [], 'GET /trades': [],
      'POST /auth/refresh': sesionDe('nuevo'),
    });
    pintar(<App />);
    await screen.findByText('Hola, Ana');
    const [a, b] = await Promise.all([api('/a'), api('/b')]);
    expect([a.ok, b.ok]).toEqual(['a', 'b']);
    expect(llamadas.filter((l) => l.ruta === '/auth/refresh')).toHaveLength(1);
  });

  it('si la renovación falla cierra la sesión y avisa', async () => {
    sessionStorage.setItem('servilink', JSON.stringify({ token: 'viejo', expira: Date.now() + 600000, yo: { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' } }));
    setToken('viejo');
    simularApi({ 'GET /trades': [], 'GET /service-requests/mine': NO_AUTH, 'POST /auth/refresh': NO_AUTH, 'POST /auth/logout': [204, undefined] });
    pintar(<App />);
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
    expect(sessionStorage.getItem('servilink')).toBeNull();
  });

  it('un 401 de /auth (clave incorrecta) no intenta renovar ni cierra nada', async () => {
    const llamadas = simularApi({
      'POST /auth/refresh': NO_AUTH,
      'POST /auth/login': [401, { code: 'UNAUTHENTICATED', message: 'Correo o clave incorrectos', traceId: 't' }],
    });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    await userEvent.type(screen.getByLabelText('Correo'), 'ana@correo.co');
    await userEvent.type(screen.getByLabelText('Clave'), 'mala-clave-123');
    await userEvent.click(document.querySelector('form button[type=submit]'));
    expect(await screen.findByText('Correo o clave incorrectos')).toBeInTheDocument();
    expect(llamadas.filter((l) => l.ruta === '/auth/refresh')).toHaveLength(1); // solo el intento inicial de recuperar
  });

  it('renueva sola un minuto antes de que venza el token de acceso', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    // El intento inicial de recuperar la sesión falla; luego se inicia sesión y arranca el reloj de renovación.
    const l = simularApi({ 'POST /auth/refresh': NO_AUTH, 'POST /auth/login': sesionDe('tok-1', 900), ...DATOS });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    await userEvent.type(screen.getByLabelText('Correo'), 'ana@correo.co');
    await userEvent.type(screen.getByLabelText('Clave'), 'clave-segura-1');
    await userEvent.click(document.querySelector('form button[type=submit]'));
    await screen.findByText('Hola, Ana');
    expect(l.filter((x) => x.ruta === '/auth/refresh')).toHaveLength(1);
    globalThis.fetch.mockClear();
    globalThis.fetch.mockImplementation(async (url) => {
      if (String(url).endsWith('/auth/refresh')) return new Response(JSON.stringify(sesionDe('tok-2', 900)), { status: 200, headers: { 'Content-Type': 'application/json' } });
      return new Response(JSON.stringify([]), { status: 200, headers: { 'Content-Type': 'application/json' } });
    });
    await act(async () => { await vi.advanceTimersByTimeAsync(839_000); }); // 14 min - 1 s: todavía no
    expect(globalThis.fetch.mock.calls.filter(([u]) => String(u).endsWith('/auth/refresh'))).toHaveLength(0);
    await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
    await waitFor(() => expect(JSON.parse(sessionStorage.getItem('servilink')).token).toBe('tok-2'));
  });

  it('salir revoca la renovación en el servidor y borra la sesión', async () => {
    sessionStorage.setItem('servilink', JSON.stringify({ token: 'tok', expira: Date.now() + 600000, yo: { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' } }));
    setToken('tok');
    const llamadas = simularApi({ ...DATOS, 'POST /auth/logout': [204, undefined] });
    pintar(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Salir' }));
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
    expect(llamadas.filter((l) => l.ruta === '/auth/logout')).toHaveLength(1);
    expect(sessionStorage.getItem('servilink')).toBeNull();
  });
});
