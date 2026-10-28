<script lang="ts">
  import { SvelteSet } from 'svelte/reactivity';
  import type {
    Catalog,
    Column,
    Constraint,
    Index,
    Table,
    TableKind,
    TableSelection,
  } from '../lib/types';

  let {
    catalog,
    instanceLabel,
    instanceName = '',
    selectedKey = '',
    onSelectTable = () => {},
    onAddToJoin,
  }: {
    catalog: Catalog | null;
    instanceLabel: string;
    instanceName?: string;
    selectedKey?: string;
    onSelectTable?: (selection: TableSelection) => void;
    onAddToJoin?: (ref: string) => void;
  } = $props();

  let isOpen = $state(true);

  function toggle(): void {
    isOpen = !isOpen;
  }

  function kindLabel(kind: TableKind): string {
    switch (kind) {
      case 'TABLE':
        return 'таблица';
      case 'PARTITIONED_TABLE':
        return 'секционированная';
      case 'VIEW':
        return 'вьюха';
      case 'MATERIALIZED_VIEW':
        return 'матвьюха';
      case 'FOREIGN_TABLE':
        return 'внешняя';
    }
  }

  function columnLabel(col: Column): string {
    const parts = [
      col.columnName,
      `${col.ordinalPosition}`,
      col.dataType,
      col.nullable ? 'null' : 'not null',
    ];
    if (col.defaultValue) {
      parts.push(`default ${col.defaultValue}`);
    }
    if (col.generated) {
      parts.push('generated');
    }
    return parts.join(' · ');
  }

  function tableHead(table: Table): string {
    const extras: string[] = [];
    if (table.columns.length > 0) {
      extras.push(`${table.columns.length} col`);
    }
    if (table.constraints.length > 0) {
      extras.push(`${table.constraints.length} con`);
    }
    if (table.indexes.length > 0) {
      extras.push(`${table.indexes.length} idx`);
    }
    return [table.tableName, kindLabel(table.kind), ...extras].join(' · ');
  }

  function constraintLabel(constraint: Constraint): string {
    const columns = constraint.columns
      .map((c) => (c.refColumnName ? `${c.columnName} → ${c.refColumnName}` : c.columnName))
      .join(', ');
    if (constraint.type === 'CHECK' || constraint.type === 'EXCLUDE') {
      return `${constraint.type}: ${constraint.constraintName} — ${constraint.expression ?? ''}`;
    }
    if (constraint.type === 'FOREIGN_KEY') {
      const actions = [
        constraint.onDelete ? `ON DELETE ${constraint.onDelete}` : null,
        constraint.onUpdate ? `ON UPDATE ${constraint.onUpdate}` : null,
      ]
        .filter(Boolean)
        .join(' / ');
      return `${constraint.type}: ${constraint.constraintName} (${columns}) → ${constraint.refTable ?? ''}${actions ? ` [${actions}]` : ''}`;
    }
    return `${constraint.type}: ${constraint.constraintName}${columns ? ` (${columns})` : ''}`;
  }

  function indexLabel(index: Index): string {
    const columns = index.columns.map((c) => c.columnName).join(', ');
    return `${index.kind}${index.unique ? ' unique' : ''} — ${index.indexName}${columns ? ` (${columns})` : ''}`;
  }

  let openTables = new SvelteSet<string>();

  function toggleTable(name: string): void {
    if (openTables.has(name)) {
      openTables.delete(name);
    } else {
      openTables.add(name);
    }
  }

  function tableOpen(name: string): boolean {
    return openTables.has(name);
  }

  function selectTable(schemaName: string, tableName: string): void {
    if (!catalog) {
      return;
    }
    onSelectTable({ instanceId: catalog.instanceId, schemaName, tableName });
    toggleTable(tableName);
  }
</script>

<div class="panel">
  <button class="lg" onclick={toggle}>
    {isOpen ? '▾' : '▸'}
    {instanceLabel || `Instance ${catalog?.instanceId ?? ''}`}
    <span class="tag">{catalog ? `схем: ${catalog.schemas.length}` : 'не загружено'}</span>
  </button>

  {#if isOpen && catalog}
    {#each catalog.schemas as schema (schema.id)}
      <div class="schema">
        <button class="schema-name" onclick={() => toggleTable(schema.schemaName)}>
          {tableOpen(schema.schemaName) ? '▾' : '▸'}
          {schema.schemaName}
          <span class="tag">таблиц: {schema.tables.length}</span>
        </button>
        {#if tableOpen(schema.schemaName)}
          {#each schema.tables as table (table.id)}
            {@const key = `${catalog.instanceId}.${schema.schemaName}.${table.tableName}`}
            <div class="table-row">
              <div class="table-line">
                <button
                  class="table-name"
                  class:selected={selectedKey === key}
                  onclick={() => selectTable(schema.schemaName, table.tableName)}
                >
                  {tableOpen(table.tableName) ? '▾' : '▸'}
                  {tableHead(table)}
                </button>
                {#if onAddToJoin && instanceName}
                  <button
                    class="add-join"
                    title="Добавить в конструктор джойнов"
                    onclick={() =>
                      onAddToJoin(`${instanceName}.${schema.schemaName}.${table.tableName}`)}
                  >
                    +
                  </button>
                {/if}
              </div>
              {#if tableOpen(table.tableName)}
                <div class="detail">
                  <div class="cols">
                    <div class="detail-title">Колонки</div>
                    {#each table.columns as col (col.ordinalPosition)}
                      <div class="col">{columnLabel(col)}</div>
                    {/each}
                  </div>
                  <div class="sec">
                    <div class="detail-title">Ограничения</div>
                    {#each table.constraints as c (c.constraintName)}
                      <div class="col">{constraintLabel(c)}</div>
                    {/each}
                  </div>
                  <div class="sec">
                    <div class="detail-title">Индексы</div>
                    {#each table.indexes as idx (idx.indexName)}
                      <div class="col">{indexLabel(idx)}</div>
                    {/each}
                  </div>
                </div>
              {/if}
            </div>
          {/each}
        {/if}
      </div>
    {/each}
  {/if}
</div>

<style>
  .schema {
    margin-top: 8px;
  }
  .schema-name {
    background: #161b22;
    width: 100%;
    text-align: left;
    border-radius: 6px;
    padding: 6px 8px;
  }
  .table-row {
    margin-left: 16px;
    margin-top: 4px;
  }
  .table-line {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .table-name {
    background: transparent;
    margin-left: 0;
    padding: 4px 8px;
    text-align: left;
    flex: 1;
  }
  .add-join {
    flex: 0 0 auto;
    padding: 2px 8px;
    font-weight: 700;
  }
  .table-name.selected {
    background: #1f6feb;
    border-radius: 4px;
  }
  .detail {
    margin-left: 24px;
    font-size: 13px;
  }
  .detail-title {
    margin-top: 8px;
    color: var(--muted);
    text-transform: uppercase;
    font-size: 11px;
    letter-spacing: 0.05em;
  }
  .col {
    color: var(--text);
    word-break: break-word;
  }
</style>
