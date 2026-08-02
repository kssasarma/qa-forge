import { HashRouter, NavLink, Route, Routes } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import CoveragePage from './pages/CoveragePage';
import RunsPage from './pages/RunsPage';
import TestDetailPage from './pages/TestDetailPage';

// HashRouter (not BrowserRouter): Spring Boot serves the SPA as plain static resources
// (PRD §8's "Dashboard build integration" note) with no server-side fallback route wired for
// deep links like /coverage — hash-based routing keeps every navigation a request for '/'.
const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/coverage', label: 'Coverage' },
  { to: '/runs', label: 'Runs' },
];

export default function App() {
  return (
    <HashRouter>
      <div className="min-h-screen">
        <header className="border-b" style={{ borderColor: 'var(--border)', background: 'var(--surface-1)' }}>
          <div className="mx-auto flex max-w-6xl items-center gap-6 px-4 py-3">
            <span className="text-lg font-semibold">QA Forge</span>
            <nav className="flex gap-4">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `text-sm ${isActive ? 'font-semibold' : ''}`
                  }
                  style={({ isActive }) => ({
                    color: isActive ? 'var(--series-1)' : 'var(--text-secondary)',
                  })}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
        </header>

        <main className="mx-auto max-w-6xl px-4 py-6">
          <Routes>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/coverage" element={<CoveragePage />} />
            <Route path="/runs" element={<RunsPage />} />
            <Route path="/tests/:fileName" element={<TestDetailPage />} />
          </Routes>
        </main>
      </div>
    </HashRouter>
  );
}
