import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Admin from '../Admin.jsx';
import { pintar, sesionComo, simularApi } from './apoyo.jsx';

const ADMIN = { id: 9, rol: 'ADMIN', nombre: 'Admin' };
const P = (clave, descripcion, valor, defecto, unidad, minimo, maximo) => ({ clave, descripcion, valor, defecto, unidad, minimo, maximo, actualizadoEn: null });
const LISTA = [P('MIN_BALANCE', 'Saldo mínimo para postularse', 40000, 40000, 'COP', 0, 1000000), P('COMMISSION_PCT', 'Comisión sobre el precio acordado', 10, 10, '%', 1, 50)];

describe('parámetros del administrador', () => {
  it('lista los parámetros y guarda uno con PUT', async () => {
    sesionComo(ADMIN);
    const llamadas = simularApi({ 'GET /admin/parameters': LISTA, 'PUT /admin/parameters/MIN_BALANCE': { ...LISTA[0], valor: 30000 } });
    pintar(<Admin tab="parametros" />);
    const campo = await screen.findByLabelText('Saldo mínimo para postularse');
    const guardar = campo.closest('form').querySelector('button');
    expect(guardar).toBeDisabled(); // sin cambios no se puede guardar
    await userEvent.clear(campo);
    await userEvent.type(campo, '30000');
    await userEvent.click(guardar);
    await waitFor(() => expect(llamadas.find((l) => l.metodo === 'PUT')?.cuerpo).toEqual({ valor: 30000 }));
  });

  it('muestra el error del servidor cuando el valor no es válido', async () => {
    sesionComo(ADMIN);
    simularApi({ 'GET /admin/parameters': LISTA,
      'PUT /admin/parameters/COMMISSION_PCT': [400, { code: 'VALIDATION_ERROR', message: 'El valor debe estar entre 1 y 50 %', traceId: 't' }] });
    pintar(<Admin tab="parametros" />);
    const campo = await screen.findByLabelText('Comisión sobre el precio acordado');
    await userEvent.clear(campo);
    await userEvent.type(campo, '20');
    await userEvent.click(campo.closest('form').querySelector('button'));
    expect(await screen.findByRole('alert')).toHaveTextContent('entre 1 y 50');
  });
});
