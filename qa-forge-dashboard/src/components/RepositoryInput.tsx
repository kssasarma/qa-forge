export default function RepositoryInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <label className="flex items-center gap-2 text-sm">
      <span style={{ color: 'var(--text-secondary)' }}>Repository</span>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="acme/backend"
        className="rounded border px-2 py-1 text-sm"
        style={{ borderColor: 'var(--border)', background: 'var(--surface-1)', color: 'var(--text-primary)' }}
      />
    </label>
  );
}
