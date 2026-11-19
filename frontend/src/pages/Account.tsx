import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { api, ApiError } from "../api/client";
import type { Balance } from "../api/types";
import { currency } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import {
  ErrorNotice,
  Field,
  fieldErrors,
  formValues,
  parseAmount,
} from "../components/Forms";

export function AccountPage() {
  const { account, reload } = useAccounts();
  const [balance, setBalance] = useState<Balance>();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!account) return;
    const controller = new AbortController();
    setError(undefined);
    setBalance(undefined);
    api
      .request<Balance>(`/accounts/${account.id}/balance`, {
        signal: controller.signal,
      })
      .then(setBalance)
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause);
      });
    return () => controller.abort();
  }, [account, revision]);
  async function create() {
    const user = api.snapshot().user;
    if (!user || busy) return;
    setBusy(true);
    setError(undefined);
    try {
      await api.request("/accounts", {
        method: "POST",
        body: { ownerId: user.sub, ownerName: user.fullName },
      });
      await reload();
    } catch (cause) {
      setError(cause);
    } finally {
      setBusy(false);
    }
  }
  async function copy() {
    try {
      await navigator.clipboard.writeText(account?.id ?? "");
      setCopied(true);
    } catch {
      setError(
        new Error(
          "Couldn't copy the account ID. Select it and copy it manually.",
        ),
      );
    }
  }
  return (
    <section>
      <h1>My account</h1>
      <ErrorNotice error={error} />
      {!account ? (
        <>
          <p>You don't have an account yet.</p>
          <button disabled={busy} onClick={() => void create()}>
            {busy ? "Creating…" : "Create account"}
          </button>
        </>
      ) : (
        <>
          <p>{account.ownerName}</p>
          <p>Account ID:</p>
          <code className="account-id">{account.id}</code>
          <button className="secondary" onClick={() => void copy()}>
            Copy account ID
          </button>
          {copied && <p role="status">Account ID copied.</p>}
          <p>To receive transfers, register a key under <Link to="/pix-keys">My Pix keys</Link>.</p>
          {balance ? (
            <dl className="balances">
              <div>
                <dt>Total balance</dt>
                <dd>{currency(balance.balance)}</dd>
              </div>
              <div>
                <dt>Locked balance</dt>
                <dd>{currency(balance.lockedBalance)}</dd>
              </div>
              <div>
                <dt>Available balance</dt>
                <dd>{currency(balance.availableBalance)}</dd>
              </div>
            </dl>
          ) : (
            !error && <p role="status">Loading balance…</p>
          )}
          <button
            className="secondary"
            onClick={() => setRevision((value) => value + 1)}
          >
            Refresh balance
          </button>
          <p className="hint">
            Funds may stay on hold while a transfer is under review.
          </p>
        </>
      )}
    </section>
  );
}

export function DepositPage() {
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const fields = fieldErrors(error);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const values = formValues(form);
    setError(undefined);
    setMessage("");
    setBusy(true);
    try {
      const rawKey = values.pixKey?.trim() ?? "";
      const result = await api.request<{
        receiverName: string;
        amount: number;
      }>("/accounts/deposit", {
        method: "POST",
        public: true,
        body: {
          pixKey: rawKey,
          amount: parseAmount(values.amount ?? ""),
        },
      });
      setMessage(
        `Deposited ${currency(result.amount)} to ${result.receiverName}.`,
      );
      form.reset();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 400
        ? new ApiError(400, "Invalid format. Check the Pix key and amount.", { ...cause.fieldErrors, pixKey: cause.fieldErrors.pixKey ?? (Object.keys(cause.fieldErrors).length ? "" : "Invalid Pix key format.") })
        : cause);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section>
      <h1>Simulated Pix deposit</h1>
      <p>
        Enter a registered Pix key (email, CPF, or random key). No real money will move.
      </p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <Field
            name="pixKey"
            label="Pix key (email, CPF, or random key)"
            required
            error={fields.pixKey}
          />
          <Field
            name="amount"
            label="Amount (R$)"
            inputMode="decimal"
            placeholder="150.00"
            required
            error={fields.amount}
          />
          <button>{busy ? "Depositing…" : "Deposit"}</button>
        </fieldset>
        <ErrorNotice
          error={error}
          notFoundMessage="This Pix key isn't registered."
        />
      </form>
      {message && (
        <p role="status">
          {message} <Link to="/">View balance</Link>
        </p>
      )}
    </section>
  );
}
