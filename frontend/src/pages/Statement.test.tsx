import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { expect, it, vi } from "vitest";
import { api } from "../api/client";
import { TransactionPage } from "./Statement";

vi.mock("../auth/Accounts", () => ({
  useAccounts: () => ({ account: { id: "account" } }),
}));
it.each([
  ["OUTGOING", "Saída", "Risk detected"],
  ["INCOMING", "Entrada", undefined],
])("renders %s events beyond the first page without internal signals", async (direction, label, reason) => {
  vi.spyOn(api, "request").mockImplementation(async (path) => {
    if (path === "/transactions/selected")
      return {
        id: "selected",
        sourceAccountId: "account",
        destinationAccountId: "destination",
        amount: 12,
        type: "PIX",
        status: "DENIED",
        createdAt: "2026-09-23T12:00:00Z",
      };
    if (path.includes("page=0"))
      return {
        entries: [
          {
            id: "unrelated",
            transactionId: "another",
            eventType: "TRANSACTION_DENIED",
            direction: "OUTGOING", amount: 12, reason: "Must not appear",
            recordedAt: "2026-09-23T12:00:00Z",
          },
        ],
        totalPages: 2,
      };
    return {
      entries: [
        {
          id: "outcome",
          transactionId: "selected",
          eventType: "TRANSACTION_DENIED",
          direction, amount: 12, reason,
          recordedAt: "2026-09-23T12:00:00Z",
        },
      ],
      totalPages: 2,
    };
  });
  const user = userEvent.setup();
  render(
    <MemoryRouter initialEntries={["/extrato/selected"]}>
      <Routes>
        <Route path="/extrato/:id" element={<TransactionPage />} />
      </Routes>
    </MemoryRouter>,
  );
  const next = await screen.findByRole("button", {
    name: "Buscar nas próximas páginas",
  });
  expect(screen.queryByText(/Must not appear/)).not.toBeInTheDocument();
  await user.click(next);
  expect(await screen.findByText(new RegExp(`${label} · R\\$\\s*12,00`))).toBeInTheDocument();
  if (reason) {
    expect(screen.getByText(`Motivo: ${reason}`)).toBeInTheDocument();
  } else {
    expect(screen.queryByText(/Motivo:/)).not.toBeInTheDocument();
  }
  expect(screen.queryByText(/Pontuação de fraude/)).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Buscar nas próximas páginas" }),
  ).not.toBeInTheDocument();
});
