import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { fetchRun, fetchRuns, fetchTests, type RunItem, type TestCase } from '../api/qaForgeApi';
import { useRepository } from '../hooks/useRepository';
import StatusBadge from '../components/StatusBadge';

interface TimelineEntry {
  runId: string;
  createdAt: string;
  item: RunItem;
}

/** Per-test timeline (Persona C, PRD §4) — built client-side from recent runs since there's
 * no single "test execution history" endpoint; only run-level detail (PRD §12.6). */
export default function TestDetailPage() {
  const { fileName } = useParams<{ fileName: string }>();
  const [repository] = useRepository();
  const [testCase, setTestCase] = useState<TestCase | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!repository || !fileName) return;
    setLoading(true);
    setError(null);

    fetchTests({ repository, size: 100 })
      .then((page) => setTestCase(page.tests.find((t) => t.fileName === fileName) ?? null))
      .catch((e) => setError(String(e)));

    fetchRuns({ repository, size: 20 })
      .then(async (page) => {
        const details = await Promise.all(page.runs.map((run) => fetchRun(run.runId)));
        const entries = details.flatMap((detail) =>
          detail.items
            .filter((item) => item.fileName === fileName)
            .map((item) => ({ runId: detail.runId, createdAt: detail.createdAt, item })),
        );
        entries.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
        setTimeline(entries);
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  }, [repository, fileName]);

  if (!repository) {
    return <p style={{ color: 'var(--text-secondary)' }}>Select a repository on the Dashboard page first.</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <Link to="/coverage" style={{ color: 'var(--series-1)' }}>
        ← Back to coverage
      </Link>

      <h1 className="text-lg font-semibold">{fileName}</h1>

      {error && <p style={{ color: 'var(--status-critical)' }}>{error}</p>}

      {testCase && (
        <dl className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm sm:grid-cols-4">
          <Field label="Layer" value={testCase.layer} />
          <Field label="User Flow" value={testCase.userFlow} />
          <Field label="Status" value={testCase.status} />
          <Field label="PR" value={testCase.prNumber ?? '—'} />
        </dl>
      )}

      <section>
        <h2 className="mb-2 text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>
          Execution timeline
        </h2>
        {loading && <p style={{ color: 'var(--text-muted)' }}>Loading…</p>}
        {!loading && timeline.length === 0 && (
          <p style={{ color: 'var(--text-secondary)' }}>No execution history found in recent runs.</p>
        )}
        <ul className="flex flex-col gap-2">
          {timeline.map((entry) => (
            <li
              key={entry.runId}
              className="flex items-center justify-between rounded-lg border p-3 text-sm"
              style={{ borderColor: 'var(--border)', background: 'var(--surface-1)' }}
            >
              <span style={{ color: 'var(--text-secondary)' }}>{new Date(entry.createdAt).toLocaleString()}</span>
              <StatusBadge status={entry.item.status} />
              <span className="tabular-nums" style={{ color: 'var(--text-secondary)' }}>
                {entry.item.durationMs ?? '—'}ms
              </span>
              <span style={{ color: 'var(--text-secondary)' }}>{entry.item.selfHealed ? 'Self-healed' : ''}</span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs" style={{ color: 'var(--text-muted)' }}>
        {label}
      </dt>
      <dd>{value}</dd>
    </div>
  );
}
