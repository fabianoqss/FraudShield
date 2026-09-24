export interface Account {
  id: string;
  ownerId: string;
  ownerName: string;
  balance: number;
  lockedBalance: number;
  status: string;
  createdAt: string;
}
export interface Balance {
  name: string;
  accountId: string;
  balance: number;
  lockedBalance: number;
  availableBalance: number;
}
export type PaymentType = "PIX" | "CREDIT" | "DEBIT";
export type TransactionStatus = "CREATED" | "APPROVED" | "DENIED" | "FLAGGED";
export interface Transaction {
  id: string;
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  type: PaymentType;
  status: TransactionStatus;
  createdAt: string;
}
export interface TransferRequest {
  sourceAccountId: string;
  destinationAccountId: string;
  amount: number;
  type: PaymentType;
  deviceId: string;
  ipAddress: null;
  idempotencyKey: string;
}
export interface Page {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface TransactionPage extends Page {
  transactions: Transaction[];
}
export interface LedgerEntry {
  id: string;
  transactionId: string;
  eventType: string;
  eventPayload: Record<string, unknown>;
  recordedAt: string;
}
export interface LedgerPage extends Page {
  entries: LedgerEntry[];
}
export const statusLabels: Record<TransactionStatus, string> = {
  CREATED: "Em análise",
  APPROVED: "APROVADA",
  DENIED: "NEGADA",
  FLAGGED: "EM REVISÃO",
};
export const currency = (amount: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    amount,
  );
export const dateTime = (value: string) =>
  new Date(value).toLocaleString("pt-BR");
