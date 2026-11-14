import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Tecnico from '../Tecnico.jsx';
import Admin from '../Admin.jsx';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';

const TECNICO = { id: 2, rol: 'TECNICO', nombre: 'Tec Uno' };
const ADMIN = { id: 9, rol: 'ADMIN', nombre: 'Admin' };
const sinZona = [404, { code: 'NOT_FOUND', message: 'x', traceId: 't' }];

describe('verificación del técnico', () => {
  it('sin verificar, las oportunidades piden verificar la identidad', async () => {
    sesionComo(TECNICO);
    simularApi({ 'GET /technicians/me/availability': { estado: 'AVAILABLE' }, 'GET /service-requests/open': [], 'GET /technicians/me/location': sinZona,
      'GET /technicians/me/verification': { estado: 'SIN_ENVIAR' } });
    pintar(<Tecnico tab="oportunidades" />);
    expect(await screen.findByText(/Primero verifica tu identidad/)).toBeInTheDocument();
  });

  it('envía los tres documentos por multipart', async () => {
    sesionComo(TECNICO);
    const llamadas = simularApi({ 'GET /technicians/me/availability': { estado: 'AVAILABLE' },
      'GET /technicians/me/verification': { estado: 'RECHAZADA', motivo: 'Foto borrosa' },
      'POST /technicians/me/verification': { estado: 'PENDIENTE' } });
    pintar(<Tecnico tab="verificacion" />);
    expect(await screen.findByText(/Foto borrosa/)).toBeInTheDocument();
    const foto = (n) => new File(['x'], n, { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText('Cédula (lado frontal)'), foto('f.png'));
    await userEvent.upload(screen.getByLabelText('Cédula (lado posterior)'), foto('r.png'));
    await userEvent.upload(screen.getByLabelText('Selfie sosteniendo tu cédula'), foto('s.png'));
    await userEvent.click(screen.getByRole('button', { name: 'Enviar a revisión' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'POST' && l.ruta === '/technicians/me/verification'), JSON.stringify(llamadas.map((l) => l.metodo + l.ruta))).toBe(true));
    const form = llamadas.find((l) => l.metodo === 'POST').cuerpo;
    expect([...form.keys()].sort()).toEqual(['cedulaFrente', 'cedulaReverso', 'selfie']);
  });

  it('un técnico aprobado lo ve confirmado', async () => {
    sesionComo(TECNICO);
    simularApi({ 'GET /technicians/me/availability': { estado: 'AVAILABLE' }, 'GET /technicians/me/verification': { estado: 'APROBADA' } });
    pintar(<Tecnico tab="verificacion" />);
    expect(await screen.findByText('Identidad verificada')).toBeInTheDocument();
  });
});

describe('verificación desde el administrador', () => {
  const lista = { items: [{ id: 2, nombre: 'Tec Uno', correo: 'tec@correo.co', verificacion: 'PENDIENTE', enviadaEn: '2026-11-13T12:00:00Z' }], pagina: 0, tamano: 50, total: 1 };

  it('aprueba a un técnico en revisión', async () => {
    sesionComo(ADMIN);
    const llamadas = simularApi({ 'GET /admin/technicians': lista, 'PATCH /admin/technicians/2/verification': { estado: 'APROBADA' } });
    pintar(<Admin tab="tecnicos" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Aprobar' }));
    await waitFor(() => expect(llamadas.find((l) => l.metodo === 'PATCH')?.cuerpo).toEqual({ decision: 'APPROVED', motivo: null }));
  });

  it('rechaza con motivo', async () => {
    sesionComo(ADMIN);
    const llamadas = simularApi({ 'GET /admin/technicians': lista, 'PATCH /admin/technicians/2/verification': { estado: 'RECHAZADA' } });
    pintar(<Admin tab="tecnicos" />);
    await userEvent.click(await screen.findByRole('button', { name: 'Rechazar' }));
    await userEvent.type(await screen.findByLabelText('Motivo'), 'Foto borrosa');
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Rechazar' }));
    await waitFor(() => expect(llamadas.find((l) => l.metodo === 'PATCH')?.cuerpo).toEqual({ decision: 'REJECTED', motivo: 'Foto borrosa' }));
  });

  it('muestra un aviso cuando no hay nadie esperando', async () => {
    sesionComo(ADMIN);
    simularApi({ 'GET /admin/technicians': { items: [], pagina: 0, tamano: 50, total: 0 } });
    pintar(<Admin tab="tecnicos" />);
    expect(await screen.findByText('No hay técnicos esperando verificación.')).toBeInTheDocument();
  });
});
