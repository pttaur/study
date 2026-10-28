import type { Catalog, Instance } from './types';

export interface TableOption {
  /** Ссылка вида instance.schema.table (instance — название инстанса). */
  ref: string;
  columns: string[];
}

/** Собирает доступные таблицы (instance.schema.table) из каталогов инстансов. */
export function availableTables(
  instances: Instance[],
  catalogs: Map<number, Catalog>,
): TableOption[] {
  const options: TableOption[] = [];
  for (const instance of instances) {
    const catalog = catalogs.get(instance.id);
    if (!catalog) {
      continue;
    }
    for (const schema of catalog.schemas) {
      for (const table of schema.tables) {
        options.push({
          ref: `${instance.label}.${schema.schemaName}.${table.tableName}`,
          columns: table.columns.map((column) => column.columnName),
        });
      }
    }
  }
  return options.sort((a, b) => a.ref.localeCompare(b.ref));
}
