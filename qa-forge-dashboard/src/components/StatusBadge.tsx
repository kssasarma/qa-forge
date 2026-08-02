import type { ExecutionStatus, GateResult } from '../api/qaForgeApi';

type Status = ExecutionStatus | GateResult;

const STATUS_CONFIG: Record<Status, { label: string; color: string; icon: string }> = {
  PASSED: { label: 'Passed', color: 'var(--status-good)', icon: '✓' },
  OPEN: { label: 'Open', color: 'var(--status-good)', icon: '✓' },
  FAILED: { label: 'Failed', color: 'var(--status-critical)', icon: '✕' },
  BLOCKED: { label: 'Blocked', color: 'var(--status-critical)', icon: '✕' },
  ERROR: { label: 'Error', color: 'var(--status-critical)', icon: '✕' },
  SKIPPED: { label: 'Skipped', color: 'var(--status-warning)', icon: '○' },
};

/** Status is always icon + label, never color alone (dataviz skill's status-palette rule). */
export default function StatusBadge({ status }: { status: Status }) {
  const config = STATUS_CONFIG[status];
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium"
      style={{ borderColor: 'var(--border)', color: config.color }}
    >
      <span aria-hidden="true">{config.icon}</span>
      {config.label}
    </span>
  );
}
