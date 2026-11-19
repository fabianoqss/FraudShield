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
export const paymentTypeLabels: Record<PaymentType, string> = {
  PIX: "Pix",
  CREDIT: "Credit",
  DEBIT: "Debit",
};
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
  lookupId: string;
  amount: number;
  type: PaymentType;
  deviceId: string;
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
  direction: "INCOMING" | "OUTGOING";
  amount: number;
  reason?: string;
  recordedAt: string;
}
export interface LedgerPage extends Page {
  entries: LedgerEntry[];
}
export const statusLabels: Record<TransactionStatus, string> = {
  CREATED: "Pending review",
  APPROVED: "APPROVED",
  DENIED: "DENIED",
  FLAGGED: "IN REVIEW",
};
export const currency = (amount: number) =>
  new Intl.NumberFormat("en-US", { style: "currency", currency: "BRL" }).format(
    amount,
  );
export const dateTime = (value: string) =>
  new Date(value).toLocaleString("en-US");

export type PixKeyType = "CPF" | "EMAIL" | "RANDOM";
export interface PixKey {
  id: string;
  type: PixKeyType;
  value: string;
  accountId: string;
  createdAt: string;
}
export interface PixKeyLookup {
  lookupId: string;
  recipientName: string;
  maskedCpf: string;
  keyType: PixKeyType;
  expiresAt: string;
}
