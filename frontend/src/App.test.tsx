import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { expect, it } from 'vitest';
import { App } from './App';

it('allows users to recover from an unknown URL', async () => {
  const user = userEvent.setup();
  render(<MemoryRouter initialEntries={['/unknown']}><App /></MemoryRouter>);

  expect(screen.getByRole('heading', { name: 'Página não encontrada' })).toBeInTheDocument();
  await user.click(screen.getByRole('link', { name: 'Voltar ao início' }));
  expect(screen.getByRole('heading', { name: 'Bem-vindo ao FraudShield' })).toBeInTheDocument();
});
