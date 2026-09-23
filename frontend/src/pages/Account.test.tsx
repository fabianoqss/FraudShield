import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { expect, it, vi } from 'vitest';
import { api, ApiError } from '../api/client';
import { DepositPage } from './Account';

it('shows a PIX-specific message when the deposit key is not found', async () => {
  vi.spyOn(api, 'request').mockRejectedValue(new ApiError(404, 'PIX key not found'));
  const user = userEvent.setup();
  render(<MemoryRouter><DepositPage /></MemoryRouter>);

  await user.type(screen.getByLabelText('Chave PIX (e-mail ou CPF)'), 'missing@example.com');
  await user.type(screen.getByLabelText('Valor (R$)'), '10,00');
  await user.click(screen.getByRole('button', { name: 'Depositar' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Chave PIX não encontrada.');
});
