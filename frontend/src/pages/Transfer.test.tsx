import { act, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, ApiError, NetworkError } from "../api/client";
import { TransferForm } from "./Transfer";
const lookup = () => ({ lookupId: "lookup-1", recipientName: "Ana Souza", maskedCpf: "***.982.247-**", keyType: "EMAIL", expiresAt: new Date(Date.now() + 300000).toISOString() });
beforeEach(() => window.localStorage.clear());
afterEach(() => vi.useRealTimers());
function form() {
  render(<MemoryRouter><TransferForm accountId="source" /></MemoryRouter>);
  fireEvent.change(screen.getByLabelText("Chave PIX"), { target: { value: "ana@example.com" } });
  fireEvent.change(screen.getByLabelText("Valor (R$)"), { target: { value: "100,50" } });
}
async function consult() {
  form();
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Consultar chave" }));
  return user;
}
async function confirm() {
  const user = await consult();
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
  return user;
}
it("looks up in the body, confirms recipient and sends only lookupId for the destination", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockResolvedValueOnce({ id: "tx", status: "APPROVED" });
  const user = await consult();
  expect(request).toHaveBeenCalledTimes(1);
  expect(request.mock.calls[0]).toEqual(["/accounts/pix-keys/lookup", { method: "POST", body: { key: "ana@example.com" } }]);
  expect(screen.getByText(/Você está enviando/)).toHaveTextContent(/100,50 para Ana Souza \(CPF \*\*\*.982.247-\*\*\)/);
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
  expect(request.mock.calls[1]).toEqual(["/transactions", { method: "POST", body: {
    sourceAccountId: "source", lookupId: "lookup-1", amount: 100.5, type: "PIX", deviceId: expect.any(String), idempotencyKey: expect.any(String),
  } }]);
  expect(await screen.findByText("APROVADA")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Ver detalhes" })).toHaveAttribute("href", "/extrato/tx");
});
it.each([[404, "Chave PIX não encontrada"], [503, "indisponível"], [400, "Formato de chave PIX inválido"]])("handles lookup HTTP %s", async (status, message) => {
  vi.spyOn(api, "request").mockRejectedValue(new ApiError(Number(status), "Rejected"));
  await consult();
  expect(screen.getByRole("alert")).toHaveTextContent(String(message));
  if (status === 400) expect(screen.getByLabelText("Chave PIX")).toHaveAttribute("aria-invalid", "true");
});
it("waits the Retry-After seconds before allowing another lookup", async () => {
  vi.useFakeTimers();
  vi.spyOn(api, "request").mockRejectedValue(new ApiError(429, "Too many", {}, "3"));
  form();
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Consultar chave" })));
  expect(screen.getByRole("status")).toHaveTextContent("3 segundos");
  expect(screen.getByRole("button", { name: "Consultar chave" })).toBeDisabled();
  await act(async () => vi.advanceTimersByTime(3000));
  expect(screen.getByRole("button", { name: "Consultar chave" })).toBeEnabled();
});
it("returns to lookup with preserved fields on an expired lookup rejection", async () => {
  vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockRejectedValueOnce(new ApiError(422, "PIX key lookup expired or invalid, look up the key again"));
  await confirm();
  expect(screen.getByRole("alert")).toHaveTextContent("Consulta expirada ou inválida");
  expect(screen.getByLabelText("Chave PIX")).toHaveValue("ana@example.com");
  expect(screen.getByLabelText("Valor (R$)")).toHaveValue("100,50");
});
it("expires the confirmation locally without sending a transfer", async () => {
  vi.useFakeTimers();
  const request = vi.spyOn(api, "request").mockResolvedValue(lookup());
  form();
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Consultar chave" })));
  expect(screen.getByRole("heading", { name: "Confirme" })).toBeInTheDocument();
  await act(async () => vi.advanceTimersByTime(300000));
  expect(screen.getByLabelText("Valor (R$)")).toHaveValue("100,50");
  expect(screen.queryByRole("button", { name: "Confirmar" })).not.toBeInTheDocument();
  expect(request).toHaveBeenCalledTimes(1);
});
it.each([[422, "Insufficient funds", "Saldo insuficiente"], [422, "Cannot transfer to the same account", "mesma conta"], [409, "Used", "já foi registrado"]])("handles transfer HTTP %s: %s", async (status, backend, message) => {
  vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockRejectedValue(new ApiError(Number(status), String(backend)));
  await confirm();
  expect(screen.getByRole("alert")).toHaveTextContent(String(message));
});
it("reuses exactly the same payload only for uncertain retries", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockRejectedValue(new NetworkError());
  const user = await confirm();
  expect(screen.getByRole("button", { name: "Confirmar" })).toBeDisabled();
  await user.click(screen.getByRole("button", { name: "Repetir o mesmo envio" }));
  expect(request.mock.calls[1]?.[1]?.body).toEqual(request.mock.calls[2]?.[1]?.body);
  expect(request.mock.calls[1]?.[1]?.body).toMatchObject({ deviceId: localStorage.getItem("fraudshield.deviceId") });
});
it("uses a new idempotency key after a definitive rejection", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockRejectedValue(new ApiError(422, "Insufficient funds"));
  const user = await confirm();
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
  const first = request.mock.calls[1]?.[1]?.body as { idempotencyKey: string };
  const second = request.mock.calls[2]?.[1]?.body as { idempotencyKey: string };
  expect(first.idempotencyKey).not.toBe(second.idempotencyKey);
});
it("discards uncertain submissions and looks up again for a fresh transfer", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockRejectedValueOnce(new NetworkError()).mockResolvedValueOnce(lookup()).mockRejectedValueOnce(new ApiError(422, "Insufficient funds"));
  const user = await confirm();
  await user.click(screen.getByRole("button", { name: "Descartar e fazer nova transferência" }));
  await user.clear(screen.getByLabelText("Valor (R$)"));
  await user.type(screen.getByLabelText("Valor (R$)"), "20,00");
  await user.click(screen.getByRole("button", { name: "Consultar chave" }));
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
  const first = request.mock.calls[1]?.[1]?.body as { idempotencyKey: string };
  const second = request.mock.calls[3]?.[1]?.body as { idempotencyKey: string; amount: number };
  expect(first.idempotencyKey).not.toBe(second.idempotencyKey);
  expect(second.amount).toBe(20);
});

it.each(["CREDIT", "DEBIT"])("uses lookupId for %s too, preserving type when going back", async (type) => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockResolvedValueOnce(lookup()).mockResolvedValueOnce({ id: "tx", status: "APPROVED" });
  const user = await consult();
  await user.click(screen.getByRole("button", { name: "Voltar" }));
  expect(screen.getByLabelText("Chave PIX")).toHaveValue("ana@example.com");
  await user.selectOptions(screen.getByLabelText("Tipo"), type);
  await user.click(screen.getByRole("button", { name: "Consultar chave" }));
  await user.click(screen.getByRole("button", { name: "Confirmar" }));
  expect(request.mock.calls[2]?.[1]?.body).toMatchObject({ lookupId: "lookup-1", type });
  expect(request.mock.calls[2]?.[1]?.body).not.toHaveProperty("destinationAccountId");
});
it("continues polling a created transfer until its outcome", async () => {
  vi.useFakeTimers();
  const request = vi.spyOn(api, "request").mockResolvedValueOnce(lookup()).mockResolvedValueOnce({ id: "tx", status: "CREATED" }).mockResolvedValueOnce({ id: "tx", status: "APPROVED" });
  form();
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Consultar chave" })));
  await act(async () => fireEvent.click(screen.getByRole("button", { name: "Confirmar" })));
  expect(screen.getByText("Em análise")).toBeInTheDocument();
  await act(async () => vi.advanceTimersByTime(1500));
  expect(request.mock.calls[2]?.[0]).toBe("/transactions/tx");
  expect(screen.getByText("APROVADA")).toBeInTheDocument();
});
