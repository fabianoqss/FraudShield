import { ArrowLeft, ArrowRight, History, ReceiptText, RefreshCw } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { api } from "../api/client";
import { currency, dateTime, paymentTypeLabels } from "../api/types";
import type {
  LedgerEntry,
  LedgerPage,
  Transaction,
  TransactionPage,
} from "../api/types";
import { useAccounts } from "../auth/Accounts";
import { Button, Card, ErrorNotice, Notice, PageHeading, TransferStatus, insetSurface, textLink } from "../components/Forms";

const columns = ["Date", "Direction", "Amount", "Status", "Details"];
const statementCell = "min-w-0 px-3 py-2.5 [overflow-wrap:anywhere] md:table-cell md:px-4 md:py-3";
const mobileLabel = "mb-1 block text-xs font-medium text-slate-400 md:hidden";

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
    <Card className="space-y-5">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeading icon={ReceiptText} description="This statement excludes simulated Pix deposits.">
          Transfer statement
        </PageHeading>
        {account && (
          <Button variant="secondary" icon={RefreshCw} onClick={() => setRevision((value) => value + 1)}>
            Refresh statement
          </Button>
        )}
      </div>
      {!account ? (
        <Link className={textLink} to="/">Create an account to view your statement.</Link>
      ) : (
        <>
          <ErrorNotice error={error} />
          {!data && !error && <Notice>Loading statement…</Notice>}
          {data && (
            <>
              {data.transactions.length === 0 ? (
                <p className={`${insetSurface} p-4 text-sm text-slate-300`}>No transfers on this page.</p>
              ) : (
                <div className={`${insetSurface} p-2 md:p-0`}>
                  <table role="table" className="block w-full text-left text-sm text-slate-300 md:table md:table-fixed">
                    <thead role="rowgroup" className="sr-only md:not-sr-only md:table-header-group">
                      <tr role="row" className="border-b border-slate-700/80 text-slate-400">
                        {columns.map((column) => (
                          <th role="columnheader" scope="col" key={column} className="px-4 py-3 text-xs font-semibold">{column}</th>
                        ))}
                      </tr>
                    </thead>
                    <tbody role="rowgroup" className="block divide-y divide-slate-700/80 md:table-row-group">
                      {data.transactions.map((item) => (
                        <tr role="row" key={item.id} className="grid grid-cols-2 py-1 md:table-row">
                          <td role="cell" className={statementCell}>
                            <span aria-hidden="true" className={mobileLabel}>Date</span>
                            {dateTime(item.createdAt)}
                          </td>
                          <td role="cell" className={statementCell}>
                            <span aria-hidden="true" className={mobileLabel}>Direction</span>
                            {item.destinationAccountId === account.id
                              ? "Incoming"
                              : "Outgoing"}{" "}
                            · {paymentTypeLabels[item.type] ?? item.type}
                          </td>
                          <td role="cell" className={`${statementCell} font-semibold text-slate-100 tabular-nums`}>
                            <span aria-hidden="true" className={mobileLabel}>Amount</span>
                            {currency(item.amount)}
                          </td>
                          <td role="cell" className={statementCell}>
                            <span aria-hidden="true" className={mobileLabel}>Status</span>
                            <TransferStatus status={item.status} />
                          </td>
                          <td role="cell" className={`${statementCell} col-span-2`}>
                            <Link
                              className={textLink}
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
              <p className="text-sm text-slate-400">Only approved transfers change your balance.</p>
              <nav aria-label="Statement pagination" className="flex flex-wrap items-center gap-2.5">
                <Button variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Previous
                </Button>
                <span className="text-sm text-slate-300 tabular-nums">
                  Page {page + 1} of {Math.max(data.totalPages, 1)}
                </span>
                <Button
                  variant="secondary"
                  icon={ArrowRight}
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Next
                </Button>
              </nav>
            </>
          )}
        </>
      )}
    </Card>
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
    <Card className="space-y-5">
      <Link className={`${textLink} inline-flex items-center gap-2 text-sm`} to="/statement">
        <ArrowLeft aria-hidden="true" className="size-4" />
        Back to statement
      </Link>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeading icon={ReceiptText}>Transfer details</PageHeading>
        <Button variant="secondary" icon={RefreshCw} onClick={() => setRevision((value) => value + 1)}>
          Refresh details
        </Button>
      </div>
      <ErrorNotice error={error} notFoundMessage="Transaction not found." />
      {!transaction && !error && <Notice>Loading transaction…</Notice>}
      {transaction && (
        <>
          <dl className={`${insetSurface} grid gap-x-6 gap-y-4 p-4 sm:grid-cols-2 sm:p-5 lg:grid-cols-3`}>
            {[
              { label: "Transaction ID", value: <code>{transaction.id}</code> },
              { label: "Status", value: <TransferStatus status={transaction.status} /> },
              { label: "Amount", value: <strong className="font-semibold text-emerald-300 tabular-nums">{currency(transaction.amount)}</strong> },
              { label: "Type", value: paymentTypeLabels[transaction.type] ?? transaction.type },
              { label: "Source account", value: <code>{transaction.sourceAccountId}</code> },
              { label: "Destination account", value: <code>{transaction.destinationAccountId}</code> },
              { label: "Date", value: dateTime(transaction.createdAt) },
            ].map(({ label, value }) => (
              <div key={label} className="min-w-0">
                <dt className="text-xs font-medium text-slate-400">{label}</dt>
                <dd className="mt-1 text-sm text-slate-100 [overflow-wrap:anywhere]">{value}</dd>
              </div>
            ))}
          </dl>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <History aria-hidden="true" className="size-5 text-teal-300" />
            Audit events
          </h2>
          <ErrorNotice error={ledgerError} />
          {entries.map((entry) => (
            <article className={`${insetSurface} space-y-1 p-4 text-sm text-slate-300 [overflow-wrap:anywhere]`} key={entry.id}>
              <h3 className="font-semibold text-slate-100">
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
              <p className="text-xs text-slate-400">{dateTime(entry.recordedAt)}</p>
              <p>
                {entry.direction === "INCOMING" ? "Incoming" : "Outgoing"} · {currency(entry.amount)}
              </p>
              {entry.reason && <p>Reason: {entry.reason}</p>}
            </article>
          ))}
          {busy && <Notice>Loading audit events…</Notice>}
          {!busy && !ledgerError && entries.length === 0 && (
            <p className="text-sm text-slate-400">
              No events for this transaction on the pages searched so far. Events may take a moment to appear.
            </p>
          )}
          {ledgerPage + 1 < totalPages && (
            <>
              <p className="text-sm text-slate-400">
                More audit pages may contain events for this transaction.
              </p>
              <Button
                variant="secondary"
                busy={busy}
                disabled={!!ledgerError}
                onClick={() => setLedgerPage((page) => page + 1)}
              >
                Search more pages
              </Button>
            </>
          )}
        </>
      )}
    </Card>
  );
}
