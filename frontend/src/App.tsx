import { Link, Route, Routes } from 'react-router';

export function App() {
  return (
    <main className="app-shell">
      <header>
        <Link className="brand" to="/">FraudShield</Link>
        <p>Pagamentos simulados com detecção de fraude.</p>
      </header>
      <Routes>
        <Route path="/" element={
          <section aria-labelledby="welcome-title">
            <h1 id="welcome-title">Bem-vindo ao FraudShield</h1>
            <p>Estamos preparando sua experiência de pagamentos.</p>
            <p>Projeto de portfólio. Todas as operações são simuladas.</p>
          </section>
        } />
        <Route path="*" element={
          <section>
            <h1>Página não encontrada</h1>
            <Link to="/">Voltar ao início</Link>
          </section>
        } />
      </Routes>
    </main>
  );
}
