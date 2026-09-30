import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { api } from "../api/client";
import type { Account } from "../api/types";
import { ErrorNotice } from "../components/Forms";

interface AccountsContext {
  accounts: Account[];
  account: Account | null;
  reload: () => Promise<void>;
}
const Context = createContext<AccountsContext | null>(null);
export function AccountsProvider({ children }: { children: ReactNode }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selected, setSelected] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>();
  async function reload() {
    setError(undefined);
    setLoading(true);
    try {
      setAccounts(await api.request<Account[]>("/accounts"));
    } catch (cause) {
      setError(cause);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    const controller = new AbortController();
    api
      .request<Account[]>("/accounts", { signal: controller.signal })
      .then(setAccounts)
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, []);
  const account =
    accounts.find((item) => item.id === selected) ?? accounts[0] ?? null;
  if (loading) return <p role="status">Carregando contas…</p>;
  if (error)
    return (
      <section>
        <ErrorNotice error={error} />
        <button onClick={() => void reload()}>Tentar novamente</button>
      </section>
    );
  return (
    <Context.Provider value={{ accounts, account, reload }}>
      {accounts.length > 1 && (
        <label className="account-picker">
          Conta selecionada
          <select
            value={account?.id}
            onChange={(event) => setSelected(event.target.value)}
          >
            {accounts.map((item) => (
              <option key={item.id} value={item.id}>
                {item.ownerName} — {item.id}
              </option>
            ))}
          </select>
        </label>
      )}
      <div key={account?.id ?? "empty"}>{children}</div>
    </Context.Provider>
  );
}
export function useAccounts() {
  const value = useContext(Context);
  if (!value) throw new Error("AccountsProvider is required");
  return value;
}
