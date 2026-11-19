import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate } from "react-router";
import { api, ApiError } from "../api/client";
import { useSession } from "../auth/useSession";
import {
  ErrorNotice,
  Field,
  fieldErrors,
  formValues,
} from "../components/Forms";

export function AuthPage({ register = false }: { register?: boolean }) {
  const { user, ready } = useSession();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const [registered, setRegistered] = useState(false);
  if (!ready) return <p role="status">Restoring session…</p>;
  if (user) return <Navigate to="/" replace />;
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
  return (
    <section className="auth-card">
      <h1>{isRegister ? "Sign up" : "Sign in"}</h1>
      {registered && (
        <p role="status">Registration complete. Sign in with your email and password.</p>
      )}
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          {isRegister && (
            <Field
              name="fullName"
              label="Full name"
              autoComplete="name"
              required
              error={fields.fullName}
            />
          )}
          <Field
            name="email"
            type="email"
            label="Email"
            autoComplete="email"
            required
            error={fields.email}
          />
          {isRegister && (
            <>
              <Field
                name="cpf"
                label="CPF"
                inputMode="numeric"
                required
                error={fields.cpf}
              />
              <Field
                name="birthDate"
                type="date"
                label="Date of birth"
                required
                error={fields.birthDate}
              />
              <p className="hint">You must be at least 18 years old.</p>
            </>
          )}
          <Field
            name="password"
            type="password"
            label="Password"
            autoComplete={isRegister ? "new-password" : "current-password"}
            minLength={isRegister ? 8 : undefined}
            required
            error={fields.password}
          />
          {isRegister && (
            <p className="hint">
              Use at least 8 characters, including an uppercase letter, a lowercase letter, a number, and a special character.
            </p>
          )}
          <button type="submit">
            {busy ? "Please wait…" : isRegister ? "Register" : "Sign in"}
          </button>
        </fieldset>
        <ErrorNotice error={error} />
      </form>
      <Link to={isRegister ? "/login" : "/register"}>
        {isRegister ? "I already have an account" : "Sign up"}
      </Link>
    </section>
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
    <section>
      <h1>Change password</h1>
      <p>You'll need to sign in again after changing your password.</p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <Field
            name="currentPassword"
            label="Current password"
            type="password"
            autoComplete="current-password"
            required
            error={fields.currentPassword}
          />
          <Field
            name="newPassword"
            label="New password"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            error={fields.newPassword}
          />
          <p className="hint">
            Use an uppercase letter, a lowercase letter, a number, and a special character.
          </p>
          <Field
            name="confirmPassword"
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            error={fields.confirmPassword}
          />
          <button>{busy ? "Saving…" : "Change password"}</button>
        </fieldset>
        <ErrorNotice error={error} />
      </form>
    </section>
  );
}
