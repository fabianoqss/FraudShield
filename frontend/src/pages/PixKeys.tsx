import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { api, ApiError } from "../api/client";
import type { PixKey, PixKeyType } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import { ErrorNotice } from "../components/Forms";

const labels: Record<PixKeyType, string> = { CPF: "CPF", EMAIL: "Email", RANDOM: "Random key" };
const registrationLabels: Record<PixKeyType, string> = {
  CPF: "Register CPF",
  EMAIL: "Register email",
  RANDOM: "Register a random key",
};
function display(key: PixKey) {
  return key.type === "CPF" ? key.value.replace(/^(\d{3})(\d{3})(\d{3})(\d{2})$/, "$1.$2.$3-$4") : key.value;
}
export function PixKeysPage() {
  const { account } = useAccounts();
  return account ? <PixKeys accountId={account.id} key={account.id} /> : (
    <section><h1>My Pix keys</h1><Link to="/">Create an account to register your Pix keys.</Link></section>
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
        setMessage("Pix key registered.");
      } else if (removing) {
        await api.request(`${path}/${removing.id}`, { method: "DELETE" });
        setKeys((previous) => previous?.filter((key) => key.id !== removing.id));
        setRemoving(undefined);
        setMessage("Pix key removed.");
      }
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status === 409
        ? new Error("This Pix key is already registered.") : cause);
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  async function copy(value: string) {
    try {
      await navigator.clipboard.writeText(value);
      setMessage("Pix key copied.");
    } catch {
      setError(new Error("Couldn't copy the Pix key. Select it and copy it manually."));
    }
  }
  return <section>
    <h1>My Pix keys</h1>
    <ErrorNotice error={error} />
    {!keys && !error && <p role="status">Loading Pix keys…</p>}
    {!keys && !!error && <button onClick={() => setRevision((value) => value + 1)}>Try again</button>}
    {keys && <>
      <p>{keys.length} of 5 keys registered.</p>
      {keys.length === 0 && <p>This account has no registered Pix keys.</p>}
      <ul>{keys.map((key) => <li key={key.id}>
        <strong>{labels[key.type]}: </strong><code>{display(key)}</code>{" "}
        <button className="secondary" disabled={busy} onClick={() => void copy(key.value)} aria-label={`Copy ${display(key)}`}>Copy</button>{" "}
        <button className="secondary" disabled={busy} onClick={() => setRemoving(key)} aria-label={`Remove ${display(key)}`}>Remove</button>
      </li>)}</ul>
      <p>CPF and email keys use your registration details. The server generates random keys.</p>
      {(["CPF", "EMAIL", "RANDOM"] as const).map((type) => <button key={type}
        disabled={busy || !!removing || keys.length >= 5 || (type !== "RANDOM" && keys.some((key) => key.type === type))}
        onClick={() => void mutate(type)}>{registrationLabels[type]}</button>)}
      {keys.length >= 5 && <p>You've reached the 5-key limit.</p>}
      {removing && <div role="group" aria-label="Confirm removal">
        <p>Remove Pix key {display(removing)}?</p>
        <button disabled={busy} onClick={() => void mutate()}>Confirm removal</button>
        <button className="secondary" disabled={busy} onClick={() => setRemoving(undefined)}>Cancel</button>
      </div>}
    </>}
    {message && <p role="status">{message}</p>}
  </section>;
}
