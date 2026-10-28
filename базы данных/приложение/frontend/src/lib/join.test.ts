import { beforeEach, describe, expect, it, vi } from 'vitest';
import { JoinStore } from './join.svelte';
import { runJoin } from './api';

vi.mock('./api', () => ({
  runJoin: vi.fn(),
  explainJoin: vi.fn(),
  joinHistory: vi.fn(),
}));

describe('JoinStore', () => {
  beforeEach(() => vi.clearAllMocks());

  it('adds base then joined tables with default types', () => {
    const store = new JoinStore();
    store.addTable('ops.public.employee');
    store.addTable('ops.public.organization');

    expect(store.tables).toHaveLength(2);
    expect(store.tables[0]?.type).toBeNull();
    expect(store.tables[1]?.type).toBe('INNER');
    expect(store.tables[1]?.on).toHaveLength(1);
    expect(store.tables[1]?.on[0]?.leftTable).toBe('ops.public.organization');
    expect(store.tables[1]?.on[0]?.rightTable).toBe('ops.public.employee');
  });

  it('ignores duplicate tables', () => {
    const store = new JoinStore();
    store.addTable('a.s.t');
    store.addTable('a.s.t');
    expect(store.tables).toHaveLength(1);
  });

  it('toggles column selection', () => {
    const store = new JoinStore();
    store.addTable('a.s.t');
    store.toggleColumn('a.s.t', 'id');
    expect(store.isSelected('a.s.t', 'id')).toBe(true);
    store.toggleColumn('a.s.t', 'id');
    expect(store.isSelected('a.s.t', 'id')).toBe(false);
  });

  it('resets base table after removing it', () => {
    const store = new JoinStore();
    store.addTable('a.s.t1');
    store.addTable('b.s.t2');
    store.removeTable(0);
    expect(store.tables).toHaveLength(1);
    expect(store.tables[0]?.ref).toBe('b.s.t2');
    expect(store.tables[0]?.type).toBeNull();
  });

  it('reports a validation error when no columns are selected', async () => {
    const store = new JoinStore();
    store.addTable('a.s.t');

    await store.run();

    expect(store.error).toMatch(/колонку/);
    expect(runJoin).not.toHaveBeenCalled();
  });

  it('runs the join when the spec is valid', async () => {
    vi.mocked(runJoin).mockResolvedValue({
      columns: [],
      rows: [],
      truncated: false,
      offset: 0,
      totalRows: 0,
      durationMs: 1,
    });
    const store = new JoinStore();
    store.addTable('a.s.t');
    store.toggleColumn('a.s.t', 'id');

    await store.run();

    expect(runJoin).toHaveBeenCalledWith(
      expect.objectContaining({ select: [{ table: 'a.s.t', column: 'id' }] }),
    );
    expect(store.result?.totalRows).toBe(0);
  });
});
