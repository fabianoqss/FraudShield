import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import { expect, it, vi } from "vitest";
import { api, ApiError } from "../api/client";
import type { PixKey, PixKeyType } from "../api/types";
import { PixKeys } from "./PixKeys";
const key = (type: PixKeyType = "CPF", id = "key-1"): PixKey => ({ id, type, value: type === "CPF" ? "52998224725" : type === "EMAIL" ? "ana@example.com" : "22222222-2222-4222-8222-222222222222", accountId: "source", createdAt: "2026-09-24T10:00:00Z" });
function show() { render(<MemoryRouter><PixKeys accountId="source" /></MemoryRouter>); }
it("lists formatted keys, copies the full random key and disables existing CPF/email types", async () => {
  const user = userEvent.setup();
  vi.spyOn(api, "request").mockResolvedValue([key(), key("EMAIL", "email"), key("RANDOM", "random")]);
  const copy = vi.spyOn(navigator.clipboard, "writeText");
  show();
  expect(await screen.findByText("529.982.247-25")).toBeInTheDocument();
  expect(screen.getByText("ana@example.com")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Cadastrar CPF" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Cadastrar E-mail" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Cadastrar Chave aleatória" })).toBeEnabled();
  await user.click(screen.getByRole("button", { name: `Copiar ${key("RANDOM").value}` }));
  expect(copy).toHaveBeenCalledWith(key("RANDOM").value);
});
it.each([["CPF", "CPF"], ["EMAIL", "E-mail"], ["RANDOM", "Chave aleatória"]] as const)("registers %s sending only type", async (type, label) => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce([]).mockResolvedValueOnce(key(type));
  const user = userEvent.setup(); show();
  await user.click(await screen.findByRole("button", { name: `Cadastrar ${label}` }));
  expect(request.mock.calls[1]).toEqual(["/accounts/source/pix-keys", { method: "POST", body: { type } }]);
  expect(screen.getByText("1 de 5 chaves cadastradas.")).toBeInTheDocument();
});
it.each([[409, "Duplicate", "Esta chave já está cadastrada"], [422, "Account is not active", "Account is not active"], [503, "Unavailable", "indisponível"]])("handles HTTP %s registration errors", async (status, backend, message) => {
  vi.spyOn(api, "request").mockResolvedValueOnce([]).mockRejectedValueOnce(new ApiError(Number(status), String(backend)));
  const user = userEvent.setup(); show();
  await user.click(await screen.findByRole("button", { name: "Cadastrar CPF" }));
  expect(screen.getByRole("alert")).toHaveTextContent(String(message));
});
it("disables all registrations at five keys", async () => {
  vi.spyOn(api, "request").mockResolvedValue(Array.from({ length: 5 }, (_, i) => key("RANDOM", String(i))));
  show();
  await screen.findByText("Limite de 5 chaves atingido.");
  for (const button of screen.getAllByRole("button", { name: /Cadastrar/ })) expect(button).toBeDisabled();
});
it("requires confirmation before deleting and enables the removed type", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce([key()]).mockResolvedValueOnce(undefined);
  const user = userEvent.setup(); show();
  await user.click(await screen.findByRole("button", { name: "Remover 529.982.247-25" }));
  expect(request).toHaveBeenCalledTimes(1);
  await user.click(within(screen.getByRole("group", { name: "Confirmar remoção" })).getByRole("button", { name: "Confirmar remoção" }));
  expect(request.mock.calls[1]).toEqual(["/accounts/source/pix-keys/key-1", { method: "DELETE" }]);
  expect(screen.getByText("Nenhuma chave cadastrada nesta conta.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Cadastrar CPF" })).toBeEnabled();
});
