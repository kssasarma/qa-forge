import { useEffect, useState, type ReactNode } from 'react';
import { fetchRuns, fetchTests, type RunSummary, type TestCase } from '../api/qaForgeApi';
import { useRepository } from '../hooks/useRepository';
import RepositoryInput from '../components/RepositoryInput';
import RunHistoryChart from '../components/RunHistoryChart';
import StatusBadge from '../components/StatusBadge';

/** Persona C (tech lead) landing view: recent pass-rate trend + headline coverage numbers. */
export default function DashboardPage() {
  const [repository, setRepository] = useRepository();
  const [tests, setTests] = useState<TestCase[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!repository) return;
    setError(null);
    Promise.all([
      fetchTests({ repository, size: 100 }),
      fetchRuns({ repository, size: 20 }),
    ])
      .then(([testsPage, runsPage]) => {
        setTests(testsPage.tests);
        setRuns(runsPage.runs);
      })
      .catch((e) => setError(String(e)));
  }, [repository]);

  const latestRun = runs[0];

  return (
    <div className="flex flex-col gap-6">
      <RepositoryInput value={repository} onChange={setRepository} />

      {error && <p style={{ color: 'var(--status-critical)' }}>{error}</p>}

      {repository && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
            <StatTile label="Active tests" value={tests.length} />
            <StatTile
              label="Latest gate"
              value={latestRun ? <StatusBadge status={latestRun.gateResult ?? 'OPEN'} /> : '—'}
            />
            <StatTile
              label="Latest pass rate"
              value={latestRun ? `${latestRun.passRatePercent.toFixed(1)}%` : '—'}
            />
          </div>

          <section className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', background: 'var(--surface-1)' }}>
            <h2 className="mb-2 text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>
              Pass rate history
            </h2>
            <RunHistoryChart runs={runs} />
          </section>
        </>
      )}
    </div>
  );
}

function StatTile({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', background: 'var(--surface-1)' }}>
      <div className="text-xs" style={{ color: 'var(--text-muted)' }}>
        {label}
      </div>
      <div className="mt-1 text-2xl font-semibold tabular-nums">{value}</div>
    </div>
  );
}
