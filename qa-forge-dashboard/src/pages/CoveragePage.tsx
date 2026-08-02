import { useEffect, useState } from 'react';
import { fetchTests, type TestCase, type TestLayer } from '../api/qaForgeApi';
import { useRepository } from '../hooks/useRepository';
import RepositoryInput from '../components/RepositoryInput';
import CoverageMap from '../components/CoverageMap';
import TestCaseTable from '../components/TestCaseTable';

const LAYERS: (TestLayer | '')[] = ['', 'PLAYWRIGHT', 'REST_ASSURED', 'DB_VALIDATION'];

export default function CoveragePage() {
  const [repository, setRepository] = useRepository();
  const [layer, setLayer] = useState<TestLayer | ''>('');
  const [tests, setTests] = useState<TestCase[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!repository) return;
    setError(null);
    fetchTests({ repository, layer: layer || undefined, size: 100 })
      .then((page) => setTests(page.tests))
      .catch((e) => setError(String(e)));
  }, [repository, layer]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center gap-4">
        <RepositoryInput value={repository} onChange={setRepository} />
        <label className="flex items-center gap-2 text-sm">
          <span style={{ color: 'var(--text-secondary)' }}>Layer</span>
          <select
            value={layer}
            onChange={(e) => setLayer(e.target.value as TestLayer | '')}
            className="rounded border px-2 py-1 text-sm"
            style={{ borderColor: 'var(--border)', background: 'var(--surface-1)', color: 'var(--text-primary)' }}
          >
            {LAYERS.map((l) => (
              <option key={l} value={l}>
                {l || 'All'}
              </option>
            ))}
          </select>
        </label>
      </div>

      {error && <p style={{ color: 'var(--status-critical)' }}>{error}</p>}

      {repository && (
        <>
          <section className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', background: 'var(--surface-1)' }}>
            <h2 className="mb-2 text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>
              Coverage by user flow
            </h2>
            <CoverageMap tests={tests} />
          </section>

          <section className="rounded-lg border p-4" style={{ borderColor: 'var(--border)', background: 'var(--surface-1)' }}>
            <h2 className="mb-2 text-sm font-medium" style={{ color: 'var(--text-secondary)' }}>
              All active tests
            </h2>
            <TestCaseTable tests={tests} />
          </section>
        </>
      )}
    </div>
  );
}
