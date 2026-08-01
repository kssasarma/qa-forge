// Typed fetch client for the REST endpoints in PRD §12. The API sits behind HTTP Basic auth
// (§17.1); this client issues same-origin requests and relies on the browser's native Basic
// auth prompt (triggered by a 401 with WWW-Authenticate) rather than implementing a login form.

export type TestLayer = 'PLAYWRIGHT' | 'REST_ASSURED' | 'DB_VALIDATION';
export type TestStatus = 'ACTIVE' | 'OBSOLETE';
export type ExecutionStatus = 'PASSED' | 'FAILED' | 'SKIPPED' | 'ERROR';
export type GateResult = 'OPEN' | 'BLOCKED';

export interface TestCase {
  id: string;
  fileName: string;
  scenarioTitle: string;
  layer: TestLayer;
  userFlow: string;
  prNumber: string | null;
  status: TestStatus;
  tags: string[];
  lastExecutionStatus: ExecutionStatus | null;
  lastExecutionMs: number | null;
  executionCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface TestsPage {
  tests: TestCase[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface RunItem {
  fileName: string;
  layer: TestLayer | null;
  status: ExecutionStatus;
  durationMs: number | null;
  retryCount: number;
  selfHealed: boolean;
  errorMessage: string | null;
}

export interface RunDetail {
  runId: string;
  repository: string;
  prNumber: string | null;
  runType: 'REGRESSION' | 'INCREMENTAL';
  triggeredBy: string;
  outcome: string;
  gateResult: GateResult | null;
  total: number;
  passed: number;
  failed: number;
  passRatePercent: number;
  durationMs: number | null;
  createdAt: string;
  items: RunItem[];
}

export interface RunSummary {
  runId: string;
  repository: string;
  prNumber: string | null;
  runType: 'REGRESSION' | 'INCREMENTAL';
  triggeredBy: string;
  outcome: string;
  gateResult: GateResult | null;
  total: number;
  passed: number;
  failed: number;
  passRatePercent: number;
  durationMs: number | null;
  createdAt: string;
}

export interface RunsPage {
  runs: RunSummary[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

const API_BASE = '/api/v1';

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`${url} failed: ${response.status} ${response.statusText}`);
  }
  return response.json() as Promise<T>;
}

export function fetchTests(params: {
  repository: string;
  status?: TestStatus;
  layer?: TestLayer;
  page?: number;
  size?: number;
}): Promise<TestsPage> {
  const query = new URLSearchParams({ repository: params.repository });
  if (params.status) query.set('status', params.status);
  if (params.layer) query.set('layer', params.layer);
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));
  return getJson(`${API_BASE}/tests?${query.toString()}`);
}

export function fetchRuns(params: { repository: string; prNumber?: string; page?: number; size?: number }): Promise<RunsPage> {
  const query = new URLSearchParams({ repository: params.repository });
  if (params.prNumber) query.set('prNumber', params.prNumber);
  if (params.page !== undefined) query.set('page', String(params.page));
  if (params.size !== undefined) query.set('size', String(params.size));
  return getJson(`${API_BASE}/runs?${query.toString()}`);
}

export function fetchRun(runId: string): Promise<RunDetail> {
  return getJson(`${API_BASE}/runs/${encodeURIComponent(runId)}`);
}

export function exportUrl(repository: string, layer?: TestLayer): string {
  const query = new URLSearchParams({ repository });
  if (layer) query.set('layer', layer);
  return `${API_BASE}/export?${query.toString()}`;
}
