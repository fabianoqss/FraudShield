import { Activity, ArrowRight, CalendarDays, Fingerprint, KeyRound, Lock, Mail, Server, Shield, ShieldCheck, Sparkles, UserRound } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, NavLink } from "react-router";
import { api, ApiError } from "../api/client";
import { useSession } from "../auth/useSession";
import {
  Button,
  Card,
  ErrorNotice,
  Field,
  FraudShieldLogo,
  Notice,
  PageHeading,
  fieldErrors,
  focusRing,
  formValues,
  insetSurface,
  textLink,
} from "../components/Forms";

const tagline = "Simulated payments with fraud detection.";
const passwordRequirements = "Use at least 8 characters, including an uppercase letter, a lowercase letter, a number, and a special character.";
const segments = [
  { to: "/login", label: "Sign in" },
  { to: "/register", label: "Sign up" },
];
const features = [
  { icon: Activity, title: "Transfer screening", description: "Follow transfers from submission to approval or review." },
  { icon: Server, title: "Transaction audit trail", description: "Review recorded events for each transfer in your statement." },
  { icon: KeyRound, title: "Pix key management", description: "Register Pix keys and verify recipients before sending funds." },
];
const flow = [
  { title: "Recipient", description: "Pix key lookup" },
  { title: "Screening", description: "Risk assessment" },
  { title: "Decision", description: "Transfer status" },
];

function AuthBrandPanel() {
  return (
    <aside className="relative hidden w-[52%] shrink-0 flex-col justify-between overflow-hidden border-r border-slate-800 bg-[#0D1219] p-12 lg:flex xl:w-[54%] xl:p-16">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,#1e293b_1px,transparent_1px),linear-gradient(to_bottom,#1e293b_1px,transparent_1px)] bg-size-[40px_40px] opacity-20" />
      <div aria-hidden="true" className="pointer-events-none absolute -top-32 -left-32 size-[520px] rounded-full bg-emerald-500/10 blur-[120px]" />
      <div aria-hidden="true" className="pointer-events-none absolute right-0 bottom-0 size-[420px] rounded-full bg-teal-600/10 blur-[110px]" />
      <div className="relative flex items-center justify-between gap-4">
        <FraudShieldLogo size="lg" />
        <span className="inline-flex items-center gap-2 rounded-full border border-emerald-400/25 bg-emerald-500/10 px-3 py-1.5 text-xs font-medium text-emerald-300">
          <span aria-hidden="true" className="size-2 rounded-full bg-emerald-400" />
          Sandbox
        </span>
      </div>
      <div className="relative my-auto max-w-xl py-8">
        <p className="mb-5 inline-flex items-center gap-2 rounded-md border border-slate-700 bg-slate-900/90 px-3 py-1 font-mono text-xs text-slate-300 uppercase">
          <ShieldCheck aria-hidden="true" className="size-3.5 text-emerald-400" />
          Risk and payment sandbox
        </p>
        <p className="text-3xl leading-tight font-bold tracking-tight text-slate-100 xl:text-4xl">{tagline}</p>
        <p className="mt-4 max-w-prose leading-relaxed text-slate-400">
          Explore Pix deposits and transfers, check their status, and review the events behind each decision.
        </p>
        <ul className="mt-8 space-y-4">
          {features.map(({ icon: Icon, title, description }) => (
            <li key={title} className="flex items-start gap-3.5">
              <span className="grid size-9 shrink-0 place-items-center rounded-lg border border-slate-700/80 bg-[#141B24] text-teal-300">
                <Icon aria-hidden="true" className="size-4" />
              </span>
              <div>
                <p className="text-sm font-semibold text-slate-200">{title}</p>
                <p className="mt-0.5 text-xs leading-relaxed text-slate-400">{description}</p>
              </div>
            </li>
          ))}
        </ul>
        <div className="mt-9 rounded-xl border border-slate-700/70 bg-[#141B24]/90 p-5 shadow-[0_20px_50px_rgba(0,0,0,0.35)]">
          <p className="flex items-center gap-2 border-b border-slate-700/70 pb-3.5 font-mono text-xs font-semibold tracking-wide text-slate-300 uppercase">
            <Activity aria-hidden="true" className="size-4 text-emerald-400" />
            Simulated transfer flow
          </p>
          <ol className="grid grid-cols-3 gap-2 pt-4 xl:gap-3">
            {flow.map(({ title, description }) => (
              <li className={`${insetSurface} p-2.5 xl:p-3`} key={title}>
                <p className="text-xs font-semibold text-emerald-300">{title}</p>
                <p className="mt-1 text-xs leading-snug text-slate-400">{description}</p>
              </li>
            ))}
          </ol>
        </div>
      </div>
      <p className="relative text-xs text-slate-400">FraudShield Risk &amp; Payment Sandbox</p>
    </aside>
  );
}

const strengthLevels = [
  { label: "Weak", bar: "bg-rose-400", text: "text-rose-300" },
  { label: "Fair", bar: "bg-amber-400", text: "text-amber-300" },
  { label: "Strong", bar: "bg-teal-400", text: "text-teal-300" },
  { label: "Very strong", bar: "bg-emerald-400", text: "text-emerald-300" },
];

function PasswordStrength({ password, id }: { password: string; id: string }) {
  const variety = [/[A-Z]/, /[a-z]/, /[0-9]/, /[^A-Za-z0-9\s]/].filter((pattern) => pattern.test(password)).length;
  const score = password.length < 8
    ? Number(password.length > 0)
    : 1 + Number(variety >= 3) + Number(variety === 4) + Number(variety === 4 && password.length >= 12);
  const strength = strengthLevels[score - 1];
  return (
    <div className="space-y-1.5">
      <div aria-hidden="true" className="grid grid-cols-4 gap-1.5">
        {strengthLevels.map(({ label }, index) => (
          <span key={label} className={`h-1.5 rounded-full transition-colors ${strength && index < score ? strength.bar : "bg-slate-700"}`} />
        ))}
      </div>
      <div className="flex flex-wrap justify-between gap-x-3 gap-y-1 text-xs leading-relaxed">
        <p id={id} className="min-w-0 flex-1 basis-40 text-slate-400">{passwordRequirements}</p>
        <span role="status" className={`font-semibold ${strength?.text ?? ""}`}>
          {strength && <><span className="sr-only">Password strength: </span>{strength.label}</>}
        </span>
      </div>
    </div>
  );
}

export function AuthPage({
  register = false,
  logoutMessage,
}: {
  register?: boolean;
  logoutMessage?: string;
}) {
  const { user, ready } = useSession();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [registered, setRegistered] = useState(false);
  const [password, setPassword] = useState("");
  if (ready && user) return <Navigate to="/" replace />;
  const fields = fieldErrors(error);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const values = formValues(event.currentTarget);
    setBusy(true);
    setError(undefined);
    try {
      if (register && !registered) {
        await api.request("/auth/register", {
          method: "POST",
          public: true,
          body: { ...values, cpf: values.cpf?.replace(/\D/g, "") },
        });
        setRegistered(true);
      } else await api.login(values.email ?? "", values.password ?? "");
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.status === 401
          ? new Error("Incorrect email or password.")
          : cause,
      );
    } finally {
      setBusy(false);
    }
  }
  const isRegister = register && !registered;
  const submitLabel = busy ? "Please wait…" : isRegister ? "Register" : "Sign in";
  return (
    <div className="flex min-h-dvh flex-col bg-[#0B0F14] lg:flex-row">
      <AuthBrandPanel />
      <main className="relative flex min-w-0 flex-1 flex-col justify-between px-5 py-8 sm:px-8 lg:px-12 xl:px-16">
        <div aria-hidden="true" className="pointer-events-none absolute inset-x-0 top-0 h-56 bg-[radial-gradient(ellipse_at_top,rgba(16,185,129,0.08),transparent_70%)]" />
        <header className="relative flex flex-col items-center pt-2 pb-6 text-center lg:hidden">
          <FraudShieldLogo />
          <p className="mt-3 text-sm text-slate-400">{tagline}</p>
        </header>
        <div aria-hidden="true" className="hidden lg:block" />
        <div className="relative mx-auto my-auto w-full max-w-[440px]">
          {logoutMessage && <div className="mb-4"><Notice>{logoutMessage}</Notice></div>}
          {!ready ? <Notice>Restoring session…</Notice> : (
            <>
              <nav aria-label="Authentication" className="mb-6 grid grid-cols-2 gap-1 rounded-xl border border-slate-700/80 bg-[#141B24] p-1">
                {segments.map(({ to, label }) => (
                  <NavLink
                    key={to}
                    to={to}
                    end
                    className={`rounded-lg border border-transparent px-2 py-2.5 text-center text-sm font-medium text-slate-400 transition-colors hover:text-slate-100 aria-[current=page]:border-slate-600 aria-[current=page]:bg-[#1A222E] aria-[current=page]:text-slate-100 aria-[current=page]:shadow-sm ${focusRing}`}
                  >
                    {label}
                  </NavLink>
                ))}
              </nav>
              <Card className="space-y-5 shadow-[0_24px_60px_rgba(0,0,0,0.45)]">
                <div>
                  <h1 className="text-2xl font-bold tracking-tight text-slate-100">{isRegister ? "Sign up" : "Sign in"}</h1>
                  <p className="mt-1.5 text-sm leading-relaxed text-slate-400">
                    {isRegister ? "Create your account to explore simulated payments." : "Access your payments and fraud monitoring dashboard."}
                  </p>
                </div>
                {registered && <Notice tone="success">Registration complete. Sign in with your email and password.</Notice>}
                <form onSubmit={submit} aria-busy={busy} className="space-y-4">
                  <fieldset disabled={busy} className="min-w-0 space-y-4">
                    {isRegister && (
                      <Field
                        name="fullName"
                        label="Full name"
                        icon={UserRound}
                        autoComplete="name"
                        placeholder="Jane Doe"
                        required
                        error={fields.fullName}
                      />
                    )}
                    <Field
                      name="email"
                      type="email"
                      label="Email"
                      icon={Mail}
                      autoComplete="email"
                      placeholder="you@example.com"
                      required
                      error={fields.email}
                    />
                    {isRegister && (
                      <div className="grid gap-4 sm:grid-cols-2">
                        <Field
                          name="cpf"
                          label="CPF"
                          icon={Fingerprint}
                          inputMode="numeric"
                          placeholder="000.000.000-00"
                          required
                          error={fields.cpf}
                        />
                        <Field
                          name="birthDate"
                          type="date"
                          label="Date of birth"
                          icon={CalendarDays}
                          required
                          error={fields.birthDate}
                          hint="You must be at least 18 years old."
                        />
                      </div>
                    )}
                    <Field
                      name="password"
                      type="password"
                      label="Password"
                      icon={Lock}
                      revealPassword
                      autoComplete={isRegister ? "new-password" : "current-password"}
                      placeholder={isRegister ? "At least 8 characters" : "••••••••"}
                      minLength={isRegister ? 8 : undefined}
                      required
                      aria-describedby={isRegister ? "register-password-requirements" : undefined}
                      onChange={isRegister ? (event) => setPassword(event.target.value) : undefined}
                      error={fields.password}
                    />
                    {isRegister && <PasswordStrength password={password} id="register-password-requirements" />}
                    <Button type="submit" busy={busy} icon={isRegister ? Sparkles : ArrowRight} className="w-full">
                      {submitLabel}
                    </Button>
                  </fieldset>
                  <ErrorNotice error={error} />
                </form>
                <span role="status" className="sr-only">{busy ? submitLabel : ""}</span>
                <p className="text-center text-sm text-slate-400">
                  {!isRegister && "New to FraudShield? "}
                  <Link className={textLink} to={isRegister ? "/login" : "/register"}>
                    {isRegister ? "I already have an account" : "Sign up"}
                  </Link>
                </p>
              </Card>
            </>
          )}
        </div>
        <footer className="relative pt-8 pb-2 text-center">
          <p className="inline-flex items-center justify-center gap-2 rounded-full border border-slate-700/80 bg-[#141B24] px-3.5 py-1.5 text-xs text-slate-400">
            <Shield aria-hidden="true" className="size-3.5 shrink-0 text-emerald-400" />
            Portfolio project. All transactions are simulated.
          </p>
        </footer>
      </main>
    </div>
  );
}

export function PasswordPage() {
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const fields = fieldErrors(error);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = formValues(event.currentTarget);
    setError(undefined);
    if (values.newPassword !== values.confirmPassword) {
      setError(new Error("The new passwords don't match."));
      return;
    }
    setBusy(true);
    try {
      await api.request("/auth/password", {
        method: "PUT",
        body: values,
        keepSessionOnUnauthorized: true,
      });
      await api.logout().catch(() => undefined);
    } catch (cause) {
      setError(
        cause instanceof ApiError && cause.status === 401
          ? new Error("Your current password is incorrect.")
          : cause,
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <Card className="max-w-xl space-y-6">
      <PageHeading icon={Lock} description="You'll need to sign in again after changing your password.">
        Change password
      </PageHeading>
      <form onSubmit={submit} className="space-y-4">
        <fieldset disabled={busy} className="min-w-0 space-y-4">
          <Field
            name="currentPassword"
            label="Current password"
            type="password"
            icon={Lock}
            autoComplete="current-password"
            required
            error={fields.currentPassword}
          />
          <Field
            name="newPassword"
            label="New password"
            type="password"
            icon={KeyRound}
            autoComplete="new-password"
            minLength={8}
            required
            error={fields.newPassword}
            hint="Use an uppercase letter, a lowercase letter, a number, and a special character."
          />
          <Field
            name="confirmPassword"
            label="Confirm new password"
            type="password"
            icon={ShieldCheck}
            autoComplete="new-password"
            minLength={8}
            required
            error={fields.confirmPassword}
          />
          <Button type="submit" busy={busy} icon={ArrowRight}>
            {busy ? "Saving…" : "Change password"}
          </Button>
        </fieldset>
        <ErrorNotice error={error} />
      </form>
    </Card>
  );
}
