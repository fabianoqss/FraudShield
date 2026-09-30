import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { beforeEach, expect, it, vi } from "vitest";
import { api, ApiError } from "../api/client";
import { AuthPage, PasswordPage } from "./Auth";

beforeEach(() => {
  api.clear();
  vi.spyOn(api, "bootstrap").mockResolvedValue();
});
it("shows the 15-minute lockout message after HTTP 429", async () => {
  vi.spyOn(api, "login").mockRejectedValue(
    new ApiError(429, "Too many attempts"),
  );
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <AuthPage />
    </MemoryRouter>,
  );
  await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
  await user.type(screen.getByLabelText("Senha"), "WrongPassword!");
  await user.click(screen.getByRole("button", { name: "Entrar" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Aguarde 15 minutos",
  );
});
it("associates server validation errors with registration fields", async () => {
  vi.spyOn(api, "request").mockRejectedValue(
    new ApiError(400, "Dados inválidos", { cpf: "CPF inválido" }),
  );
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <AuthPage register />
    </MemoryRouter>,
  );
  await user.type(screen.getByLabelText("Nome completo"), "Ana Souza");
  await user.type(screen.getByLabelText("E-mail"), "ana@example.com");
  await user.type(screen.getByLabelText("CPF"), "11111111111");
  await user.type(screen.getByLabelText("Data de nascimento"), "1990-05-20");
  await user.type(screen.getByLabelText("Senha"), "Senha@123");
  await user.click(screen.getByRole("button", { name: "Cadastrar" }));
  expect(await screen.findByText("CPF inválido")).toBeVisible();
  expect(screen.getByLabelText("CPF")).toHaveAccessibleDescription(
    "CPF inválido",
  );
  expect(screen.getByLabelText("CPF")).toHaveAttribute("aria-invalid", "true");
});
it("logs out only after the server confirms a password change", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValue(undefined);
  const logout = vi.spyOn(api, "logout").mockResolvedValue();
  const user = userEvent.setup();
  render(<PasswordPage />);
  await user.type(screen.getByLabelText("Senha atual"), "Senha@123");
  await user.type(screen.getByLabelText("Nova senha"), "NovaSenha@123");
  await user.type(
    screen.getByLabelText("Confirme a nova senha"),
    "NovaSenha@123",
  );
  await user.click(screen.getByRole("button", { name: "Alterar senha" }));
  expect(request).toHaveBeenCalledWith(
    "/auth/password",
    expect.objectContaining({ method: "PUT" }),
  );
  expect(logout).toHaveBeenCalledOnce();
  expect(request.mock.invocationCallOrder[0]).toBeLessThan(
    logout.mock.invocationCallOrder[0] ?? 0,
  );
});
