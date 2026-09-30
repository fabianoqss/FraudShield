import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { expect, it, vi } from "vitest";
import { api, ApiError } from "../api/client";
import { DepositPage } from "./Account";

it("shows a PIX-specific message when the deposit key is not found", async () => {
  vi.spyOn(api, "request").mockRejectedValue(
    new ApiError(404, "PIX key not found"),
  );
  const user = userEvent.setup();
  render(
    <MemoryRouter>
      <DepositPage />
    </MemoryRouter>,
  );

  await user.type(
    screen.getByLabelText("Chave PIX (e-mail, CPF ou aleatória)"),
    "missing@example.com",
  );
  await user.type(screen.getByLabelText("Valor (R$)"), "10,00");
  await user.click(screen.getByRole("button", { name: "Depositar" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Chave PIX não cadastrada.",
  );
});

it("preserves random keys in the deposit body", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValue({ receiverName: "Ana", amount: 10 });
  const user = userEvent.setup();
  render(<MemoryRouter><DepositPage /></MemoryRouter>);
  const key = "22222222-2222-4222-8222-222222222222";
  await user.type(screen.getByLabelText("Chave PIX (e-mail, CPF ou aleatória)"), key);
  await user.type(screen.getByLabelText("Valor (R$)"), "10");
  await user.click(screen.getByRole("button", { name: "Depositar" }));
  expect(request).toHaveBeenCalledWith("/accounts/deposit", { method: "POST", public: true, body: { pixKey: key, amount: 10 } });
});

it("shows an invalid key format next to the deposit field", async () => {
  vi.spyOn(api, "request").mockRejectedValue(new ApiError(400, "Invalid PIX key format"));
  const user = userEvent.setup();
  render(<MemoryRouter><DepositPage /></MemoryRouter>);
  await user.type(screen.getByLabelText("Chave PIX (e-mail, CPF ou aleatória)"), "invalid");
  await user.type(screen.getByLabelText("Valor (R$)"), "10");
  await user.click(screen.getByRole("button", { name: "Depositar" }));
  expect(screen.getByLabelText("Chave PIX (e-mail, CPF ou aleatória)")).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByText("Formato de chave PIX inválido.")).toBeInTheDocument();
});
