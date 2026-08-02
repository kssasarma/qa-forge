import { useEffect, useState } from 'react';
import { fetchRun, fetchRuns, type RunDetail, type RunSummary } from '../api/qaForgeApi';
import { useRepository } from '../hooks/useRepository';
import RepositoryInput from '../components/RepositoryInput';
import StatusBadge from '../components/StatusBadge';

/** Run rows expand in place to show their items — PRD §8 doesn't list a separate run-detail
 * page, so GET /api/v1/runs/{runId} (§12.6) is used for an inline expansion instead of a route. */
export default function RunsPage() {
  const [repository, setRepository] = useRepository();
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [expandedRunId, setExpandedRunId] = useState<string | null>(null);
  const [expandedDetail, setExpandedDetail] = useState<RunDetail | null>(null);

  useEffect(() => {
    if (!repository) return;
    setError(null);
    fetchRuns({ repository, size: 50 })
      .then((page) => setRuns(page.runs))
      .catch((e) => setError(String(e)));
  }, [repository]);

  function toggleRun(runId: string) {
    if (expandedRunId === runId) {
      setExpandedRunId(null);
      setExpandedDetail(null);
      return;
    }
    setExpandedRunId(runId);
    setExpandedDetail(null);
    fetchRun(runId)
      .then(setExpandedDetail)
      .catch((e) => setError(String(e)));
  }

  return (
    <div className="flex flex-col gap-6">
      <RepositoryInput value={repository} onChange={setRepository} />

      {error && <p style={{ color: 'var(--status-critical)' }}>{error}</p>}

      {repository && runs.length === 0 && !error && (
        <p style={{ color: 'var(--text-secondary)' }}>No runs yet for {repository}.</p>
      )}

      {runs.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead>
              <tr className="border-b" style={{ color: 'var(--text-muted)', borderColor: 'var(--gridline)' }}>
                <th className="py-2 pr-4 font-medium">Run</th>
                <th className="py-2 pr-4 font-medium">Type</th>
                <th className="py-2 pr-4 font-medium">PR</th>
                <th className="py-2 pr-4 font-medium">Gate</th>
                <th className="py-2 pr-4 font-medium">Pass rate</th>
                <th className="py-2 pr-4 font-medium">Created</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((run) => (
                <RunRow
                  key={run.runId}
                  run={run}
                  expanded={expandedRunId === run.runId}
                  detail={expandedRunId === run.runId ? expandedDetail : null}
                  onToggle={() => toggleRun(run.runId)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function RunRow({
  run,
  expanded,
  detail,
  onToggle,
}: {
  run: RunSummary;
  expanded: boolean;
  detail: RunDetail | null;
  onToggle: () => void;
}) {
  return (
    <>
      <tr
        className="cursor-pointer border-b"
        style={{ borderColor: 'var(--gridline)' }}
        onClick={onToggle}
        aria-expanded={expanded}
      >
        <td className="py-2 pr-4" style={{ color: 'var(--series-1)' }}>
          {run.runId.slice(0, 8)}
        </td>
        <td className="py-2 pr-4" style={{ color: 'var(--text-secondary)' }}>
          {run.runType}
        </td>
        <td className="py-2 pr-4" style={{ color: 'var(--text-secondary)' }}>
          {run.prNumber ?? '—'}
        </td>
        <td className="py-2 pr-4">{run.gateResult ? <StatusBadge status={run.gateResult} /> : '—'}</td>
        <td className="py-2 pr-4 tabular-nums">{run.passRatePercent.toFixed(1)}%</td>
        <td className="py-2 pr-4" style={{ color: 'var(--text-secondary)' }}>
          {new Date(run.createdAt).toLocaleString()}
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={6} className="pb-4" style={{ background: 'var(--page-plane)' }}>
            {!detail ? (
              <p className="px-2 py-2 text-xs" style={{ color: 'var(--text-muted)' }}>
                Loading…
              </p>
            ) : detail.items.length === 0 ? (
              <p className="px-2 py-2 text-xs" style={{ color: 'var(--text-muted)' }}>
                No items in this run.
              </p>
            ) : (
              <table className="w-full text-left text-xs">
                <thead>
                  <tr style={{ color: 'var(--text-muted)' }}>
                    <th className="py-1 pl-2 pr-4 font-medium">File</th>
                    <th className="py-1 pr-4 font-medium">Status</th>
                    <th className="py-1 pr-4 font-medium">Duration</th>
                    <th className="py-1 pr-4 font-medium">Self-healed</th>
                    <th className="py-1 pr-4 font-medium">Error</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.items.map((item) => (
                    <tr key={item.fileName}>
                      <td className="py-1 pl-2 pr-4">{item.fileName}</td>
                      <td className="py-1 pr-4">
                        <StatusBadge status={item.status} />
                      </td>
                      <td className="py-1 pr-4 tabular-nums" style={{ color: 'var(--text-secondary)' }}>
                        {item.durationMs ?? '—'}ms
                      </td>
                      <td className="py-1 pr-4" style={{ color: 'var(--text-secondary)' }}>
                        {item.selfHealed ? 'Yes' : 'No'}
                      </td>
                      <td className="py-1 pr-4" style={{ color: 'var(--status-critical)' }}>
                        {item.errorMessage ?? ''}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </td>
        </tr>
      )}
    </>
  );
}
