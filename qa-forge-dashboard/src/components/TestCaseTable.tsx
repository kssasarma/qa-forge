import { Link } from 'react-router-dom';
import type { TestCase } from '../api/qaForgeApi';
import StatusBadge from './StatusBadge';

export default function TestCaseTable({ tests }: { tests: TestCase[] }) {
  if (tests.length === 0) {
    return <p style={{ color: 'var(--text-secondary)' }}>No test cases found.</p>;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b" style={{ color: 'var(--text-muted)', borderColor: 'var(--gridline)' }}>
            <th className="py-2 pr-4 font-medium">File</th>
            <th className="py-2 pr-4 font-medium">Layer</th>
            <th className="py-2 pr-4 font-medium">User Flow</th>
            <th className="py-2 pr-4 font-medium">Last Result</th>
            <th className="py-2 pr-4 font-medium">Runs</th>
          </tr>
        </thead>
        <tbody>
          {tests.map((test) => (
            <tr key={test.id} className="border-b" style={{ borderColor: 'var(--gridline)' }}>
              <td className="py-2 pr-4">
                <Link to={`/tests/${encodeURIComponent(test.fileName)}`} style={{ color: 'var(--series-1)' }}>
                  {test.fileName}
                </Link>
              </td>
              <td className="py-2 pr-4" style={{ color: 'var(--text-secondary)' }}>
                {test.layer}
              </td>
              <td className="py-2 pr-4" style={{ color: 'var(--text-secondary)' }}>
                {test.userFlow}
              </td>
              <td className="py-2 pr-4">
                {test.lastExecutionStatus ? <StatusBadge status={test.lastExecutionStatus} /> : '—'}
              </td>
              <td className="py-2 pr-4 tabular-nums" style={{ color: 'var(--text-secondary)' }}>
                {test.executionCount}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
