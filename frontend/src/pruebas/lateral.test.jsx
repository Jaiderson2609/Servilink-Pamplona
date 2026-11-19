import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';
import App from '../App.jsx';
import { menuDe } from '../menus.js';
import { setToken } from '../api.js';

vi.mock('../Mapa.jsx', () => ({ default: () => <div data-testid="mapa" /> })); // Leaflet necesita un navegador real

const HOGAR = { id: 1, rol: 'HOGAR', nombre: 'Ana Pérez' };
const TECNICO = { id: 2, rol: 'TECNICO', nombre: 'Luis Mora' };
const ADMIN = { id: 9, rol: 'ADMIN', nombre: 'Jaider' };
const vacio = { code: 'NOT_FOUND', message: 'x', traceId: 't' };

beforeEach(() => { setToken(null); window.location.hash = ''; });

const enlaces = () => within(screen.getByRole('navigation', { name: 'Principal' })).getAllByRole('link').map((a) => a.textContent);

describe('panel lateral', () => {
  it('el hogar ve sus dos opciones y la activa es Nueva solicitud', async () => {
    sesionComo(HOGAR);
    simularApi({ 'GET /service-requests/mine': [], 'GET /trades': [{ id: 2, nombre: 'Electricista' }] });
    pintar(<App />);
    await screen.findByText('Hola, Ana');
    expect(enlaces()).toEqual(['Nueva solicitud', 'Mis solicitudes']);
    expect(screen.getByRole('link', { name: 'Nueva solicitud' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText('Ana Pérez')).toBeInTheDocument();
  });

  it('Mis solicitudes lista todas las solicitudes del hogar', async () => {
    sesionComo(HOGAR);
    window.location.hash = '#/h/solicitudes';
    simularApi({
      'GET /service-requests/mine': [{ id: 3, estado: 'CERRADA', descripcion: 'Cambiar guardas de la puerta', oficio: 'Cerrajería', hogarId: 1 }],
      'GET /trades': [],
    });
    pintar(<App />);
    expect(await screen.findByRole('heading', { name: 'Mis solicitudes' })).toBeInTheDocument();
    expect(screen.getByText('Cambiar guardas de la puerta')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis solicitudes' })).toHaveAttribute('aria-current', 'page');
  });

  it('el técnico ve sus cinco opciones y puede cambiar de una a otra', async () => {
    sesionComo(TECNICO);
    simularApi({
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, vacio], 'GET /technicians/me/verification': [404, vacio],
      'GET /technicians/me/availability': { estado: 'AVAILABLE' }, 'GET /wallet': [404, vacio],
    });
    pintar(<App />);
    await screen.findByText('Hola, Luis');
    expect(enlaces()).toEqual(['Solicitudes cercanas', 'Mis servicios', 'Billetera', 'Zona de trabajo', 'Verificación']);
    expect(screen.getByRole('link', { name: 'Solicitudes cercanas' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('link', { name: 'Zona de trabajo' }));
    await waitFor(() => expect(window.location.hash).toBe('#/t/zona'));
    await waitFor(() => expect(screen.getByRole('link', { name: 'Zona de trabajo' })).toHaveAttribute('aria-current', 'page'));
  });

  it('el administrador ve sus seis opciones', async () => {
    sesionComo(ADMIN);
    window.location.hash = '#/a/parametros';
    simularApi({ 'GET /admin/parameters': [] });
    pintar(<App />);
    await waitFor(() => expect(enlaces()).toEqual(['Resumen', 'Usuarios', 'Solicitudes', 'Verificación', 'Parámetros', 'Auditoría']));
    expect(screen.getByRole('link', { name: 'Parámetros' })).toHaveAttribute('aria-current', 'page');
  });

  it('Salir cierra la sesión desde el panel', async () => {
    sesionComo(ADMIN);
    simularApi({ 'GET /admin/summary': [404, vacio], 'POST /auth/logout': [204, undefined] });
    pintar(<App />);
    await userEvent.click(await screen.findByRole('button', { name: 'Salir' }));
    expect(await screen.findByLabelText('Correo')).toBeInTheDocument();
  });
});

describe('menuDe', () => {
  it('en el detalle de una solicitud deja marcada la opción que la contiene', () => {
    const detalle = { vista: 's', id: 4 };
    expect(menuDe('HOGAR', detalle)).toMatchObject({ base: 'h', activa: 'solicitudes' });
    expect(menuDe('TECNICO', detalle)).toMatchObject({ base: 't', activa: 'servicios' });
    expect(menuDe('ADMIN', detalle)).toMatchObject({ base: 'a', activa: 'solicitudes' });
  });

  it('una opción desconocida vuelve a la primera', () => {
    expect(menuDe('HOGAR', { vista: 'h', tab: 'nada' }).activa).toBe('nueva');
    expect(menuDe('ADMIN', { vista: 'a', tab: 'nada' }).activa).toBe('resumen');
  });
});
