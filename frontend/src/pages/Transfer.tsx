import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { api, ApiError, NetworkError } from "../api/client";
import { pollTransaction } from "../api/poll";
import { currency, statusLabels } from "../api/types";
import type { PaymentType, PixKeyLookup, Transaction, TransferRequest } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import {
  ErrorNotice,
  Field,
  fieldErrors,
  parseAmount,
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
      <section>
        <h1>Transferir</h1>
        <p>Crie uma conta antes de transferir.</p>
        <Link to="/">Minha conta</Link>
      </section>
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
    setError(new Error("Consulta expirada ou inválida. Consulte a chave PIX novamente."));
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
    setPollingMessage("Acompanhando a análise…");
    try {
      const result = await pollTransaction(
        created.id,
        current.signal,
        (updated) => setTransaction({ id: updated.id, status: updated.status }),
      );
      if (!current.signal.aborted)
        setPollingMessage(
          result === "timeout"
            ? "A análise ainda está pendente. Consulte o extrato para acompanhar."
            : "",
        );
    } catch (cause) {
      if (!current.signal.aborted) {
        setError(cause);
        setPollingMessage(
          "Não foi possível acompanhar o status. Consulte o extrato.",
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
            "A resposta não chegou. A transferência pode ter sido criada. Consulte o extrato ou repita o mesmo envio com segurança.",
          ),
        );
      } else {
        setRetry(undefined);
        if (cause instanceof ApiError && cause.status === 409)
          setError(
            new Error(
              "Este envio já foi registrado. Consulte o extrato antes de fazer outra transferência.",
            ),
          );
        else if (cause instanceof ApiError && cause.status === 422) {
          if (/lookup|consulta/i.test(cause.message)) expireLookup();
          else setError(new Error(/same account|mesma conta/i.test(cause.message)
            ? "Não é possível transferir para a mesma conta."
            : /insufficient|saldo insuficiente/i.test(cause.message)
              ? "Saldo insuficiente para realizar a transferência." : cause.message));
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
        setError(new ApiError(400, "Formato de chave PIX inválido.", { key: "Formato de chave PIX inválido." }));
      else if (cause instanceof ApiError && cause.status === 404)
        setError(new Error("Chave PIX não encontrada"));
      else if (cause instanceof ApiError && cause.status === 429) {
        const seconds = Number(cause.retryAfter);
        const delay = Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : 60;
        setNow(Date.now());
        setRetryAt(Date.now() + delay * 1000);
        setError(new Error("Muitas consultas. Aguarde para consultar novamente."));
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
    <section>
      <h1>Transferir</h1>
      <ErrorNotice error={error} />
      {!transaction && !lookup && (
        <form onSubmit={submit}>
          <h2>Para quem?</h2>
          <fieldset disabled={busy || !!retry}>
            <Field
              name="key"
              label="Chave PIX"
              value={key}
              onChange={(event) => setKey(event.target.value)}
              required
              error={fields.key}
            />
            <Field
              name="amount"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              label="Valor (R$)"
              inputMode="decimal"
              placeholder="100,00"
              required
              error={fields.amount}
            />
            <label htmlFor="payment-type">Tipo</label>
            <select id="payment-type" name="type" value={type} onChange={(event) => setType(event.target.value as PaymentType)}>
              <option value="PIX">PIX</option>
              <option value="CREDIT">Crédito</option>
              <option value="DEBIT">Débito</option>
            </select>
            {fields.type && <p className="field-error">{fields.type}</p>}
            <button disabled={waitSeconds > 0}>{busy ? "Consultando…" : "Consultar chave"}</button>
          </fieldset>
        </form>
      )}
      {waitSeconds > 0 && <p role="status">Muitas consultas. Aguarde {waitSeconds} segundos.</p>}
      {!transaction && lookup && <div>
        <h2>Confirme</h2>
        <p>Você está enviando {currency(parseAmount(amount))} para {lookup.recipientName} (CPF {lookup.maskedCpf})</p>
        <button disabled={busy || !!retry} onClick={confirm}>{busy ? "Enviando…" : "Confirmar"}</button>
        <button className="secondary" disabled={busy || !!retry} onClick={() => { setLookup(undefined); setError(undefined); }}>Voltar</button>
      </div>}
      {retry && (
        <div>
          <p>Os dados do envio foram preservados para evitar duplicação.</p>
          <button disabled={busy} onClick={() => void send(retry)}>
            Repetir o mesmo envio
          </button>
          <button
            type="button"
            className="secondary"
            disabled={busy}
            onClick={() => {
              setRetry(undefined);
              setLookup(undefined);
              setError(undefined);
            }}
          >
            Descartar e fazer nova transferência
          </button>
          <Link to="/extrato">Consultar extrato</Link>
        </div>
      )}
      {transaction && (
        <div>
          <p role="status">
            <strong>{statusLabels[transaction.status]}</strong>
          </p>
          <p>
            Transação: <code>{transaction.id}</code>
          </p>
          {transaction.status === "DENIED" && (
            <p>
              A transferência foi negada pela análise de fraude. O valor não foi
              transferido.
            </p>
          )}
          {transaction.status === "FLAGGED" && (
            <p>
              A transferência aguarda revisão manual e o valor permanece
              reservado.
            </p>
          )}
          {transaction.status === "APPROVED" && (
            <p>
              Transferência aprovada. O saldo pode levar alguns instantes para
              atualizar.
            </p>
          )}
          {pollingMessage && <p role="status">{pollingMessage}</p>}
          <Link to={`/extrato/${transaction.id}`}>Ver detalhes</Link>
          {transaction.status !== "CREATED" && (
            <button
              className="secondary"
              onClick={() => {
                setTransaction(undefined);
                setLookup(undefined);
                setError(undefined);
              }}
            >
              Nova transferência
            </button>
          )}
        </div>
      )}
    </section>
  );
}
