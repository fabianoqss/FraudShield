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
  if (!ready) return <p role="status">Restoring session…</p>;
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
        "You signed out in this browser, but we couldn't confirm that the server revoked your session.",
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
        <p>Simulated payments with fraud detection.</p>
        {user && (
          <>
            <p>Hello, {user.fullName}</p>
            <nav aria-label="Main menu">
              <NavLink to="/" end>
                My account
              </NavLink>
              <NavLink to="/deposit">Deposit</NavLink>
              <NavLink to="/pix-keys">My Pix keys</NavLink>
              <NavLink to="/transfer">Transfer</NavLink>
              <NavLink to="/statement">Statement</NavLink>
              <NavLink to="/password">Change password</NavLink>
              <button
                className="secondary"
                disabled={loggingOut}
                onClick={() => void logout()}
              >
                {loggingOut ? "Signing out…" : "Sign out"}
              </button>
            </nav>
          </>
        )}
      </header>
      {logoutMessage && !user && <p role="status">{logoutMessage}</p>}
      <Routes>
        <Route path="/login" element={<AuthPage key="login" />} />
        <Route
          path="/register"
          element={<AuthPage key="register" register />}
        />
        <Route element={<Protected />}>
          <Route path="/password" element={<PasswordPage />} />
          <Route path="/deposit" element={<DepositPage />} />
          <Route element={<AccountRoutes />}>
            <Route path="/" element={<AccountPage />} />
            <Route path="/pix-keys" element={<PixKeysPage />} />
            <Route path="/transfer" element={<TransferPage />} />
            <Route path="/statement" element={<StatementPage />} />
            <Route path="/statement/:id" element={<TransactionPage />} />
          </Route>
        </Route>
        <Route
          path="*"
          element={
            <section>
              <h1>Page not found</h1>
              <Link to="/">Back to home</Link>
            </section>
          }
        />
      </Routes>
      <footer>Portfolio project. All transactions are simulated.</footer>
    </main>
  );
}
