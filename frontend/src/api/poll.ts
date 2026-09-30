import { api } from "./client";
import type { Transaction } from "./types";

export async function pollTransaction(
  id: string,
  signal: AbortSignal,
  onUpdate: (transaction: Transaction) => void,
): Promise<"complete" | "timeout"> {
  const controller = new AbortController();
  let expired = false;
  const cancel = () => controller.abort();
  if (signal.aborted) throw new DOMException("Aborted", "AbortError");
  signal.addEventListener("abort", cancel, { once: true });
  const deadline = setTimeout(() => {
    expired = true;
    controller.abort();
  }, 30_000);
  function delay() {
    return new Promise<void>((resolve, reject) => {
      const abort = () => {
        clearTimeout(timer);
        reject(new DOMException("Aborted", "AbortError"));
      };
      const timer = setTimeout(() => {
        controller.signal.removeEventListener("abort", abort);
        resolve();
      }, 1500);
      controller.signal.addEventListener("abort", abort, { once: true });
    });
  }
  function query(): Promise<Transaction> {
    return new Promise((resolve, reject) => {
      const abort = () => reject(new DOMException("Aborted", "AbortError"));
      controller.signal.addEventListener("abort", abort, { once: true });
      api
        .request<Transaction>(`/transactions/${encodeURIComponent(id)}`, {
          signal: controller.signal,
        })
        .then(
          (transaction) => {
            controller.signal.removeEventListener("abort", abort);
            resolve(transaction);
          },
          (error: unknown) => {
            controller.signal.removeEventListener("abort", abort);
            reject(error);
          },
        );
    });
  }
  try {
    while (!controller.signal.aborted) {
      await delay();
      const transaction = await query();
      if (controller.signal.aborted) break;
      onUpdate(transaction);
      if (transaction.status !== "CREATED") return "complete";
    }
    return "timeout";
  } catch (error) {
    if (expired && !signal.aborted) return "timeout";
    throw error;
  } finally {
    clearTimeout(deadline);
    signal.removeEventListener("abort", cancel);
  }
}
