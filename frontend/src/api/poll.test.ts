import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "./client";
import { pollTransaction } from "./poll";
import type { Transaction } from "./types";
const created: Transaction = {
  id: "tx",
  sourceAccountId: "a",
  destinationAccountId: "b",
  amount: 10,
  type: "PIX",
  status: "CREATED",
  createdAt: "2026-09-23T12:00:00Z",
};
beforeEach(() => vi.useFakeTimers());
afterEach(() => vi.useRealTimers());
it.each(["APPROVED", "DENIED", "FLAGGED"] as const)(
  "polls every 1.5 seconds and stops on %s",
  async (status) => {
    const request = vi
      .spyOn(api, "request")
      .mockResolvedValueOnce(created)
      .mockResolvedValueOnce({ ...created, status });
    const update = vi.fn();
    const result = pollTransaction("tx", new AbortController().signal, update);
    await vi.advanceTimersByTimeAsync(1499);
    expect(request).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1501);
    await expect(result).resolves.toBe("complete");
    await vi.advanceTimersByTimeAsync(30_000);
    expect(request).toHaveBeenCalledTimes(2);
    expect(update).toHaveBeenLastCalledWith(
      expect.objectContaining({ status }),
    );
  },
);
it("stops after 30 seconds while status is still pending", async () => {
  vi.spyOn(api, "request").mockResolvedValue(created);
  const result = pollTransaction("tx", new AbortController().signal, vi.fn());
  await vi.advanceTimersByTimeAsync(30_000);
  await expect(result).resolves.toBe("timeout");
  expect(vi.getTimerCount()).toBe(0);
});
it("cancels on navigation without making another request", async () => {
  const request = vi.spyOn(api, "request");
  const controller = new AbortController();
  const result = pollTransaction("tx", controller.signal, vi.fn());
  const assertion = expect(result).rejects.toMatchObject({
    name: "AbortError",
  });
  controller.abort();
  await assertion;
  await vi.advanceTimersByTimeAsync(30_000);
  expect(request).not.toHaveBeenCalled();
});
it("propagates endpoint errors instead of reporting a denied transfer", async () => {
  vi.spyOn(api, "request").mockRejectedValue(new Error("Unavailable"));
  const result = pollTransaction("tx", new AbortController().signal, vi.fn());
  const assertion = expect(result).rejects.toThrow("Unavailable");
  await vi.advanceTimersByTimeAsync(1500);
  await assertion;
  expect(vi.getTimerCount()).toBe(0);
});
it("enforces the deadline even when a shared refresh or request has not finished", async () => {
  vi.spyOn(api, "request").mockImplementation(
    () => new Promise(() => undefined),
  );
  const result = pollTransaction("tx", new AbortController().signal, vi.fn());
  await vi.advanceTimersByTimeAsync(30_000);
  await expect(result).resolves.toBe("timeout");
  expect(vi.getTimerCount()).toBe(0);
});
