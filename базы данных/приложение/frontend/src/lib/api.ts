import type {
  Catalog,
  CreateInstanceRequest,
  DiffResult,
  Instance,
  JoinRequest,
  QueryHistoryEntry,
  QueryResult,
  RowGrid,
} from './types';

const BASE = '/api';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await fetch(`${BASE}${path}`, { ...init, headers });

  if (!res.ok) {
    const detail = await res.text();
    throw new Error(`HTTP ${res.status}: ${detail || res.statusText}`);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export function listInstances(): Promise<Instance[]> {
  return request<Instance[]>('/instances');
}

export function createInstance(body: CreateInstanceRequest): Promise<Instance> {
  return request<Instance>('/instances', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function loadSchema(instanceId: number): Promise<Catalog> {
  return request<Catalog>(`/instances/${instanceId}/load`, { method: 'POST' });
}

export function checkChanges(instanceId: number): Promise<DiffResult> {
  return request<DiffResult>(`/instances/${instanceId}/check`, { method: 'POST' });
}

export function catalogEvents(instanceId: number): EventSource {
  return new EventSource(`${BASE}/instances/${instanceId}/events`);
}

function rowsPath(instanceId: number, schema: string, table: string): string {
  return `/instances/${instanceId}/tables/${encodeURIComponent(schema)}/${encodeURIComponent(table)}/rows`;
}

export function getRows(
  instanceId: number,
  schema: string,
  table: string,
  limit: number,
  offset: number,
): Promise<RowGrid> {
  const qs = new URLSearchParams({ limit: String(limit), offset: String(offset) });
  return request<RowGrid>(`${rowsPath(instanceId, schema, table)}?${qs}`);
}

export function runJoin(body: JoinRequest): Promise<QueryResult> {
  return request<QueryResult>('/join', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

export function joinHistory(): Promise<QueryHistoryEntry[]> {
  return request<QueryHistoryEntry[]>('/join/history');
}
