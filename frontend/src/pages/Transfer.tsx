import { ArrowRight, ArrowRightLeft, Banknote, KeyRound, Search } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { api, ApiError, NetworkError } from "../api/client";
import { pollTransaction } from "../api/poll";
import { currency, paymentTypeLabels } from "../api/types";
import type { PaymentType, PixKeyLookup, Transaction, TransferRequest } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import {
  Button,
  Card,
  ErrorNotice,
  Field,
  FieldError,
  Notice,
  PageHeading,
  TransferStatus,
  fieldControl,
  fieldErrors,
  fieldLabel,
  insetSurface,
  parseAmount,
  textLink,
} from "../components/Forms";

function deviceId() {
  const key = "fraudshield.deviceId";
  let id = window.localStorage.getItem(key);
  if (!id) {
    id = crypto.randomUUID();
    window.localStorage.setItem(key, id);
  }
  return id;
}
export function TransferPage() {
  const { account } = useAccounts();
  if (!account)
    return (
      <Card className="max-w-xl space-y-5">
        <PageHeading icon={ArrowRightLeft} description="Create an account before making a transfer.">Transfer</PageHeading>
        <Link className={textLink} to="/">My account</Link>
      </Card>
    );
  return <TransferForm key={account.id} accountId={account.id} />;
}
export function TransferForm({ accountId }: { accountId: string }) {
  const [key, setKey] = useState("");
  const [amount, setAmount] = useState("");
  const [type, setType] = useState<PaymentType>("PIX");
  const [lookup, setLookup] = useState<PixKeyLookup>();
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(Date.now());
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [transaction, setTransaction] = useState<Pick<Transaction, "id" | "status">>();
  const [pollingMessage, setPollingMessage] = useState("");
  const [retry, setRetry] = useState<TransferRequest>();
  const inFlight = useRef(false);
  const mounted = useRef(true);
  const controller = useRef<AbortController | null>(null);
  const fields = fieldErrors(error);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
      controller.current?.abort();
    };
  }, []);
  const waitSeconds = Math.max(0, Math.ceil((retryAt - now) / 1000));
  function expireLookup() {
    setLookup(undefined);
    setError(new Error("The lookup expired or is invalid. Look up the Pix key again."));
  }
  useEffect(() => {
    if (!retryAt) return;
    const timer = window.setInterval(() => {
      const time = Date.now();
      setNow(time);
      if (time >= retryAt) setRetryAt(0);
    }, 250);
    return () => window.clearInterval(timer);
  }, [retryAt]);
  useEffect(() => {
    if (!lookup || transaction || busy) return;
    const timer = window.setTimeout(expireLookup, Math.max(0, Date.parse(lookup.expiresAt) - Date.now()));
    return () => window.clearTimeout(timer);
  }, [lookup, transaction, busy]);
  async function track(created: Transaction) {
    controller.current?.abort();
    const current = new AbortController();
    controller.current = current;
    setPollingMessage("Checking transfer status…");
    try {
      const result = await pollTransaction(
        created.id,
        current.signal,
        (updated) => setTransaction({ id: updated.id, status: updated.status }),
      );
      if (!current.signal.aborted)
        setPollingMessage(
          result === "timeout"
            ? "The transfer is still pending review. Check your statement for updates."
            : "",
        );
    } catch (cause) {
      if (!current.signal.aborted) {
        setError(cause);
        setPollingMessage(
          "Couldn't check the transfer status. Check your statement for updates.",
        );
      }
    }
  }
  async function send(payload: TransferRequest) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError(undefined);
    try {
      const created = await api.request<Transaction>("/transactions", {
        method: "POST",
        body: payload,
      });
      if (!mounted.current) return;
      setRetry(undefined);
      setTransaction({ id: created.id, status: created.status });
      if (created.status === "CREATED") void track(created);
    } catch (cause) {
      if (!mounted.current) return;
      if (cause instanceof NetworkError) {
        setRetry(payload);
        setError(
          new Error(
            "We didn't receive a response. Your transfer may have been created. Check your statement or safely retry the same submission.",
          ),
        );
      } else {
        setRetry(undefined);
        if (cause instanceof ApiError && cause.status === 409)
          setError(
            new Error(
              "This submission was already accepted. Check your statement before making another transfer.",
            ),
          );
        else if (cause instanceof ApiError && cause.status === 422) {
          if (/lookup|consulta/i.test(cause.message)) expireLookup();
          else setError(new Error(/same account|mesma conta/i.test(cause.message)
            ? "You can't transfer to the same account."
            : /insufficient|saldo insuficiente/i.test(cause.message)
              ? "Insufficient funds for this transfer." : cause.message));
        }
        else setError(cause);
      }
    } finally {
      inFlight.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || retry || Date.now() < retryAt) return;
    setError(undefined);
    try {
      parseAmount(amount);
      inFlight.current = true;
      setBusy(true);
      const result = await api.request<PixKeyLookup>("/accounts/pix-keys/lookup", {
        method: "POST", body: { key },
      });
      if (!mounted.current) return;
      if (!Number.isFinite(Date.parse(result.expiresAt)) || Date.parse(result.expiresAt) <= Date.now()) expireLookup();
      else setLookup(result);
    } catch (cause) {
      if (!mounted.current) return;
      if (cause instanceof ApiError && cause.status === 400)
        setError(new ApiError(400, "Invalid Pix key format.", { key: "Invalid Pix key format." }));
      else if (cause instanceof ApiError && cause.status === 404)
        setError(new Error("Pix key not found"));
      else if (cause instanceof ApiError && cause.status === 429) {
        const seconds = Number(cause.retryAfter);
        const delay = Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : 60;
        setNow(Date.now());
        setRetryAt(Date.now() + delay * 1000);
        setError(new Error("Too many lookups. Wait before trying again."));
      } else setError(cause);
    } finally {
      inFlight.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  function confirm() {
    if (!lookup || retry || inFlight.current) return;
    if (Date.parse(lookup.expiresAt) <= Date.now()) { expireLookup(); return; }
    void send({
      sourceAccountId: accountId, lookupId: lookup.lookupId,
      amount: parseAmount(amount), type, deviceId: deviceId(),
      idempotencyKey: crypto.randomUUID(),
    });
  }
  return (
    <Card className="max-w-xl space-y-6">
      <PageHeading icon={ArrowRightLeft}>Transfer</PageHeading>
      <ErrorNotice error={error} />
      {!transaction && !lookup && (
        <form onSubmit={submit} className="space-y-4">
          <h2 className="font-semibold text-slate-200">Recipient</h2>
          <fieldset disabled={busy || !!retry} className="min-w-0 space-y-4">
            <Field
              name="key"
              label="Pix key"
              icon={KeyRound}
              value={key}
              onChange={(event) => setKey(event.target.value)}
              required
              error={fields.key}
            />
            <Field
              name="amount"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              label="Amount (R$)"
              icon={Banknote}
              inputMode="decimal"
              placeholder="100.00"
              required
              error={fields.amount}
            />
            <div className="space-y-1.5">
              <label className={fieldLabel} htmlFor="payment-type">Payment type</label>
              <select
                className={fieldControl}
                id="payment-type"
                name="type"
                value={type}
                aria-invalid={!!fields.type}
                aria-describedby={fields.type ? "payment-type-error" : undefined}
                onChange={(event) => setType(event.target.value as PaymentType)}
              >
                <option value="PIX">{paymentTypeLabels.PIX}</option>
                <option value="CREDIT">{paymentTypeLabels.CREDIT}</option>
                <option value="DEBIT">{paymentTypeLabels.DEBIT}</option>
              </select>
              {fields.type && <FieldError id="payment-type-error">{fields.type}</FieldError>}
            </div>
            <Button type="submit" icon={Search} busy={busy} disabled={waitSeconds > 0}>
              {busy ? "Looking up key…" : "Look up key"}
            </Button>
          </fieldset>
        </form>
      )}
      {waitSeconds > 0 && (
        <Notice>Too many lookups. Try again in {waitSeconds} {waitSeconds === 1 ? "second" : "seconds"}.</Notice>
      )}
      {!transaction && lookup && (
        <div className={`${insetSurface} space-y-4 p-4 sm:p-5`}>
          <h2 className="text-lg font-semibold text-slate-100">Confirm transfer</h2>
          <p className="text-sm text-slate-300 [overflow-wrap:anywhere]">
            You're sending {currency(parseAmount(amount))} to {lookup.recipientName} (CPF {lookup.maskedCpf})
          </p>
          <div className="flex flex-wrap gap-2">
            <Button busy={busy} disabled={!!retry} icon={ArrowRight} onClick={confirm}>{busy ? "Sending…" : "Confirm"}</Button>
            <Button variant="secondary" disabled={busy || !!retry} onClick={() => { setLookup(undefined); setError(undefined); }}>Back</Button>
          </div>
        </div>
      )}
      {retry && (
        <div className={`${insetSurface} space-y-4 p-4 sm:p-5`}>
          <p className="text-sm text-slate-300">We saved the transfer details to prevent a duplicate submission.</p>
          <div className="flex flex-wrap items-center gap-2.5">
            <Button busy={busy} onClick={() => void send(retry)}>
              Retry same submission
            </Button>
            <Button
              variant="secondary"
              disabled={busy}
              onClick={() => {
                setRetry(undefined);
                setLookup(undefined);
                setError(undefined);
              }}
            >
              Discard and start a new transfer
            </Button>
            <Link className={textLink} to="/statement">View statement</Link>
          </div>
        </div>
      )}
      {transaction && (
        <div className={`${insetSurface} space-y-4 p-4 text-sm text-slate-300 sm:p-5`}>
          <p role="status">
            <TransferStatus status={transaction.status} />
          </p>
          <p>
            Transaction: <code className="text-slate-200 [overflow-wrap:anywhere]">{transaction.id}</code>
          </p>
          {transaction.status === "DENIED" && (
            <p>
              Fraud screening declined this transfer. No money was sent.
            </p>
          )}
          {transaction.status === "FLAGGED" && (
            <p>
              The transfer is pending manual review. The funds remain on hold.
            </p>
          )}
          {transaction.status === "APPROVED" && (
            <p>
              Transfer approved. Your balance may take a moment to update.
            </p>
          )}
          {pollingMessage && <Notice>{pollingMessage}</Notice>}
          <div className="flex flex-wrap items-center gap-3">
            <Link className={textLink} to={`/statement/${transaction.id}`}>View details</Link>
            {transaction.status !== "CREATED" && (
              <Button
                variant="secondary"
                onClick={() => {
                  setTransaction(undefined);
                  setLookup(undefined);
                  setError(undefined);
                }}
              >
                New transfer
              </Button>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
