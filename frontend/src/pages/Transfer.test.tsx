import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, expect, it, vi } from 'vitest';
import { api, ApiError, NetworkError } from '../api/client';
import { TransferForm } from './Transfer';
const destination = '22222222-2222-4222-8222-222222222222';
beforeEach(() => window.localStorage.clear());
async function fillAndSubmit() {
  const user = userEvent.setup();
  render(<MemoryRouter><TransferForm accountId="11111111-1111-4111-8111-111111111111" /></MemoryRouter>);
  await user.type(screen.getByLabelText('Identificador da conta de destino'), destination);
  await user.type(screen.getByLabelText('Valor (R$)'), '100,50');
  await user.click(screen.getByRole('button', { name: 'Enviar transferência' }));
  return user;
}
it.each([[422, /saldo insuficiente/], [409, /já foi registrado/]] as const)('shows an actionable message for HTTP %s', async (status, message) => {
  vi.spyOn(api, 'request').mockRejectedValue(new ApiError(status, 'Rejected'));
  await fillAndSubmit();
  expect(await screen.findByRole('alert')).toHaveTextContent(message);
});
it('reuses the same payload and key only after an uncertain network response', async () => {
  const request = vi.spyOn(api, 'request').mockRejectedValue(new NetworkError());
  const user = await fillAndSubmit();
  expect(screen.getByLabelText('Valor (R$)')).toBeDisabled();
  await user.click(screen.getByRole('button', { name: 'Repetir o mesmo envio' }));
  expect(request).toHaveBeenCalledTimes(2);
  expect(request.mock.calls[0]?.[1]?.body).toEqual(request.mock.calls[1]?.[1]?.body);
  expect(request.mock.calls[0]?.[1]?.body).toMatchObject({ amount: 100.5, ipAddress: null, destinationAccountId: destination, deviceId: window.localStorage.getItem('fraudshield.deviceId') });
});
it('uses a new key for a new submission after a definitive rejection', async () => {
  const request = vi.spyOn(api, 'request').mockRejectedValue(new ApiError(422, 'Insufficient funds'));
  const user = await fillAndSubmit();
  await user.click(screen.getByRole('button', { name: 'Enviar transferência' }));
  const first = request.mock.calls[0]?.[1]?.body as { idempotencyKey: string };
  const second = request.mock.calls[1]?.[1]?.body as { idempotencyKey: string };
  expect(first.idempotencyKey).not.toBe(second.idempotencyKey);
});
it('discards an uncertain submission and creates a fresh key for the next transfer', async () => {
  const request = vi.spyOn(api, 'request').mockRejectedValueOnce(new NetworkError()).mockRejectedValueOnce(new ApiError(422, 'Insufficient funds'));
  const user = await fillAndSubmit();
  expect(screen.getByLabelText('Valor (R$)')).toBeDisabled();

  await user.click(screen.getByRole('button', { name: 'Descartar e fazer nova transferência' }));

  expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Repetir o mesmo envio' })).not.toBeInTheDocument();
  expect(screen.getByLabelText('Valor (R$)')).toBeEnabled();
  expect(request).toHaveBeenCalledTimes(1);
  await user.clear(screen.getByLabelText('Valor (R$)'));
  await user.type(screen.getByLabelText('Valor (R$)'), '20,00');
  await user.click(screen.getByRole('button', { name: 'Enviar transferência' }));

  const first = request.mock.calls[0]?.[1]?.body as { idempotencyKey: string };
  const second = request.mock.calls[1]?.[1]?.body as { idempotencyKey: string; amount: number };
  expect(second.idempotencyKey).not.toBe(first.idempotencyKey);
  expect(second.amount).toBe(20);
});
