import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router";
import { api, ApiError, NetworkError } from "../api/client";
import { pollTransaction } from "../api/poll";
import { statusLabels } from "../api/types";
import type { PaymentType, Transaction, TransferRequest } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import {
  ErrorNotice,
  Field,
  fieldErrors,
  formValues,
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
  return <TransferForm accountId={account.id} />;
}
export function TransferForm({ accountId }: { accountId: string }) {
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [transaction, setTransaction] = useState<Transaction>();
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
  async function track(created: Transaction) {
    controller.current?.abort();
    const current = new AbortController();
    controller.current = current;
    setPollingMessage("Acompanhando a análise…");
    try {
      const result = await pollTransaction(
        created.id,
        current.signal,
        setTransaction,
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
      setTransaction(created);
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
        else if (cause instanceof ApiError && cause.status === 422)
          setError(
            new ApiError(
              422,
              `Transferência não realizada: saldo insuficiente ou conta de destino desconhecida. ${cause.message}`,
              cause.fieldErrors,
            ),
          );
        else setError(cause);
      }
    } finally {
      inFlight.current = false;
      if (mounted.current) setBusy(false);
    }
  }
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || retry) return;
    const values = formValues(event.currentTarget);
    try {
      const destinationAccountId = values.destinationAccountId?.trim() ?? "";
      if (
        !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
          destinationAccountId,
        )
      )
        throw new Error("Informe um identificador de conta válido (UUID).");
      if (destinationAccountId.toLowerCase() === accountId.toLowerCase())
        throw new Error("Selecione uma conta de destino diferente da origem.");
      await send({
        sourceAccountId: accountId,
        destinationAccountId,
        amount: parseAmount(values.amount ?? ""),
        type: values.type as PaymentType,
        deviceId: deviceId(),
        ipAddress: null,
        idempotencyKey: crypto.randomUUID(),
      });
    } catch (cause) {
      setError(cause);
    }
  }
  return (
    <section>
      <h1>Transferir</h1>
      <ErrorNotice error={error} />
      {!transaction && (
        <form onSubmit={submit}>
          <fieldset disabled={busy || !!retry}>
            <Field
              name="destinationAccountId"
              label="Identificador da conta de destino"
              required
              error={fields.destinationAccountId}
            />
            <Field
              name="amount"
              label="Valor (R$)"
              inputMode="decimal"
              placeholder="100,00"
              required
              error={fields.amount}
            />
            <label htmlFor="payment-type">Tipo</label>
            <select id="payment-type" name="type">
              <option value="PIX">PIX</option>
              <option value="CREDIT">Crédito</option>
              <option value="DEBIT">Débito</option>
            </select>
            {fields.type && <p className="field-error">{fields.type}</p>}
            <button>{busy ? "Enviando…" : "Enviar transferência"}</button>
          </fieldset>
        </form>
      )}
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
