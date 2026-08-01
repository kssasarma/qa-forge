import { useEffect, useState } from 'react';

const STORAGE_KEY = 'qaforge.repository';

/** Persists the selected repository across pages/reloads; no repo-picker API exists (PRD §12), so this is local-only. */
export function useRepository(): [string, (value: string) => void] {
  const [repository, setRepository] = useState(() => localStorage.getItem(STORAGE_KEY) ?? '');

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, repository);
  }, [repository]);

  return [repository, setRepository];
}
