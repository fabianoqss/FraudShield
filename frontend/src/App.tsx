import { useState } from "react";
import { Link, Navigate, NavLink, Outlet, Route, Routes } from "react-router";
import { api } from "./api/client";
import { useSession } from "./auth/useSession";
import { AccountsProvider } from "./auth/Accounts";
import { AuthPage, PasswordPage } from "./pages/Auth";
import { AccountPage, DepositPage } from "./pages/Account";
import { PixKeysPage } from "./pages/PixKeys";
import { TransferPage } from "./pages/Transfer";
import { StatementPage, TransactionPage } from "./pages/Statement";

function Protected() {
  const { user, ready } = useSession();
  if (!ready) return <p role="status">Restaurando sessão…</p>;
  if (!user) return <Navigate to="/login" replace />;
  return <Outlet />;
}
function AccountRoutes() {
  return (
    <AccountsProvider>
      <Outlet />
    </AccountsProvider>
  );
}

export function App() {
  const { user } = useSession();
  const [logoutMessage, setLogoutMessage] = useState("");
  const [loggingOut, setLoggingOut] = useState(false);
  async function logout() {
    setLoggingOut(true);
    setLogoutMessage("");
    try {
      await api.logout();
    } catch {
      setLogoutMessage(
        "Você saiu neste navegador, mas não foi possível confirmar a revogação no servidor.",
      );
    } finally {
      setLoggingOut(false);
    }
  }
  return (
    <main className="app-shell">
      <header>
        <Link className="brand" to="/">
          FraudShield
        </Link>
        <p>Pagamentos simulados com detecção de fraude.</p>
        {user && (
          <>
            <p>Olá, {user.fullName}</p>
            <nav aria-label="Menu principal">
              <NavLink to="/" end>
                Minha conta
              </NavLink>
              <NavLink to="/deposito">Depositar</NavLink>
              <NavLink to="/chaves">Minhas chaves PIX</NavLink>
              <NavLink to="/transferir">Transferir</NavLink>
              <NavLink to="/extrato">Extrato</NavLink>
              <NavLink to="/senha">Trocar senha</NavLink>
              <button
                className="secondary"
                disabled={loggingOut}
                onClick={() => void logout()}
              >
                {loggingOut ? "Saindo…" : "Sair"}
              </button>
            </nav>
          </>
        )}
      </header>
      {logoutMessage && !user && <p role="status">{logoutMessage}</p>}
      <Routes>
        <Route path="/login" element={<AuthPage key="login" />} />
        <Route
          path="/cadastro"
          element={<AuthPage key="register" register />}
        />
        <Route element={<Protected />}>
          <Route path="/senha" element={<PasswordPage />} />
          <Route path="/deposito" element={<DepositPage />} />
          <Route element={<AccountRoutes />}>
            <Route path="/" element={<AccountPage />} />
            <Route path="/chaves" element={<PixKeysPage />} />
            <Route path="/transferir" element={<TransferPage />} />
            <Route path="/extrato" element={<StatementPage />} />
            <Route path="/extrato/:id" element={<TransactionPage />} />
          </Route>
        </Route>
        <Route
          path="*"
          element={
            <section>
              <h1>Página não encontrada</h1>
              <Link to="/">Voltar ao início</Link>
            </section>
          }
        />
      </Routes>
      <footer>Projeto de portfólio. Todas as operações são simuladas.</footer>
    </main>
  );
}
