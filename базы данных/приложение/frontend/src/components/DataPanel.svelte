<script lang="ts">
  import type { Table } from '../lib/types';
  import { primaryKeyColumns } from '../lib/rowDiff';
  import RowsExplorer from './RowsExplorer.svelte';

  let { instanceId, schemaName, table }: { instanceId: number; schemaName: string; table: Table } =
    $props();

  let keyColumns = $derived(primaryKeyColumns(table));
</script>

<div class="data-panel">
  <div class="meta">
    <span class="muted">Таблица {schemaName}.{table.tableName}</span>
    <span class="muted">{table.columns.length} колонок</span>
    <span class="muted">
      PK: {keyColumns.length > 0 ? keyColumns.join(', ') : 'нет (сравнение по всей строке)'}
    </span>
  </div>
  {#key `${instanceId}.${schemaName}.${table.tableName}`}
    <RowsExplorer {instanceId} {schemaName} tableName={table.tableName} {keyColumns} />
  {/key}
</div>

<style>
  .data-panel {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px;
  }
  .meta {
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
    font-size: 12px;
  }
</style>
