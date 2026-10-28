import type {
  JoinCondition,
  JoinRequest,
  JoinTableSpec,
  JoinType,
  QueryHistoryEntry,
  QueryResult,
  SelectColumn,
} from './types';
import { joinHistory, runJoin } from './api';
import { toErrorMessage } from './errors';

/** Состояние конструктора джойнов: таблицы, соединения, выбор колонок и результат. */
export class JoinStore {
  pageSize = 1000;
  tables = $state<JoinTableSpec[]>([]);
  select = $state<SelectColumn[]>([]);
  result = $state<QueryResult | null>(null);
  history = $state<QueryHistoryEntry[]>([]);
  tab = $state<'result' | 'history'>('result');
  running = $state(false);
  historyLoading = $state(false);
  error = $state('');

  addTable(ref: string): void {
    if (this.tables.some((table) => table.ref === ref)) {
      return;
    }
    const base = this.tables[0];
    const isBase = this.tables.length === 0 || !base;
    this.tables.push({
      ref,
      type: isBase ? null : 'INNER',
      on: isBase ? [] : [{ leftTable: ref, leftColumn: '', rightTable: base.ref, rightColumn: '' }],
    });
  }

  removeTable(index: number): void {
    this.tables.splice(index, 1);
    this.select = this.select.filter((column) => this.tables.some((t) => t.ref === column.table));
    const base = this.tables[0];
    if (base) {
      base.type = null;
      base.on = [];
    }
  }

  setJoinType(index: number, type: JoinType): void {
    const table = this.tables[index];
    if (table) {
      table.type = type;
    }
  }

  addCondition(index: number): void {
    const spec = this.tables[index];
    if (!spec) {
      return;
    }
    const other = this.tables.find((table) => table.ref !== spec.ref);
    if (!other) {
      return;
    }
    spec.on.push({ leftTable: spec.ref, leftColumn: '', rightTable: other.ref, rightColumn: '' });
  }

  setCondition(index: number, conditionIndex: number, patch: Partial<JoinCondition>): void {
    const condition = this.tables[index]?.on[conditionIndex];
    if (condition) {
      Object.assign(condition, patch);
    }
  }

  removeCondition(index: number, conditionIndex: number): void {
    this.tables[index]?.on.splice(conditionIndex, 1);
  }

  toggleColumn(ref: string, column: string): void {
    const index = this.select.findIndex((c) => c.table === ref && c.column === column);
    if (index >= 0) {
      this.select.splice(index, 1);
    } else {
      this.select.push({ table: ref, column });
    }
  }

  isSelected(ref: string, column: string): boolean {
    return this.select.some((c) => c.table === ref && c.column === column);
  }

  hasTable(ref: string): boolean {
    return this.tables.some((table) => table.ref === ref);
  }

  selectedCount(ref: string): number {
    return this.select.filter((column) => column.table === ref).length;
  }

  selectAll(ref: string, columns: string[]): void {
    const missing = columns
      .filter((column) => !this.isSelected(ref, column))
      .map((column) => ({ table: ref, column }));
    this.select = [...this.select, ...missing];
  }

  clearSelection(ref: string): void {
    this.select = this.select.filter((column) => column.table !== ref);
  }

  clear(): void {
    this.tables = [];
    this.select = [];
    this.result = null;
    this.error = '';
  }

  async run(): Promise<void> {
    const problem = this.validate();
    if (problem) {
      this.error = problem;
      return;
    }
    this.running = true;
    this.error = '';
    this.tab = 'result';
    try {
      this.result = await runJoin(this.build(0));
    } catch (e) {
      this.error = toErrorMessage(e);
      this.result = null;
    } finally {
      this.running = false;
    }
  }

  async nextPage(): Promise<void> {
    const current = this.result;
    if (current) {
      await this.page(current.offset + this.pageSize);
    }
  }

  async prevPage(): Promise<void> {
    const current = this.result;
    if (current) {
      await this.page(Math.max(0, current.offset - this.pageSize));
    }
  }

  async loadHistory(): Promise<void> {
    this.historyLoading = true;
    this.error = '';
    try {
      this.history = await joinHistory();
    } catch (e) {
      this.error = toErrorMessage(e);
      this.history = [];
    } finally {
      this.historyLoading = false;
    }
  }

  private async page(offset: number): Promise<void> {
    this.running = true;
    this.error = '';
    try {
      this.result = await runJoin(this.build(offset));
    } catch (e) {
      this.error = toErrorMessage(e);
    } finally {
      this.running = false;
    }
  }

  private build(offset: number): JoinRequest {
    return {
      tables: this.tables,
      select: this.select,
      maxRows: this.pageSize,
      offset,
      timeoutMs: 30000,
    };
  }

  private validate(): string | null {
    if (this.tables.length === 0) {
      return 'Добавьте хотя бы одну таблицу';
    }
    if (this.select.length === 0) {
      return 'Выберите хотя бы одну колонку';
    }
    for (let i = 1; i < this.tables.length; i++) {
      const table = this.tables[i];
      if (!table) {
        continue;
      }
      if (table.on.length === 0) {
        return `Задайте условие соединения для ${table.ref}`;
      }
      if (table.on.some((condition) => !condition.leftColumn || !condition.rightColumn)) {
        return `Заполните условие соединения для ${table.ref}`;
      }
    }
    return null;
  }
}
