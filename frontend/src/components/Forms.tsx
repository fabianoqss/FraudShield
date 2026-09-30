import { useId } from "react";
import type { InputHTMLAttributes } from "react";
import { ApiError } from "../api/client";

export function Field({
  label,
  error,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string }) {
  const id = useId();
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        {...props}
        id={id}
        aria-invalid={!!error}
        aria-describedby={error ? `${id}-error` : undefined}
      />
      {error && (
        <span className="field-error" id={`${id}-error`}>
          {error}
        </span>
      )}
    </div>
  );
}
export function ErrorNotice({
  error,
  notFoundMessage,
}: {
  error: unknown;
  notFoundMessage?: string;
}) {
  if (!error) return null;
  let message =
    error instanceof Error
      ? error.message
      : "Não foi possível concluir a operação.";
  if (error instanceof ApiError) {
    const messages: Record<number, string> = {
      403: "Você não tem permissão para acessar esta conta ou operação.",
      429: "Muitas tentativas de login. Aguarde 15 minutos e tente novamente.",
      503: "Serviço temporariamente indisponível. Tente novamente mais tarde.",
    };
    message =
      error.status === 404
        ? (notFoundMessage ?? message)
        : (messages[error.status] ?? message);
  }
  return (
    <p role="alert" className="error">
      {message}
    </p>
  );
}
export function fieldErrors(error: unknown) {
  return error instanceof ApiError ? error.fieldErrors : {};
}
export function formValues(form: HTMLFormElement) {
  return Object.fromEntries(
    Array.from(new FormData(form).entries(), ([key, value]) => [
      key,
      String(value),
    ]),
  );
}
export function parseAmount(value: string): number {
  if (!/^\d+(?:[.,]\d{1,2})?$/.test(value.trim()))
    throw new Error("Informe um valor com até duas casas decimais.");
  const amount = Number(value.trim().replace(",", "."));
  if (
    !Number.isFinite(amount) ||
    amount <= 0 ||
    !Number.isSafeInteger(Math.round(amount * 100))
  )
    throw new Error(
      "Informe um valor maior que zero e dentro do limite permitido.",
    );
  return amount;
}
