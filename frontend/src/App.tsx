import { ArrowDownToLine, ArrowRightLeft, KeyRound, Lock, LogOut, ReceiptText, Shield, Wallet } from "lucide-react";
import { useState } from "react";
import { Link, Navigate, NavLink, Outlet, Route, Routes } from "react-router";
import { api } from "./api/client";
import { useSession } from "./auth/useSession";
import { AccountsProvider } from "./auth/Accounts";
import { Button, Card, FraudShieldLogo, Notice, focusRing, textLink } from "./components/Forms";
import { AuthPage, PasswordPage } from "./pages/Auth";
import { AccountPage, DepositPage } from "./pages/Account";
import { PixKeysPage } from "./pages/PixKeys";
import { TransferPage } from "./pages/Transfer";
import { StatementPage, TransactionPage } from "./pages/Statement";

const navigation = [
  { to: "/", label: "My account", icon: Wallet },
  { to: "/deposit", label: "Deposit", icon: ArrowDownToLine },
  { to: "/pix-keys", label: "My Pix keys", icon: KeyRound },
  { to: "/transfer", label: "Transfer", icon: ArrowRightLeft },
  { to: "/statement", label: "Statement", icon: ReceiptText },
  { to: "/password", label: "Change password", icon: Lock },
];

function Protected() {
  const { user, ready } = useSession();
  if (!ready) return <Notice>Restoring session…</Notice>;
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
    <Routes>
      <Route path="/login" element={<AuthPage key="login" logoutMessage={logoutMessage} />} />
      <Route path="/register" element={<AuthPage key="register" register logoutMessage={logoutMessage} />} />
      <Route element={
        <div className="flex min-h-dvh flex-col">
          <header className="border-b border-slate-700/80 bg-[#0D1219]">
            <div className="mx-auto max-w-6xl px-4 sm:px-8">
              <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 py-5">
                <Link className={`rounded-xl ${focusRing}`} to="/" aria-label="FraudShield home">
                  <FraudShieldLogo />
                </Link>
                {user ? (
                  <div className="flex min-w-0 flex-wrap items-center gap-x-4 gap-y-2 text-sm">
                    <p className="min-w-0 text-slate-300 [overflow-wrap:anywhere]">Hello, {user.fullName}</p>
                    <Button variant="secondary" icon={LogOut} busy={loggingOut} onClick={() => void logout()}>
                      {loggingOut ? "Signing out…" : "Sign out"}
                    </Button>
                  </div>
                ) : (
                  <p className="text-sm text-slate-400">Simulated payments with fraud detection.</p>
                )}
              </div>
              {user && (
                <nav aria-label="Main menu" className="grid grid-cols-2 gap-1.5 border-t border-slate-800 py-3 sm:grid-cols-3 lg:flex lg:flex-wrap">
                  {navigation.map(({ to, label, icon: Icon }) => (
                    <NavLink
                      key={to}
                      to={to}
                      end={to === "/"}
                      className={`flex min-h-11 items-center gap-2 rounded-xl border border-transparent px-2.5 py-2 text-sm font-medium text-slate-300 transition-colors hover:bg-slate-800/70 hover:text-slate-100 aria-[current=page]:border-emerald-400/30 aria-[current=page]:bg-emerald-400/10 aria-[current=page]:text-emerald-300 lg:px-3.5 ${focusRing}`}
                    >
                      <Icon aria-hidden="true" className="size-4 shrink-0" />
                      {label}
                    </NavLink>
                  ))}
                </nav>
              )}
            </div>
          </header>
          <main className="mx-auto w-full max-w-6xl flex-1 space-y-5 px-4 py-6 sm:px-8 sm:py-10">
            {logoutMessage && !user && <Notice>{logoutMessage}</Notice>}
            <Outlet />
          </main>
          <footer className="mx-auto flex w-full max-w-6xl items-center gap-2 px-4 pb-6 text-xs text-slate-400 sm:px-8">
            <Shield aria-hidden="true" className="size-4 shrink-0 text-emerald-400" />
            Portfolio project. All transactions are simulated.
          </footer>
        </div>
      }>
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
            <Card className="space-y-3">
              <h1 className="text-2xl font-bold tracking-tight">Page not found</h1>
              <Link className={textLink} to="/">Back to home</Link>
            </Card>
          }
        />
      </Route>
    </Routes>
  );
}
