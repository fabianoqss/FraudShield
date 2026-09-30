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
  if (!ready) return <p role="status">Restaurando sessão…</p>;
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
          ? new Error("E-mail ou senha incorretos.")
          : cause,
      );
    } finally {
      setBusy(false);
    }
  }
  const isRegister = register && !registered;
  return (
    <section className="auth-card">
      <h1>{isRegister ? "Criar cadastro" : "Entrar"}</h1>
      {registered && (
        <p role="status">Cadastro criado. Entre com seu e-mail e senha.</p>
      )}
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          {isRegister && (
            <Field
              name="fullName"
              label="Nome completo"
              autoComplete="name"
              required
              error={fields.fullName}
            />
          )}
          <Field
            name="email"
            type="email"
            label="E-mail"
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
                label="Data de nascimento"
                required
                error={fields.birthDate}
              />
              <p className="hint">É necessário ter 18 anos ou mais.</p>
            </>
          )}
          <Field
            name="password"
            type="password"
            label="Senha"
            autoComplete={isRegister ? "new-password" : "current-password"}
            minLength={isRegister ? 8 : undefined}
            required
            error={fields.password}
          />
          {isRegister && (
            <p className="hint">
              Use pelo menos 8 caracteres, com maiúscula, minúscula, número e
              caractere especial.
            </p>
          )}
          <button type="submit">
            {busy ? "Aguarde…" : isRegister ? "Cadastrar" : "Entrar"}
          </button>
        </fieldset>
        <ErrorNotice error={error} />
      </form>
      <Link to={isRegister ? "/login" : "/cadastro"}>
        {isRegister ? "Já tenho cadastro" : "Criar cadastro"}
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
      setError(new Error("As novas senhas não coincidem."));
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
          ? new Error("A senha atual está incorreta.")
          : cause,
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <section>
      <h1>Trocar senha</h1>
      <p>Após a alteração, você precisará entrar novamente.</p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          <Field
            name="currentPassword"
            label="Senha atual"
            type="password"
            autoComplete="current-password"
            required
            error={fields.currentPassword}
          />
          <Field
            name="newPassword"
            label="Nova senha"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            error={fields.newPassword}
          />
          <p className="hint">
            Use maiúscula, minúscula, número e caractere especial.
          </p>
          <Field
            name="confirmPassword"
            label="Confirme a nova senha"
            type="password"
            autoComplete="new-password"
            minLength={8}
            required
            error={fields.confirmPassword}
          />
          <button>{busy ? "Salvando…" : "Alterar senha"}</button>
        </fieldset>
        <ErrorNotice error={error} />
      </form>
    </section>
  );
}
