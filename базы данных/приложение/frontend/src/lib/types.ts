export type InstanceKind = 'POSTGRES' | 'MARIADB';

export interface Instance {
  id: number;
  label: string;
  connectionUrl: string;
  username: string;
  kind: InstanceKind;
}

export interface CreateInstanceRequest {
  label: string;
  connectionUrl: string;
  username: string;
  password: string;
  kind?: InstanceKind;
}

export interface Catalog {
  instanceId: number;
  schemas: Schema[];
}

export interface Schema {
  id: number;
  instanceId: number;
  schemaName: string;
  owner: string | null;
  comment: string | null;
  tables: Table[];
}

export type TableKind =
  'TABLE' | 'PARTITIONED_TABLE' | 'VIEW' | 'MATERIALIZED_VIEW' | 'FOREIGN_TABLE';

export interface Table {
  id: number;
  schemaId: number;
  tableName: string;
  kind: TableKind;
  columns: Column[];
  constraints: Constraint[];
  indexes: Index[];
}

export interface Column {
  id: number;
  tableId: number;
  columnName: string;
  ordinalPosition: number;
  dataType: string;
  nullable: boolean;
  defaultValue: string | null;
  generated: boolean;
}

export type ConstraintType = 'PRIMARY_KEY' | 'FOREIGN_KEY' | 'UNIQUE' | 'CHECK' | 'EXCLUDE';

export interface ConstraintColumn {
  columnName: string;
  position: number;
  refColumnName: string | null;
}

export interface Constraint {
  id: number;
  tableId: number;
  constraintName: string;
  type: ConstraintType;
  /** Тело CHECK/EXCLUDE (для остальных — null). */
  expression: string | null;
  /** Для FK — целевая таблица в виде "schema.table" (для остальных — null). */
  refTable: string | null;
  /** Для FK — правила ссылочной целостности. */
  onDelete: string | null;
  onUpdate: string | null;
  /** Состав ключа с порядком. */
  columns: ConstraintColumn[];
}

export type IndexKind = 'BTREE' | 'HASH' | 'GIN' | 'GIST' | 'SPGIST' | 'BRIN';

export interface IndexColumn {
  columnName: string;
  position: number;
}

export interface Index {
  id: number;
  tableId: number;
  indexName: string;
  unique: boolean;
  primary: boolean;
  kind: IndexKind;
  /** Полное определение индекса (в т.ч. выражения/условие partial). */
  definition: string | null;
  columns: IndexColumn[];
}

export type DiffType = 'ADDED' | 'REMOVED' | 'MODIFIED';

export interface LeafDiff {
  name: string;
  type: DiffType;
}

export interface TableDiff {
  name: string;
  type: DiffType;
  columns: LeafDiff[];
  constraints: LeafDiff[];
  indexes: LeafDiff[];
}

export interface SchemaDiff {
  name: string;
  type: DiffType;
  tables: TableDiff[];
}

export interface DiffResult {
  baseInstanceId: number;
  compareInstanceId: number;
  schemas: SchemaDiff[];
}

export interface TableSelection {
  instanceId: number;
  schemaName: string;
  tableName: string;
}

export type JoinType = 'INNER' | 'LEFT' | 'RIGHT' | 'FULL';

export interface JoinCondition {
  leftTable: string;
  leftColumn: string;
  rightTable: string;
  rightColumn: string;
}

export interface JoinTableSpec {
  ref: string;
  type: JoinType | null;
  on: JoinCondition[];
}

export interface SelectColumn {
  table: string;
  column: string;
}

export interface JoinRequest {
  tables: JoinTableSpec[];
  select: SelectColumn[];
  maxRows?: number;
  offset?: number;
  timeoutMs?: number;
}

export interface ColumnInfo {
  name: string;
  type: string;
}

export interface QueryResult {
  columns: ColumnInfo[];
  rows: unknown[][];
  truncated: boolean;
  offset: number;
  totalRows: number;
  durationMs: number;
}

export interface QueryHistoryEntry {
  id: number;
  sql: string;
  status: string;
  rowCount: number | null;
  durationMs: number | null;
  error: string | null;
  createdAt: string;
}

export interface RowGrid {
  columns: string[];
  rows: unknown[][];
  totalRows: number;
}
