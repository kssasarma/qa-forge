import type { TestCase, TestLayer } from '../api/qaForgeApi';

const LAYERS: TestLayer[] = ['PLAYWRIGHT', 'REST_ASSURED', 'DB_VALIDATION'];
const LAYER_LABELS: Record<TestLayer, string> = {
  PLAYWRIGHT: 'Playwright',
  REST_ASSURED: 'RestAssured',
  DB_VALIDATION: 'DB Validation',
};

/**
 * Which user flows have Playwright / RestAssured / DB validation coverage (Persona C, PRD
 * §4). This is a presence/identity question, not a magnitude one, so it's a table with a
 * covered/not-covered dot per cell rather than a chart (dataviz skill's "choosing a form").
 */
export default function CoverageMap({ tests }: { tests: TestCase[] }) {
  const flows = Array.from(new Set(tests.map((t) => t.userFlow).filter(Boolean))).sort();

  if (flows.length === 0) {
    return <p style={{ color: 'var(--text-secondary)' }}>No active tests yet.</p>;
  }

  const covered = (flow: string, layer: TestLayer) =>
    tests.some((t) => t.userFlow === flow && t.layer === layer);

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b" style={{ color: 'var(--text-muted)', borderColor: 'var(--gridline)' }}>
            <th className="py-2 pr-4 font-medium">User Flow</th>
            {LAYERS.map((layer) => (
              <th key={layer} className="py-2 pr-4 text-center font-medium">
                {LAYER_LABELS[layer]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {flows.map((flow) => (
            <tr key={flow} className="border-b" style={{ borderColor: 'var(--gridline)' }}>
              <td className="py-2 pr-4">{flow}</td>
              {LAYERS.map((layer) => (
                <td key={layer} className="py-2 pr-4 text-center">
                  {covered(flow, layer) ? (
                    <span aria-label={`${LAYER_LABELS[layer]} covered`} style={{ color: 'var(--status-good)' }}>
                      ●
                    </span>
                  ) : (
                    <span aria-label={`${LAYER_LABELS[layer]} not covered`} style={{ color: 'var(--gridline)' }}>
                      ○
                    </span>
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
