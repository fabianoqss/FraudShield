import { CircleAlert, CircleCheck, CircleX, Clock3, Eye, EyeOff, Info, LoaderCircle } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { useId, useState } from "react";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";
import { ApiError } from "../api/client";
import { statusLabels } from "../api/types";
import type { TransactionStatus } from "../api/types";

export const focusRing = "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-400";
export const textLink = `rounded-sm font-medium text-emerald-400 underline-offset-4 hover:text-emerald-300 hover:underline ${focusRing}`;
export const fieldLabel = "block text-sm font-medium text-slate-300";
export const fieldControl = "block min-h-11 w-full min-w-0 rounded-xl border border-slate-500 bg-[#0B0F14] px-3.5 py-2.5 text-base text-slate-100 placeholder:text-slate-400 transition-colors enabled:hover:border-slate-400 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-emerald-400 aria-invalid:border-rose-400 aria-invalid:focus-visible:outline-rose-400 disabled:opacity-60 sm:text-sm";
export const insetSurface = "min-w-0 rounded-xl border border-slate-700/80 bg-[#0D1219]";

export function FraudShieldLogo({ size = "md" }: { size?: "md" | "lg" }) {
  return (
    <span className="inline-flex items-center gap-3 select-none">
      <span className={`grid shrink-0 place-items-center rounded-xl border border-emerald-500/30 bg-linear-to-br from-emerald-500/20 to-teal-600/10 shadow-[0_0_20px_rgba(16,185,129,0.18)] ${size === "lg" ? "size-11" : "size-9"}`}>
        <svg aria-hidden="true" viewBox="0 0 36 36" fill="none" className="size-6">
          <path
            d="M18 3L5.5 8.2V17.4C5.5 25.3 10.8 32.5 18 34.5C25.2 32.5 30.5 25.3 30.5 17.4V8.2L18 3Z"
            stroke="#10B981"
            strokeWidth="2.2"
            strokeLinejoin="round"
            fill="#10B981"
            fillOpacity=".08"
          />
          <path
            d="M9.5 18.5H13.5L15.8 13.5L19.8 23.5L22.2 18.5H26.5"
            stroke="#34D399"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <circle cx="26.5" cy="18.5" r="1.5" fill="#10B981" />
        </svg>
      </span>
      <span className="flex flex-col text-left">
        <span className="text-xl leading-none font-bold tracking-tight text-slate-100">
          Fraud<span className="text-emerald-400">Shield</span>
        </span>
        <span className="mt-1 font-mono text-[10px] tracking-widest text-slate-400 uppercase">
          Security Engine
        </span>
      </span>
    </span>
  );
}

export function Card({
  children,
  className = "",
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`min-w-0 rounded-xl border border-slate-700/70 bg-[#141B24] p-5 shadow-lg shadow-black/20 sm:p-8 ${className}`}>
      {children}
    </section>
  );
}

export function PageHeading({
  children,
  icon: Icon,
  description,
}: {
  children: ReactNode;
  icon: LucideIcon;
  description?: string;
}) {
  return (
    <div className="flex items-start gap-3.5">
      <span className="grid size-10 shrink-0 place-items-center rounded-xl border border-emerald-400/25 bg-emerald-400/10 text-emerald-300">
        <Icon aria-hidden="true" className="size-5" />
      </span>
      <div className="min-w-0">
        <h1 className="text-2xl font-bold tracking-tight text-slate-100">{children}</h1>
        {description && <p className="mt-1.5 text-sm leading-relaxed text-slate-400">{description}</p>}
      </div>
    </div>
  );
}

const buttonVariants = {
  primary: "border-transparent bg-linear-to-r from-teal-400 to-emerald-400 text-[#0B0F14] shadow-[0_8px_24px_rgba(16,185,129,0.18)] enabled:hover:from-teal-300 enabled:hover:to-emerald-300",
  secondary: "border-slate-500 bg-[#0D1219] text-slate-200 enabled:hover:border-emerald-400 enabled:hover:text-emerald-300",
  danger: "border-rose-400/60 bg-rose-500/10 text-rose-200 enabled:hover:bg-rose-500/20",
};

export function Button({
  children,
  className = "",
  variant = "primary",
  icon: Icon,
  busy = false,
  type = "button",
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: keyof typeof buttonVariants;
  icon?: LucideIcon;
  busy?: boolean;
}) {
  return (
    <button
      {...props}
      type={type}
      disabled={disabled || busy}
      className={`inline-flex min-h-11 max-w-full cursor-pointer items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-center text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-55 ${focusRing} ${buttonVariants[variant]} ${className}`}
    >
      {busy && <LoaderCircle aria-hidden="true" className="size-4 shrink-0 motion-safe:animate-spin" />}
      <span className="min-w-0">{children}</span>
      {!busy && Icon && <Icon aria-hidden="true" className="size-4 shrink-0" />}
    </button>
  );
}

export function FieldError({ id, children }: { id: string; children: ReactNode }) {
  return (
    <p id={id} className="flex items-start gap-1.5 text-xs leading-relaxed text-rose-300 [overflow-wrap:anywhere]">
      <CircleAlert aria-hidden="true" className="mt-0.5 size-3.5 shrink-0" />
      {children}
    </p>
  );
}

export function Field({
  label,
  error,
  icon: Icon,
  hint,
  revealPassword = false,
  id: suppliedId,
  type,
  className = "",
  "aria-describedby": describedBy,
  "aria-invalid": invalid,
  ...props
}: InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  icon?: LucideIcon;
  hint?: ReactNode;
  revealPassword?: boolean;
}) {
  const generatedId = useId();
  const id = suppliedId ?? generatedId;
  const [revealedFor, setRevealedFor] = useState<string>();
  const canReveal = type === "password" && revealPassword;
  const passwordScope = props.autoComplete ?? "";
  const visible = canReveal && revealedFor === passwordScope;
  const description = [
    error && `${id}-error`,
    hint && `${id}-hint`,
    describedBy,
  ].filter(Boolean).join(" ");
  return (
    <div className="min-w-0 space-y-1.5">
      <label className={fieldLabel} htmlFor={id}>{label}</label>
      <div className="relative">
        {Icon && <Icon aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-3.5 my-auto size-4 text-slate-400" />}
        <input
          {...props}
          id={id}
          type={visible ? "text" : type}
          spellCheck={type === "password" ? false : props.spellCheck}
          autoCapitalize={type === "password" ? "none" : props.autoCapitalize}
          aria-invalid={error ? true : (invalid ?? false)}
          aria-describedby={description || undefined}
          className={`${fieldControl} ${Icon ? "pl-10" : ""} ${canReveal ? "pr-12" : ""} ${className}`}
        />
        {canReveal && (
          <button
            type="button"
            disabled={props.disabled}
            aria-label={visible ? "Hide password" : "Show password"}
            aria-controls={id}
            className={`absolute inset-y-0 right-0 grid w-11 cursor-pointer place-items-center rounded-xl text-slate-400 enabled:hover:text-emerald-300 disabled:cursor-not-allowed ${focusRing}`}
            onClick={() => setRevealedFor(visible ? undefined : passwordScope)}
          >
            {visible ? <EyeOff aria-hidden="true" className="size-4" /> : <Eye aria-hidden="true" className="size-4" />}
          </button>
        )}
      </div>
      {error && <FieldError id={`${id}-error`}>{error}</FieldError>}
      {hint && <p className="text-xs leading-relaxed text-slate-400" id={`${id}-hint`}>{hint}</p>}
    </div>
  );
}

const alertVariants = {
  error: { icon: CircleAlert, style: "border-rose-400/45 bg-rose-400/10 text-rose-200" },
  success: { icon: CircleCheck, style: "border-emerald-400/40 bg-emerald-400/10 text-emerald-200" },
  info: { icon: Info, style: "border-slate-600 bg-[#0D1219] text-slate-300" },
};

export function Notice({ children, tone = "info" }: {
  children: ReactNode;
  tone?: keyof typeof alertVariants;
}) {
  const { icon: Icon, style } = alertVariants[tone];
  return (
    <div role={tone === "error" ? "alert" : "status"} className={`flex items-start gap-2.5 rounded-xl border px-3.5 py-3 text-sm leading-relaxed [overflow-wrap:anywhere] ${style}`}>
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <div className="min-w-0">{children}</div>
    </div>
  );
}

const transferStatusStyles = {
  CREATED: { icon: Clock3, style: "border-slate-500 bg-slate-800 text-slate-200" },
  APPROVED: { icon: CircleCheck, style: "border-emerald-400/40 bg-emerald-400/10 text-emerald-300" },
  FLAGGED: { icon: CircleAlert, style: "border-amber-400/40 bg-amber-400/10 text-amber-300" },
  DENIED: { icon: CircleX, style: "border-rose-400/40 bg-rose-400/10 text-rose-300" },
};

export function TransferStatus({ status }: { status: TransactionStatus }) {
  const { icon: Icon, style } = transferStatusStyles[status] ?? transferStatusStyles.CREATED;
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs font-semibold ${style}`}>
      <Icon aria-hidden="true" className="size-3.5 shrink-0" />
      {statusLabels[status]}
    </span>
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
      : "Couldn't complete the request.";
  if (error instanceof ApiError) {
    const messages: Record<number, string> = {
      403: "You don't have permission to access this account or perform this action.",
      429: "Too many sign-in attempts. Wait 15 minutes and try again.",
      503: "Service temporarily unavailable. Try again later.",
    };
    message =
      error.status === 404
        ? (notFoundMessage ?? message)
        : (messages[error.status] ?? message);
  }
  return <Notice tone="error">{message}</Notice>;
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
    throw new Error("Enter an amount with up to two decimal places.");
  const amount = Number(value.trim().replace(",", "."));
  if (
    !Number.isFinite(amount) ||
    amount <= 0 ||
    !Number.isSafeInteger(Math.round(amount * 100))
  )
    throw new Error(
      "Enter an amount greater than zero and within the allowed limit.",
    );
  return amount;
}
