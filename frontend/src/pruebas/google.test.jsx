import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, simularApi } from './apoyo.jsx';
import App from '../App.jsx';
import { setToken } from '../api.js';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> }));

const NO_AUTH = [401, { code: 'UNAUTHENTICATED', message: 'x', traceId: 't' }];
const SESION = { accessToken: 'tok-g', tokenType: 'Bearer', expiresIn: 900, usuarioId: 1, rol: 'HOGAR', nombre: 'Ana Gómez' };
const NUEVA = [422, { code: 'GOOGLE_REGISTRATION_REQUIRED', message: 'faltan datos', traceId: 't' }];
let enviarCredencial;

beforeEach(() => {
  sessionStorage.clear(); setToken(null); enviarCredencial = null;
  window.google = { accounts: { id: {
    initialize: vi.fn((o) => { enviarCredencial = o.callback; }),
    renderButton: vi.fn(),
  } } };
});
afterEach(() => { delete window.google; sessionStorage.clear(); setToken(null); });

const BASE = { 'POST /auth/refresh': NO_AUTH, 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] };

describe('ingreso con Google', () => {
  it('sin ID de cliente no muestra nada de Google', async () => {
    simularApi({ ...BASE, 'GET /auth/google/config': { clientId: '' } });
    pintar(<App />);
    await screen.findByLabelText('Correo');
    await new Promise((r) => setTimeout(r, 20));
    expect(window.google.accounts.id.renderButton).not.toHaveBeenCalled();
  });

  it('con ID de cliente dibuja el botón de Google', async () => {
    simularApi({ ...BASE, 'GET /auth/google/config': { clientId: 'cliente.apps.googleusercontent.com' } });
    pintar(<App />);
    await waitFor(() => expect(window.google.accounts.id.renderButton).toHaveBeenCalled());
    expect(window.google.accounts.id.initialize.mock.calls[0][0].client_id).toBe('cliente.apps.googleusercontent.com');
  });

  it('una cuenta existente entra con un solo toque', async () => {
    const llamadas = simularApi({ ...BASE, 'GET /auth/google/config': { clientId: 'c' }, 'POST /auth/google': SESION });
    pintar(<App />);
    await waitFor(() => expect(enviarCredencial).toBeTruthy());
    enviarCredencial({ credential: 'id-token' });
    expect(await screen.findByText('Hola, Ana')).toBeInTheDocument();
    expect(llamadas.find((l) => l.ruta === '/auth/google').cuerpo).toEqual({ credencial: 'id-token' });
  });

  it('una cuenta nueva pide rol, teléfono y términos y reenvía la misma credencial', async () => {
    let n = 0;
    const llamadas = simularApi({ ...BASE, 'GET /auth/google/config': { clientId: 'c' }, 'POST /auth/google': () => (n++ === 0 ? NUEVA : SESION) });
    pintar(<App />);
    await waitFor(() => expect(enviarCredencial).toBeTruthy());
    enviarCredencial({ credential: 'id-token' });
    await userEvent.selectOptions(await screen.findByLabelText('Quiero'), 'TECNICO');
    await userEvent.type(screen.getByLabelText('Teléfono'), '3001234567');
    await userEvent.click(screen.getByLabelText(/Acepto la política/));
    await userEvent.click(screen.getByRole('button', { name: 'Crear cuenta con Google' }));
    expect(await screen.findByText('Hola, Ana')).toBeInTheDocument();
    const envios = llamadas.filter((l) => l.ruta === '/auth/google');
    expect(envios[1].cuerpo).toEqual({ credencial: 'id-token', rol: 'TECNICO', telefono: '3001234567', aceptaTerminos: true });
  });

  it('se puede cancelar el registro y volver al botón', async () => {
    simularApi({ ...BASE, 'GET /auth/google/config': { clientId: 'c' }, 'POST /auth/google': NUEVA });
    pintar(<App />);
    await waitFor(() => expect(enviarCredencial).toBeTruthy());
    enviarCredencial({ credential: 'id-token' });
    await userEvent.click(await screen.findByRole('button', { name: 'Cancelar' }));
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
    await waitFor(() => expect(window.google.accounts.id.renderButton).toHaveBeenCalledTimes(2));
  });

  it('muestra el error cuando Google o el servidor rechazan el ingreso', async () => {
    simularApi({ ...BASE, 'GET /auth/google/config': { clientId: 'c' },
      'POST /auth/google': [403, { code: 'ACCOUNT_SUSPENDED', message: 'x', traceId: 't' }] });
    pintar(<App />);
    await waitFor(() => expect(enviarCredencial).toBeTruthy());
    enviarCredencial({ credential: 'id-token' });
    expect(await screen.findByText(/cuenta está suspendida/)).toBeInTheDocument();
  });
});
