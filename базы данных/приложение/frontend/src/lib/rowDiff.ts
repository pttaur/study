import type { Table } from './types';

export interface ModifiedRow {
  key: unknown[];
  changedColumns: number[];
  baseline: unknown[];
  live: unknown[];
}

export interface RowDiffResult {
  /** Колонки, по которым сопоставлялись строки (пусто — сопоставление по всей строке). */
  keyColumns: string[];
  added: unknown[][];
  removed: unknown[][];
  modified: ModifiedRow[];
  unchanged: number;
}

/** Извлекает имена колонок PRIMARY KEY (в порядке ключа) из ограничений таблицы. */
export function primaryKeyColumns(table: Table): string[] {
  const pk = table.constraints.find((c) => c.type === 'PRIMARY_KEY');
  if (!pk) {
    return [];
  }
  return [...pk.columns]
    .sort((a, b) => a.position - b.position)
    .map((column) => column.columnName)
    .filter((name) => name.length > 0);
}

function cellKey(value: unknown): string {
  return value === undefined ? '\u0000undefined' : JSON.stringify(value);
}

/** Стабильный ключ по набору значений (в порядке ключевых колонок). */
export function keyOfValues(values: unknown[]): string {
  return values.map(cellKey).join('\u0001');
}

function tupleKey(row: unknown[], indices: number[]): string {
  return keyOfValues(indices.map((i) => row[i]));
}

/** Индексы ключевых колонок в массиве колонок; отсутствующие отбрасываются. */
export function keyIndices(columns: string[], keyColumns: string[]): number[] {
  return keyColumns.map((name) => columns.indexOf(name)).filter((i) => i >= 0);
}

function fullRowKey(row: unknown[]): string {
  return row.map(cellKey).join('\u0001');
}

/**
 * Строит построчный дифф между эталонными (то, что на экране) и живыми строками.
 *
 * <p>Если известны колонки первичного ключа — строки сопоставляются по нему:
 * тогда дополнительно вычисляются изменённые строки. Иначе строки сопоставляются
 * целиком (мультимножество), и определяются только добавленные/удалённые.</p>
 */
export function diffRows(
  baselineColumns: string[],
  baselineRows: unknown[][],
  liveRows: unknown[][],
  keyColumns: string[],
): RowDiffResult {
  const indices = keyIndices(baselineColumns, keyColumns);
  const byKey = keyColumns.length > 0 && indices.length === keyColumns.length;

  if (!byKey) {
    return diffByFullRow(baselineRows, liveRows);
  }

  const baselineByKey = new Map<string, unknown[]>();
  for (const row of baselineRows) {
    baselineByKey.set(tupleKey(row, indices), row);
  }
  const liveByKey = new Map<string, unknown[]>();
  for (const row of liveRows) {
    liveByKey.set(tupleKey(row, indices), row);
  }

  const added: unknown[][] = [];
  const modified: ModifiedRow[] = [];
  let unchanged = 0;

  for (const [key, liveRow] of liveByKey) {
    const baseRow = baselineByKey.get(key);
    if (!baseRow) {
      added.push(liveRow);
      continue;
    }
    const changedColumns: number[] = [];
    const width = Math.max(baseRow.length, liveRow.length);
    for (let i = 0; i < width; i++) {
      if (cellKey(baseRow[i]) !== cellKey(liveRow[i])) {
        changedColumns.push(i);
      }
    }
    if (changedColumns.length > 0) {
      modified.push({
        key: indices.map((i) => liveRow[i]),
        changedColumns,
        baseline: baseRow,
        live: liveRow,
      });
    } else {
      unchanged += 1;
    }
  }

  const removed: unknown[][] = [];
  for (const [key, baseRow] of baselineByKey) {
    if (!liveByKey.has(key)) {
      removed.push(baseRow);
    }
  }

  return { keyColumns, added, removed, modified, unchanged };
}

/** Имена колонок, значения которых изменились хотя бы в одной строке. */
export function changedColumnNames(columns: string[], diff: RowDiffResult): string[] {
  const changed = new Set<number>();
  for (const change of diff.modified) {
    for (const col of change.changedColumns) {
      changed.add(col);
    }
  }
  return columns.filter((_, i) => changed.has(i));
}

export type RowStatus = 'modified' | 'added';

export interface LiveRowAnnotation {
  /** Индекс строки в живой выборке → статус (для подсветки в таблице). */
  statusByRow: Map<number, RowStatus>;
  /** Индекс строки в живой выборке → изменённые индексы колонок. */
  changedCellsByRow: Map<number, Set<number>>;
  /** Индекс строки в живой выборке → прежняя строка (для подсказки «было»). */
  previousByRow: Map<number, unknown[]>;
}

/**
 * Сопоставляет результат диффа со свежими (live) строками, чтобы подсветить их в таблице
 * после обновления данных. Для диффа без ключевых колонок связь установить нельзя — карты пусты.
 */
export function annotateLiveRows(
  liveColumns: string[],
  liveRows: unknown[][],
  diff: RowDiffResult,
): LiveRowAnnotation {
  const statusByRow = new Map<number, RowStatus>();
  const changedCellsByRow = new Map<number, Set<number>>();
  const previousByRow = new Map<number, unknown[]>();

  const indices = keyIndices(liveColumns, diff.keyColumns);
  if (indices.length === 0) {
    return { statusByRow, changedCellsByRow, previousByRow };
  }

  const liveIndexByKey = new Map<string, number>();
  liveRows.forEach((row, i) => {
    liveIndexByKey.set(keyOfValues(indices.map((k) => row[k])), i);
  });

  for (const change of diff.modified) {
    const index = liveIndexByKey.get(keyOfValues(change.key));
    if (index === undefined) {
      continue;
    }
    statusByRow.set(index, 'modified');
    changedCellsByRow.set(index, new Set(change.changedColumns));
    previousByRow.set(index, change.baseline);
  }

  for (const row of diff.added) {
    const index = liveIndexByKey.get(keyOfValues(indices.map((k) => row[k])));
    if (index !== undefined) {
      statusByRow.set(index, 'added');
    }
  }

  return { statusByRow, changedCellsByRow, previousByRow };
}

function diffByFullRow(baselineRows: unknown[][], liveRows: unknown[][]): RowDiffResult {
  const remaining = new Map<string, unknown[][]>();
  for (const row of baselineRows) {
    const key = fullRowKey(row);
    const bucket = remaining.get(key);
    if (bucket) {
      bucket.push(row);
    } else {
      remaining.set(key, [row]);
    }
  }

  const added: unknown[][] = [];
  let unchanged = 0;
  for (const row of liveRows) {
    const key = fullRowKey(row);
    const bucket = remaining.get(key);
    if (bucket && bucket.length > 0) {
      bucket.pop();
      unchanged += 1;
    } else {
      added.push(row);
    }
  }

  const removed: unknown[][] = [];
  for (const bucket of remaining.values()) {
    removed.push(...bucket);
  }

  return { keyColumns: [], added, removed, modified: [], unchanged };
}
