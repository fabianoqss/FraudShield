import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { api } from "../api/client";
import { currency, dateTime, paymentTypeLabels, statusLabels } from "../api/types";
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
      <h1>Transfer statement</h1>
      <p className="hint">
        This statement excludes simulated Pix deposits.
      </p>
      {!account ? (
        <Link to="/">Create an account to view your statement.</Link>
      ) : (
        <>
          <button
            className="secondary"
            onClick={() => setRevision((value) => value + 1)}
          >
            Refresh statement
          </button>
          <ErrorNotice error={error} />
          {!data && !error && <p role="status">Loading statement…</p>}
          {data && (
            <>
              {data.transactions.length === 0 ? (
                <p>No transfers on this page.</p>
              ) : (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Direction</th>
                        <th>Amount</th>
                        <th>Status</th>
                        <th>Details</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.transactions.map((item) => (
                        <tr key={item.id}>
                          <td>{dateTime(item.createdAt)}</td>
                          <td>
                            {item.destinationAccountId === account.id
                              ? "Incoming"
                              : "Outgoing"}{" "}
                            · {paymentTypeLabels[item.type] ?? item.type}
                          </td>
                          <td>{currency(item.amount)}</td>
                          <td>{statusLabels[item.status]}</td>
                          <td>
                            <Link
                              to={`/statement/${item.id}`}
                              aria-label={`View transaction ${item.id}`}
                            >
                              View details
                            </Link>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <p>Only approved transfers change your balance.</p>
              <nav aria-label="Statement pagination" className="pagination">
                <button disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Previous
                </button>
                <span>
                  Page {page + 1} of {Math.max(data.totalPages, 1)}
                </span>
                <button
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Next
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
      <Link to="/statement">Back to statement</Link>
      <h1>Transfer details</h1>
      <button
        className="secondary"
        onClick={() => setRevision((value) => value + 1)}
      >
        Refresh details
      </button>
      <ErrorNotice error={error} notFoundMessage="Transaction not found." />
      {!transaction && !error && <p role="status">Loading transaction…</p>}
      {transaction && (
        <>
          <dl className="details">
            <dt>Transaction ID</dt>
            <dd>{transaction.id}</dd>
            <dt>Status</dt>
            <dd>{statusLabels[transaction.status]}</dd>
            <dt>Amount</dt>
            <dd>{currency(transaction.amount)}</dd>
            <dt>Type</dt>
            <dd>{paymentTypeLabels[transaction.type] ?? transaction.type}</dd>
            <dt>Source account</dt>
            <dd>{transaction.sourceAccountId}</dd>
            <dt>Destination account</dt>
            <dd>{transaction.destinationAccountId}</dd>
            <dt>Date</dt>
            <dd>{dateTime(transaction.createdAt)}</dd>
          </dl>
          <h2>Audit events</h2>
          <ErrorNotice error={ledgerError} />
          {entries.map((entry) => (
            <article className="ledger-entry" key={entry.id}>
              <h3>
                {entry.eventType === "TRANSACTION_CREATED"
                  ? "Transaction created"
                  : entry.eventType === "TRANSACTION_APPROVED"
                    ? "Transaction approved"
                    : entry.eventType === "TRANSACTION_DENIED"
                      ? "Transaction denied"
                      : entry.eventType === "TRANSACTION_FLAGGED"
                        ? "Transaction in review"
                        : entry.eventType}
              </h3>
              <p>{dateTime(entry.recordedAt)}</p>
              <p>
                {entry.direction === "INCOMING" ? "Incoming" : "Outgoing"} · {currency(entry.amount)}
              </p>
              {entry.reason && <p>Reason: {entry.reason}</p>}
            </article>
          ))}
          {busy && <p role="status">Loading audit events…</p>}
          {!busy && !ledgerError && entries.length === 0 && (
            <p>
              No events for this transaction on the pages searched so far. Events may take a moment to appear.
            </p>
          )}
          {ledgerPage + 1 < totalPages && (
            <>
              <p>
                More audit pages may contain events for this transaction.
              </p>
              <button
                disabled={busy || !!ledgerError}
                onClick={() => setLedgerPage((page) => page + 1)}
              >
                Search more pages
              </button>
            </>
          )}
        </>
      )}
    </section>
  );
}
