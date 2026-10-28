import { describe, expect, it } from 'vitest';
import { annotateLiveRows, changedColumnNames, diffRows, primaryKeyColumns } from './rowDiff';
import type { Table } from './types';

function tableWithPk(...columns: string[]): Table {
  return {
    id: 1,
    schemaId: 1,
    tableName: 't',
    kind: 'TABLE',
    columns: [],
    constraints: [
      {
        id: 1,
        tableId: 1,
        constraintName: 't_pkey',
        type: 'PRIMARY_KEY',
        expression: null,
        refTable: null,
        onDelete: null,
        onUpdate: null,
        columns: columns.map((columnName, index) => ({
          columnName,
          position: index + 1,
          refColumnName: null,
        })),
      },
    ],
    indexes: [],
  };
}

describe('primaryKeyColumns', () => {
  it('returns a single-column primary key', () => {
    expect(primaryKeyColumns(tableWithPk('id'))).toEqual(['id']);
  });

  it('returns a composite primary key in key order', () => {
    expect(primaryKeyColumns(tableWithPk('tenant_id', 'id'))).toEqual(['tenant_id', 'id']);
  });

  it('returns empty when there is no primary key', () => {
    const table = { ...tableWithPk('id'), constraints: [] };
    expect(primaryKeyColumns(table)).toEqual([]);
  });
});

describe('diffRows (by primary key)', () => {
  const columns = ['id', 'email'];
  const key = ['id'];

  it('detects modified rows', () => {
    const result = diffRows(columns, [[1, 'a@b.c']], [[1, 'z@b.c']], key);
    expect(result.modified).toHaveLength(1);
    expect(result.modified[0]?.changedColumns).toEqual([1]);
    expect(result.modified[0]?.key).toEqual([1]);
    expect(result.added).toHaveLength(0);
    expect(result.removed).toHaveLength(0);
  });

  it('detects added and removed rows', () => {
    const baseline = [
      [1, 'a@b.c'],
      [2, 'b@b.c'],
    ];
    const live = [
      [2, 'b@b.c'],
      [3, 'c@b.c'],
    ];
    const result = diffRows(columns, baseline, live, key);
    expect(result.added).toEqual([[3, 'c@b.c']]);
    expect(result.removed).toEqual([[1, 'a@b.c']]);
    expect(result.unchanged).toBe(1);
  });

  it('reports no changes when rows are identical', () => {
    const rows = [
      [1, 'a@b.c'],
      [2, 'b@b.c'],
    ];
    const result = diffRows(columns, rows, [...rows], key);
    expect(result.added).toHaveLength(0);
    expect(result.removed).toHaveLength(0);
    expect(result.modified).toHaveLength(0);
    expect(result.unchanged).toBe(2);
  });
});

describe('changedColumnNames', () => {
  it('returns the names of columns changed in any modified row', () => {
    const diff = diffRows(['id', 'a', 'b'], [[1, 'x', 'y']], [[1, 'z', 'y']], ['id']);
    expect(changedColumnNames(['id', 'a', 'b'], diff)).toEqual(['a']);
  });
});

describe('annotateLiveRows', () => {
  const columns = ['id', 'email'];
  const baseline = [
    [1, 'a@b.c'],
    [2, 'b@b.c'],
    [3, 'c@b.c'],
  ];

  it('marks modified and added rows in the live result', () => {
    const live = [
      [1, 'z@b.c'],
      [2, 'b@b.c'],
      [4, 'd@b.c'],
    ];
    const annotation = annotateLiveRows(columns, live, diffRows(columns, baseline, live, ['id']));

    expect(annotation.statusByRow.get(0)).toBe('modified');
    expect([...(annotation.changedCellsByRow.get(0) ?? [])]).toEqual([1]);
    expect(annotation.previousByRow.get(0)).toEqual([1, 'a@b.c']);
    expect(annotation.statusByRow.get(2)).toBe('added');
  });

  it('returns empty maps without key columns', () => {
    const diff = diffRows(['a'], [[1]], [[2]], []);
    const annotation = annotateLiveRows(['a'], [[2]], diff);

    expect(annotation.statusByRow.size).toBe(0);
    expect(annotation.changedCellsByRow.size).toBe(0);
  });
});

describe('diffRows (without primary key)', () => {
  it('compares rows as a multiset', () => {
    const baseline = [
      ['a', 1],
      ['b', 2],
    ];
    const live = [
      ['b', 2],
      ['c', 3],
    ];
    const result = diffRows(['x', 'y'], baseline, live, []);
    expect(result.keyColumns).toEqual([]);
    expect(result.added).toEqual([['c', 3]]);
    expect(result.removed).toEqual([['a', 1]]);
    expect(result.unchanged).toBe(1);
  });
});
