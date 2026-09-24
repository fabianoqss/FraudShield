import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router";
import { expect, it, vi } from "vitest";
import { api } from "../api/client";
import { TransactionPage } from "./Statement";

vi.mock("../auth/Accounts", () => ({
  useAccounts: () => ({ account: { id: "account" } }),
}));
it("finds the fraud score beyond the first ledger page and excludes other transactions", async () => {
  vi.spyOn(api, "request").mockImplementation(async (path) => {
    if (path === "/transactions/selected")
      return {
        id: "selected",
        sourceAccountId: "account",
        destinationAccountId: "destination",
        amount: 12,
        type: "PIX",
        status: "APPROVED",
        createdAt: "2026-09-23T12:00:00Z",
      };
    if (path.includes("page=0"))
      return {
        entries: [
          {
            id: "unrelated",
            transactionId: "another",
            eventType: "TRANSACTION_DENIED",
            eventPayload: { reason: "Must not appear" },
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
          eventType: "TRANSACTION_APPROVED",
          eventPayload: { fraudScore: 0.02 },
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
  expect(
    await screen.findByText("Pontuação de fraude: 0,02"),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Buscar nas próximas páginas" }),
  ).not.toBeInTheDocument();
});
