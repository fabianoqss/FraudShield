import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { api } from "../api/client";
import { currency, dateTime, statusLabels } from "../api/types";
import type {
  LedgerEntry,
  LedgerPage,
  Transaction,
  TransactionPage,
} from "../api/types";
import { useAccounts } from "../auth/Accounts";
import { ErrorNotice } from "../components/Forms";

export function StatementPage() {
  const { account } = useAccounts();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<TransactionPage>();
  const [error, setError] = useState<unknown>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!account) return;
    const controller = new AbortController();
    setData(undefined);
    setError(undefined);
    api
      .request<TransactionPage>(
        `/transactions?accountId=${account.id}&page=${page}&size=20`,
        { signal: controller.signal },
      )
      .then(setData)
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause);
      });
    return () => controller.abort();
  }, [account, page, revision]);
  return (
    <section>
      <h1>Extrato de transferências</h1>
      <p className="hint">
        Depósitos PIX simulados não aparecem neste extrato.
      </p>
      {!account ? (
        <Link to="/">Crie uma conta para consultar seu extrato.</Link>
      ) : (
        <>
          <button
            className="secondary"
            onClick={() => setRevision((value) => value + 1)}
          >
            Atualizar extrato
          </button>
          <ErrorNotice error={error} />
          {!data && !error && <p role="status">Carregando extrato…</p>}
          {data && (
            <>
              {data.transactions.length === 0 ? (
                <p>Nenhuma transferência nesta página.</p>
              ) : (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Data</th>
                        <th>Movimento</th>
                        <th>Valor</th>
                        <th>Status</th>
                        <th>Detalhes</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.transactions.map((item) => (
                        <tr key={item.id}>
                          <td>{dateTime(item.createdAt)}</td>
                          <td>
                            {item.destinationAccountId === account.id
                              ? "Entrada"
                              : "Saída"}{" "}
                            · {item.type}
                          </td>
                          <td>{currency(item.amount)}</td>
                          <td>{statusLabels[item.status]}</td>
                          <td>
                            <Link
                              to={`/extrato/${item.id}`}
                              aria-label={`Ver transação ${item.id}`}
                            >
                              Ver detalhes
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p>Somente transferências aprovadas movimentam o saldo.</p>
              <nav aria-label="Paginação do extrato" className="pagination">
                <button disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Anterior
                </button>
                <span>
                  Página {page + 1} de {Math.max(data.totalPages, 1)}
                </span>
                <button
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Próxima
                </button>
              </nav>
            </>
          )}
        </>
      )}
    </section>
  );
}

export function TransactionPage() {
  const { id = "" } = useParams();
  const { account } = useAccounts();
  const [transaction, setTransaction] = useState<Transaction>();
  const [entries, setEntries] = useState<LedgerEntry[]>([]);
  const [ledgerPage, setLedgerPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [ledgerError, setLedgerError] = useState<unknown>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setTransaction(undefined);
    setError(undefined);
    setEntries([]);
    setLedgerPage(0);
    setTotalPages(0);
    api
      .request<Transaction>(`/transactions/${encodeURIComponent(id)}`, {
        signal: controller.signal,
      })
      .then(setTransaction)
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setError(cause);
      });
    return () => controller.abort();
  }, [id, revision]);
  useEffect(() => {
    if (!account || !transaction) return;
    const controller = new AbortController();
    setBusy(true);
    setLedgerError(undefined);
    api
      .request<LedgerPage>(
        `/ledger/account/${account.id}?page=${ledgerPage}&size=100`,
        { signal: controller.signal },
      )
      .then((data) => {
        setEntries((previous) => {
          const combined = [
            ...(ledgerPage === 0 ? [] : previous),
            ...data.entries.filter((entry) => entry.transactionId === id),
          ];
          return [
            ...new Map(combined.map((entry) => [entry.id, entry])).values(),
          ];
        });
        setTotalPages(data.totalPages);
      })
      .catch((cause: unknown) => {
        if (!controller.signal.aborted) setLedgerError(cause);
      })
      .finally(() => {
        if (!controller.signal.aborted) setBusy(false);
      });
    return () => controller.abort();
  }, [account, transaction, id, ledgerPage]);
  return (
    <section>
      <Link to="/extrato">Voltar ao extrato</Link>
      <h1>Detalhes da transferência</h1>
      <button
        className="secondary"
        onClick={() => setRevision((value) => value + 1)}
      >
        Atualizar detalhes
      </button>
      <ErrorNotice error={error} notFoundMessage="Transação não encontrada." />
      {!transaction && !error && <p role="status">Carregando transação…</p>}
      {transaction && (
        <>
          <dl className="details">
            <dt>Identificador</dt>
            <dd>{transaction.id}</dd>
            <dt>Status</dt>
            <dd>{statusLabels[transaction.status]}</dd>
            <dt>Valor</dt>
            <dd>{currency(transaction.amount)}</dd>
            <dt>Tipo</dt>
            <dd>{transaction.type}</dd>
            <dt>Origem</dt>
            <dd>{transaction.sourceAccountId}</dd>
            <dt>Destino</dt>
            <dd>{transaction.destinationAccountId}</dd>
            <dt>Data</dt>
            <dd>{dateTime(transaction.createdAt)}</dd>
          </dl>
          <h2>Eventos de auditoria</h2>
          <ErrorNotice error={ledgerError} />
          {entries.map((entry) => (
            <article className="ledger-entry" key={entry.id}>
              <h3>
                {entry.eventType === "TRANSACTION_CREATED"
                  ? "Transação criada"
                  : entry.eventType === "TRANSACTION_APPROVED"
                    ? "Transação aprovada"
                    : entry.eventType === "TRANSACTION_DENIED"
                      ? "Transação negada"
                      : entry.eventType === "TRANSACTION_FLAGGED"
                        ? "Transação em revisão"
                        : entry.eventType}
              </h3>
              <p>{dateTime(entry.recordedAt)}</p>
              <p>
                {entry.direction === "INCOMING" ? "Entrada" : "Saída"} · {currency(entry.amount)}
              </p>
              {entry.reason && <p>Motivo: {entry.reason}</p>}
            </article>
          ))}
          {busy && <p role="status">Carregando eventos…</p>}
          {!busy && !ledgerError && entries.length === 0 && (
            <p>
              Nenhum evento desta transação nas páginas consultadas. Os eventos
              podem levar alguns instantes para chegar.
            </p>
          )}
          {ledgerPage + 1 < totalPages && (
            <>
              <p>
                Há mais páginas de auditoria que podem conter eventos desta
                transação.
              </p>
              <button
                disabled={busy || !!ledgerError}
                onClick={() => setLedgerPage((page) => page + 1)}
              >
                Buscar nas próximas páginas
              </button>
            </>
          )}
        </>
      )}
    </section>
  );
}
