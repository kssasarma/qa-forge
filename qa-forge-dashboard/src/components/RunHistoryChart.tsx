import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { RunSummary } from '../api/qaForgeApi';

/**
 * Pass rate over time — a single series (magnitude over time), one axis, thin 2px line,
 * recessive gridlines, hover tooltip. No legend needed for one series (dataviz skill).
 */
export default function RunHistoryChart({ runs }: { runs: RunSummary[] }) {
  const data = [...runs]
    .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    .map((run) => ({
      date: new Date(run.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' }),
      passRatePercent: Math.round(run.passRatePercent * 10) / 10,
    }));

  if (data.length === 0) {
    return <p style={{ color: 'var(--text-secondary)' }}>No runs yet.</p>;
  }

  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--gridline)" vertical={false} />
        <XAxis dataKey="date" stroke="var(--text-muted)" tick={{ fontSize: 12 }} axisLine={{ stroke: 'var(--baseline)' }} tickLine={false} />
        <YAxis
          domain={[0, 100]}
          stroke="var(--text-muted)"
          tick={{ fontSize: 12 }}
          axisLine={{ stroke: 'var(--baseline)' }}
          tickLine={false}
          width={40}
          unit="%"
        />
        <Tooltip
          contentStyle={{
            background: 'var(--surface-1)',
            border: '1px solid var(--border)',
            borderRadius: 8,
            fontSize: 12,
          }}
          labelStyle={{ color: 'var(--text-primary)' }}
          formatter={(value: number) => [`${value}%`, 'Pass rate']}
        />
        <Line
          type="monotone"
          dataKey="passRatePercent"
          stroke="var(--series-1)"
          strokeWidth={2}
          dot={{ r: 3, fill: 'var(--series-1)' }}
          activeDot={{ r: 5 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}
