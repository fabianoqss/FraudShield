import { Copy, KeyRound, Plus, RefreshCw, Trash2 } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { api, ApiError } from "../api/client";
import type { PixKey, PixKeyType } from "../api/types";
import { useAccounts } from "../auth/Accounts";
import { Button, Card, ErrorNotice, Notice, PageHeading, insetSurface, textLink } from "../components/Forms";

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
    <Card className="space-y-5">
      <PageHeading icon={KeyRound}>My Pix keys</PageHeading>
      <Link className={textLink} to="/">Create an account to register your Pix keys.</Link>
    </Card>
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
  return (
    <Card className="space-y-5">
      <PageHeading icon={KeyRound} description={keys && `${keys.length} of 5 keys registered.`}>
        My Pix keys
      </PageHeading>
      <ErrorNotice error={error} />
      {!keys && !error && <Notice>Loading Pix keys…</Notice>}
      {!keys && !!error && <Button icon={RefreshCw} onClick={() => setRevision((value) => value + 1)}>Try again</Button>}
      {keys && (
        <>
          {keys.length === 0 ? (
            <p className={`${insetSurface} p-4 text-sm text-slate-300`}>This account has no registered Pix keys.</p>
          ) : (
            <ul className="space-y-2.5">
              {keys.map((key) => (
                <li className={`${insetSurface} flex flex-wrap items-center justify-between gap-3 p-4`} key={key.id}>
                  <div className="min-w-0 space-y-1">
                    <p className="text-xs font-semibold text-emerald-300">{labels[key.type]}</p>
                    <code className="block text-sm text-slate-200 [overflow-wrap:anywhere]">{display(key)}</code>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="secondary" icon={Copy} disabled={busy} onClick={() => void copy(key.value)} aria-label={`Copy ${display(key)}`}>
                      Copy
                    </Button>
                    <Button variant="danger" icon={Trash2} disabled={busy} onClick={() => setRemoving(key)} aria-label={`Remove ${display(key)}`}>
                      Remove
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <p className="text-sm leading-relaxed text-slate-400">CPF and email keys use your registration details. The server generates random keys.</p>
          <div className="flex flex-wrap gap-2">
            {(["CPF", "EMAIL", "RANDOM"] as const).map((type) => (
              <Button
                key={type}
                icon={Plus}
                disabled={busy || !!removing || keys.length >= 5 || (type !== "RANDOM" && keys.some((key) => key.type === type))}
                onClick={() => void mutate(type)}
              >
                {registrationLabels[type]}
              </Button>
            ))}
          </div>
          {keys.length >= 5 && <p className="text-sm text-slate-400">You've reached the 5-key limit.</p>}
          {removing && (
            <div role="group" aria-label="Confirm removal" className="space-y-3 rounded-xl border border-rose-400/45 bg-rose-400/10 p-4">
              <p className="text-sm text-rose-200 [overflow-wrap:anywhere]">Remove Pix key {display(removing)}?</p>
              <div className="flex flex-wrap gap-2">
                <Button variant="danger" icon={Trash2} busy={busy} onClick={() => void mutate()}>Confirm removal</Button>
                <Button variant="secondary" disabled={busy} onClick={() => setRemoving(undefined)}>Cancel</Button>
              </div>
            </div>
          )}
        </>
      )}
      {message && <Notice tone="success">{message}</Notice>}
    </Card>
  );
}
