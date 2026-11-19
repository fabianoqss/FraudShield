import { ArrowDownToLine, Banknote, Copy, KeyRound, Plus, RefreshCw, Wallet } from "lucide-react";
import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { api, ApiError } from "../api/client";
import type { Balance } from "../api/types";
import { currency } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import {
  Button,
  Card,
  ErrorNotice,
  Field,
  Notice,
  PageHeading,
  fieldErrors,
  formValues,
  insetSurface,
  parseAmount,
  textLink,
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
    <Card className="space-y-6">
      <PageHeading icon={Wallet}>My account</PageHeading>
      <ErrorNotice error={error} />
      {!account ? (
        <div className={`${insetSurface} space-y-4 p-4 sm:p-5`}>
          <p className="text-sm text-slate-300">You don't have an account yet.</p>
          <Button busy={busy} icon={Plus} onClick={() => void create()}>
            {busy ? "Creating…" : "Create account"}
          </Button>
        </div>
      ) : (
        <>
          {balance ? (
            <dl className="grid gap-3 md:grid-cols-3">
              {[
                { label: "Total balance", value: balance.balance },
                { label: "Locked balance", value: balance.lockedBalance },
                { label: "Available balance", value: balance.availableBalance, available: true },
              ].map(({ label, value, available }) => (
                <div className={`${insetSurface} p-4 sm:p-5 ${available ? "ring-1 ring-emerald-400/30" : ""}`} key={label}>
                  <dt className="text-sm font-medium text-slate-400">{label}</dt>
                  <dd className={`mt-2 text-2xl font-bold tracking-tight tabular-nums [overflow-wrap:anywhere] ${available ? "text-emerald-300" : "text-slate-100"}`}>
                    {currency(value)}
                  </dd>
                </div>
              ))}
            </dl>
          ) : (
            !error && <Notice>Loading balance…</Notice>
          )}
          <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
            <Button variant="secondary" icon={RefreshCw} onClick={() => setRevision((value) => value + 1)}>
              Refresh balance
            </Button>
            <p className="text-sm text-slate-400">Funds may stay on hold while a transfer is under review.</p>
          </div>
          <div className={`${insetSurface} space-y-4 p-4 sm:p-5`}>
            <h2 className="font-semibold text-slate-100 [overflow-wrap:anywhere]">{account.ownerName}</h2>
            <div className="flex flex-wrap items-end justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs font-medium text-slate-400">Account ID:</p>
                <code className="mt-1 block text-sm text-slate-200 [overflow-wrap:anywhere]">{account.id}</code>
              </div>
              <Button variant="secondary" icon={Copy} onClick={() => void copy()}>
                Copy account ID
              </Button>
            </div>
            {copied && <Notice tone="success">Account ID copied.</Notice>}
            <p className="text-sm text-slate-400">
              To receive transfers, register a key under <Link className={textLink} to="/pix-keys">My Pix keys</Link>.
            </p>
          </div>
        </>
      )}
    </Card>
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
    <Card className="max-w-xl space-y-6">
      <PageHeading icon={ArrowDownToLine} description="Enter a registered Pix key (email, CPF, or random key). No real money will move.">
        Simulated Pix deposit
      </PageHeading>
      <form onSubmit={submit} className="space-y-4">
        <fieldset disabled={busy} className="min-w-0 space-y-4">
          <Field
            name="pixKey"
            label="Pix key (email, CPF, or random key)"
            icon={KeyRound}
            required
            error={fields.pixKey}
          />
          <Field
            name="amount"
            label="Amount (R$)"
            icon={Banknote}
            inputMode="decimal"
            placeholder="150.00"
            required
            error={fields.amount}
          />
          <Button type="submit" busy={busy} icon={ArrowDownToLine}>{busy ? "Depositing…" : "Deposit"}</Button>
        </fieldset>
        <ErrorNotice
          error={error}
          notFoundMessage="This Pix key isn't registered."
        />
      </form>
      {message && (
        <Notice tone="success">
          {message} <Link className={textLink} to="/">View balance</Link>
        </Notice>
      )}
    </Card>
  );
}
