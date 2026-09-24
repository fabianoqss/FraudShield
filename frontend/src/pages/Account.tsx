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
          "Não foi possível copiar. Selecione o identificador e copie manualmente.",
        ),
      );
    }
  }
  return (
    <section>
      <h1>Minha conta</h1>
      <ErrorNotice error={error} />
      {!account ? (
        <>
          <p>Você ainda não tem uma conta.</p>
          <button disabled={busy} onClick={() => void create()}>
            {busy ? "Criando…" : "Criar conta"}
          </button>
        </>
      ) : (
        <>
          <p>{account.ownerName}</p>
          <p>Identificador da conta:</p>
          <code className="account-id">{account.id}</code>
          <button className="secondary" onClick={() => void copy()}>
            Copiar identificador
          </button>
          {copied && <p role="status">Identificador copiado.</p>}
          <p>Para receber transferências, cadastre uma chave em <Link to="/chaves">Minhas chaves PIX</Link>.</p>
          {balance ? (
            <dl className="balances">
              <div>
                <dt>Saldo total</dt>
                <dd>{currency(balance.balance)}</dd>
              </div>
              <div>
                <dt>Bloqueado</dt>
                <dd>{currency(balance.lockedBalance)}</dd>
              </div>
              <div>
                <dt>Disponível</dt>
                <dd>{currency(balance.availableBalance)}</dd>
              </div>
            </dl>
          ) : (
            !error && <p role="status">Carregando saldo…</p>
          )}
          <button
            className="secondary"
            onClick={() => setRevision((value) => value + 1)}
          >
            Atualizar saldo
          </button>
          <p className="hint">
            Valores em análise ou em revisão podem permanecer reservados.
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
        `Depósito de ${currency(result.amount)} realizado para ${result.receiverName}.`,
      );
      form.reset();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 400
        ? new ApiError(400, "Formato inválido. Verifique a chave PIX e o valor.", { ...cause.fieldErrors, pixKey: cause.fieldErrors.pixKey ?? (Object.keys(cause.fieldErrors).length ? "" : "Formato de chave PIX inválido.") })
        : cause);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section>
      <h1>Depósito PIX simulado</h1>
      <p>
        Informe uma chave PIX cadastrada (e-mail, CPF ou chave aleatória). Nenhum dinheiro real será
        movimentado.
      </p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <Field
            name="pixKey"
            label="Chave PIX (e-mail, CPF ou aleatória)"
            required
            error={fields.pixKey}
          />
          <Field
            name="amount"
            label="Valor (R$)"
            inputMode="decimal"
            placeholder="150,00"
            required
            error={fields.amount}
          />
          <button>{busy ? "Depositando…" : "Depositar"}</button>
        </fieldset>
        <ErrorNotice
          error={error}
          notFoundMessage="Chave PIX não cadastrada."
        />
      </form>
      {message && (
        <p role="status">
          {message} <Link to="/">Ver saldo</Link>
        </p>
      )}
    </section>
  );
}
