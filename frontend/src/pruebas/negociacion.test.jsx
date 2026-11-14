import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Detalle from '../Detalle.jsx';
import Tecnico from '../Tecnico.jsx';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';

const HOGAR = { id: 1, rol: 'HOGAR', nombre: 'Ana Hogar' };
const TECNICO = { id: 2, rol: 'TECNICO', nombre: 'Tec Uno' };
const base = { id: 4, estado: 'E02', oficioId: 2, oficio: 'Electricista', descripcion: 'Cambiar un tomacorriente roto', hogarId: 1, expiraEn: '2099-01-01T00:00:00Z' };
const oferta = (id, autor, montoCop, secuencia, estado = 'PENDIENTE') => ({ id, autor, montoCop, secuencia, estado });
const postulacion = (ofertas, precioCop = 30000, estado = 'ACTIVA') => ({ id: 21, tecnicoId: 2, tecnico: 'Tec Uno', precioCop, estado, calificaciones: 0, ofertas });

describe('negociación del hogar', () => {
  it('envía una contraoferta con clave de idempotencia', async () => {
    sesionComo(HOGAR);
    const llamadas = simularApi({
      'GET /service-requests/4': base, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications': [postulacion([oferta(1, 'TECNICO', 30000, 1)])],
      'POST /applications/21/offers': [201, oferta(2, 'HOGAR', 25000, 2)],
    });
    pintar(<Detalle id={4} />);
    await userEvent.type(await screen.findByLabelText('Tu contraoferta (COP)'), '25000');
    await userEvent.click(screen.getByRole('button', { name: 'Contraofertar' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'POST' && l.ruta === '/applications/21/offers')).toBe(true));
    const l = llamadas.find((x) => x.ruta === '/applications/21/offers');
    expect(l.cuerpo).toEqual({ montoCop: 25000 });
    expect(l.cab['Idempotency-Key']).toMatch(/^[A-Za-z0-9_-]{8,80}$/);
  });

  it('con una contraoferta pendiente no deja elegir ni contraofertar otra vez', async () => {
    sesionComo(HOGAR);
    simularApi({
      'GET /service-requests/4': base, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications': [postulacion([oferta(1, 'TECNICO', 30000, 1), oferta(2, 'HOGAR', 25000, 2)])],
    });
    pintar(<Detalle id={4} />);
    expect(await screen.findByText(/Esperando la respuesta del técnico/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Elegir a este técnico' })).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Contraofertar' })).not.toBeInTheDocument();
  });
});

describe('negociación del técnico', () => {
  it('acepta la contraoferta del hogar', async () => {
    sesionComo(TECNICO);
    const llamadas = simularApi({
      'GET /service-requests/4': base, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications/mine': postulacion([oferta(1, 'TECNICO', 30000, 1), oferta(2, 'HOGAR', 25000, 2)]),
      'PATCH /offers/2/accept': oferta(2, 'HOGAR', 25000, 2, 'ACEPTADA'),
    });
    pintar(<Detalle id={4} />);
    await userEvent.click(await screen.findByRole('button', { name: /Aceptar/ }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'PATCH' && l.ruta === '/offers/2/accept')).toBe(true));
  });

  it('responde con su oferta final y puede retirar la postulación', async () => {
    sesionComo(TECNICO);
    const llamadas = simularApi({
      'GET /service-requests/4': base, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications/mine': postulacion([oferta(1, 'TECNICO', 30000, 1), oferta(2, 'HOGAR', 25000, 2)]),
      'POST /applications/21/offers': [201, oferta(3, 'TECNICO', 27000, 3)],
      'PATCH /applications/21/withdraw': { id: 21, estado: 'RETIRADA' },
    });
    pintar(<Detalle id={4} />);
    await userEvent.type(await screen.findByLabelText('Mi oferta final (COP)'), '27000');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar oferta final' }));
    await waitFor(() => expect(llamadas.find((l) => l.ruta === '/applications/21/offers')?.cuerpo).toEqual({ montoCop: 27000 }));
    await userEvent.click(screen.getByRole('button', { name: 'Retirar postulación' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Retirar' }));
    await waitFor(() => expect(llamadas.some((l) => l.metodo === 'PATCH' && l.ruta === '/applications/21/withdraw')).toBe(true));
  });

  it('sin contraoferta espera la decisión; con las dos ofertas usadas no ofrece otra', async () => {
    sesionComo(TECNICO);
    simularApi({
      'GET /service-requests/4': base, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications/mine': postulacion([oferta(1, 'TECNICO', 30000, 1)]),
    });
    pintar(<Detalle id={4} />);
    expect(await screen.findByText(/Esperando la decisión del hogar/)).toBeInTheDocument();
  });

  it('muestra el formulario de postulación cuando aún no se ha postulado', async () => {
    sesionComo(TECNICO);
    simularApi({ 'GET /service-requests/4': base, 'GET /service-requests/4/photos': [] });
    pintar(<Detalle id={4} />);
    expect(await screen.findByRole('button', { name: 'Postularme' })).toBeInTheDocument();
  });

  it('una postulación retirada o ya no activa se informa', async () => {
    sesionComo(TECNICO);
    simularApi({
      'GET /service-requests/4': base, 'GET /service-requests/4/photos': [],
      'GET /service-requests/4/applications/mine': postulacion([oferta(1, 'TECNICO', 30000, 1)], 30000, 'RETIRADA'),
    });
    pintar(<Detalle id={4} />);
    expect(await screen.findByText('Retiraste tu postulación.')).toBeInTheDocument();
  });
});

describe('disponibilidad del técnico', () => {
  it('cambia el estado con PATCH /technicians/me/availability', async () => {
    sesionComo(TECNICO);
    const llamadas = simularApi({
      'GET /technicians/me/availability': { estado: 'AVAILABLE' },
      'PATCH /technicians/me/availability': { estado: 'BUSY' },
      'GET /service-requests/open': [], 'GET /technicians/me/location': [404, { code: 'NOT_FOUND', message: 'x', traceId: 't' }],
    });
    pintar(<Tecnico tab="oportunidades" />);
    await userEvent.selectOptions(await screen.findByLabelText('Mi disponibilidad'), 'BUSY');
    await waitFor(() => expect(llamadas.find((l) => l.metodo === 'PATCH')?.cuerpo).toEqual({ estado: 'BUSY' }));
  });
});
