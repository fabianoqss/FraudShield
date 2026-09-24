import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { api, ApiError } from "../api/client";
import type { PixKey, PixKeyType } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import { ErrorNotice } from "../components/Forms";

const labels: Record<PixKeyType, string> = { CPF: "CPF", EMAIL: "E-mail", RANDOM: "Chave aleatória" };
function display(key: PixKey) {
  return key.type === "CPF" ? key.value.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4") : key.value;
}
export function PixKeysPage() {
  const { account } = useAccounts();
  return account ? <PixKeys accountId={account.id} key={account.id} /> : (
    <section><h1>Minhas chaves PIX</h1><Link to="/">Crie uma conta para cadastrar suas chaves.</Link></section>
  );
}
export function PixKeys({ accountId }: { accountId: string }) {
  const [keys, setKeys] = useState<PixKey[]>();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState<PixKey>();
  const [message, setMessage] = useState("");
  const [revision, setRevision] = useState(0);
  const inFlight = useRef(false);
  const path = `/accounts/${accountId}/pix-keys`;
  useEffect(() => {
    const controller = new AbortController();
    setError(undefined);
    api.request<PixKey[]>(path, { signal: controller.signal }).then((data) => {
      if (!controller.signal.aborted) setKeys(data);
    }).catch((cause: unknown) => {
      if (!controller.signal.aborted) setError(cause);
    });
    return () => controller.abort();
  }, [path, revision]);
  async function mutate(type?: PixKeyType) {
    if (inFlight.current) return;
    inFlight.current = true;
    setBusy(true);
    setError(undefined);
    setMessage("");
    try {
      if (type) {
        const created = await api.request<PixKey>(path, { method: "POST", body: { type } });
        setKeys((previous) => [...(previous ?? []), created]);
        setMessage("Chave cadastrada.");
      } else if (removing) {
        await api.request(`${path}/${removing.id}`, { method: "DELETE" });
        setKeys((previous) => previous?.filter((key) => key.id !== removing.id));
        setRemoving(undefined);
        setMessage("Chave removida.");
      }
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 409
        ? new Error("Esta chave já está cadastrada") : cause);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setMessage("Chave copiada.");
    } catch {
      setError(new Error("Não foi possível copiar. Selecione a chave e copie manualmente."));
    }
  }
  return <section>
    <h1>Minhas chaves PIX</h1>
    <ErrorNotice error={error} />
    {!keys && !error && <p role="status">Carregando chaves…</p>}
    {!keys && !!error && <button onClick={() => setRevision((value) => value + 1)}>Tentar novamente</button>}
    {keys && <>
      <p>{keys.length} de 5 chaves cadastradas.</p>
      {keys.length === 0 && <p>Nenhuma chave cadastrada nesta conta.</p>}
      <ul>{keys.map((key) => <li key={key.id}>
        <strong>{labels[key.type]}: </strong><code>{display(key)}</code>{" "}
        <button className="secondary" disabled={busy} onClick={() => void copy(key.value)} aria-label={`Copiar ${display(key)}`}>Copiar</button>{" "}
        <button className="secondary" disabled={busy} onClick={() => setRemoving(key)} aria-label={`Remover ${display(key)}`}>Remover</button>
      </li>)}</ul>
      <p>CPF e e-mail usam os dados do seu cadastro. A chave aleatória é gerada pelo servidor.</p>
      {(["CPF", "EMAIL", "RANDOM"] as const).map((type) => <button key={type}
        disabled={busy || !!removing || keys.length >= 5 || (type !== "RANDOM" && keys.some((key) => key.type === type))}
        onClick={() => void mutate(type)}>Cadastrar {labels[type]}</button>)}
      {keys.length >= 5 && <p>Limite de 5 chaves atingido.</p>}
      {removing && <div role="group" aria-label="Confirmar remoção">
        <p>Remover a chave {display(removing)}?</p>
        <button disabled={busy} onClick={() => void mutate()}>Confirmar remoção</button>
        <button className="secondary" disabled={busy} onClick={() => setRemoving(undefined)}>Cancelar</button>
      </div>}
    </>}
    {message && <p role="status">{message}</p>}
  </section>;
}
