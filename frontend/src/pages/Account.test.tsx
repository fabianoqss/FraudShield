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
    screen.getByLabelText("Pix key (email, CPF, or random key)"),
    "missing@example.com",
  );
  await user.type(screen.getByLabelText("Amount (R$)"), "10,00");
  await user.click(screen.getByRole("button", { name: "Deposit" }));

  expect(await screen.findByRole("alert")).toHaveTextContent(
    "This Pix key isn't registered.",
  );
});

it("preserves random keys in the deposit body", async () => {
  const request = vi.spyOn(api, "request").mockResolvedValue({ receiverName: "Ana", amount: 10 });
  const user = userEvent.setup();
  render(<MemoryRouter><DepositPage /></MemoryRouter>);
  const key = "22222222-2222-4222-8222-222222222222";
  await user.type(screen.getByLabelText("Pix key (email, CPF, or random key)"), key);
  await user.type(screen.getByLabelText("Amount (R$)"), "10");
  await user.click(screen.getByRole("button", { name: "Deposit" }));
  expect(request).toHaveBeenCalledWith("/accounts/deposit", { method: "POST", public: true, body: { pixKey: key, amount: 10 } });
});

it("shows an invalid key format next to the deposit field", async () => {
  vi.spyOn(api, "request").mockRejectedValue(new ApiError(400, "Invalid PIX key format"));
  const user = userEvent.setup();
  render(<MemoryRouter><DepositPage /></MemoryRouter>);
  await user.type(screen.getByLabelText("Pix key (email, CPF, or random key)"), "invalid");
  await user.type(screen.getByLabelText("Amount (R$)"), "10");
  await user.click(screen.getByRole("button", { name: "Deposit" }));
  expect(screen.getByLabelText("Pix key (email, CPF, or random key)")).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByText("Invalid Pix key format.")).toBeInTheDocument();
});
