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
  expect(screen.getByRole("button", { name: "Register CPF" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Register email" })).toBeDisabled();
  expect(screen.getByRole("button", { name: "Register a random key" })).toBeEnabled();
  await user.click(screen.getByRole("button", { name: `Copy ${key("RANDOM").value}` }));
  expect(copy).toHaveBeenCalledWith(key("RANDOM").value);
});
it.each([["CPF", "Register CPF"], ["EMAIL", "Register email"], ["RANDOM", "Register a random key"]] as const)("registers %s sending only type", async (type, label) => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce([]).mockResolvedValueOnce(key(type));
  const user = userEvent.setup(); show();
  await user.click(await screen.findByRole("button", { name: label }));
  expect(request.mock.calls[1]).toEqual(["/accounts/source/pix-keys", { method: "POST", body: { type } }]);
  expect(screen.getByText("1 of 5 keys registered.")).toBeInTheDocument();
});
it.each([[409, "Duplicate", "This Pix key is already registered."], [422, "Account is not active", "Account is not active"], [503, "Unavailable", "temporarily unavailable"]])("handles HTTP %s registration errors", async (status, backend, message) => {
  vi.spyOn(api, "request").mockResolvedValueOnce([]).mockRejectedValueOnce(new ApiError(Number(status), String(backend)));
  const user = userEvent.setup(); show();
  await user.click(await screen.findByRole("button", { name: "Register CPF" }));
  expect(screen.getByRole("alert")).toHaveTextContent(String(message));
});
it("disables all registrations at five keys", async () => {
  vi.spyOn(api, "request").mockResolvedValue(Array.from({ length: 5 }, (_, i) => key("RANDOM", String(i))));
  show();
  await screen.findByText("You've reached the 5-key limit.");
  for (const button of screen.getAllByRole("button", { name: /Register/ })) expect(button).toBeDisabled();
});
it("requires confirmation before deleting and enables the removed type", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValueOnce([key()]).mockResolvedValueOnce(undefined);
  const user = userEvent.setup(); show();
  await user.click(await screen.findByRole("button", { name: "Remove 529.982.247-25" }));
  expect(request).toHaveBeenCalledTimes(1);
  await user.click(within(screen.getByRole("group", { name: "Confirm removal" })).getByRole("button", { name: "Confirm removal" }));
  expect(request.mock.calls[1]).toEqual(["/accounts/source/pix-keys/key-1", { method: "DELETE" }]);
  expect(screen.getByText("This account has no registered Pix keys.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Register CPF" })).toBeEnabled();
});
