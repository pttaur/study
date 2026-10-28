<script lang="ts">
  import type { JoinStore } from '../lib/join.svelte';
  import type { JoinType } from '../lib/types';
  import type { TableOption } from '../lib/tables';
  import JoinConditionRow from './JoinConditionRow.svelte';

  let { store, index, options }: { store: JoinStore; index: number; options: TableOption[] } =
    $props();

  const JOIN_TYPES: JoinType[] = ['INNER', 'LEFT', 'RIGHT', 'FULL'];

  let table = $derived(store.tables[index]);
  let isBase = $derived(index === 0);
  let columns = $derived(options.find((option) => option.ref === table?.ref)?.columns ?? []);
</script>

{#if table}
  <div class="card">
    <div class="head">
      <span class="lg">{table.ref}</span>
      {#if isBase}
        <span class="muted">FROM</span>
      {:else}
        <select
          value={table.type ?? 'INNER'}
          onchange={(e) => store.setJoinType(index, e.currentTarget.value as JoinType)}
        >
          {#each JOIN_TYPES as type (type)}<option value={type}>{type} JOIN</option>{/each}
        </select>
      {/if}
      <span class="spacer"></span>
      <button title="Убрать таблицу" onclick={() => store.removeTable(index)}>✕</button>
    </div>

    <div class="fields-head">
      <span class="cond-title">Поля в результат</span>
      <span class="muted">выбрано: {store.selectedCount(table.ref)}</span>
      <span class="spacer"></span>
      <button class="mini" onclick={() => store.selectAll(table.ref, columns)}>все</button>
      <button class="mini" onclick={() => store.clearSelection(table.ref)}>снять</button>
    </div>

    <div class="columns">
      {#each columns as column (column)}
        <label class="col">
          <input
            type="checkbox"
            checked={store.isSelected(table.ref, column)}
            onchange={() => store.toggleColumn(table.ref, column)}
          />
          {column}
        </label>
      {/each}
      {#if columns.length === 0}
        <span class="muted">нет колонок (схема не загружена)</span>
      {/if}
    </div>

    {#if !isBase}
      <div class="conditions">
        <div class="cond-title">Условие соединения</div>
        {#each table.on as condition, conditionIndex (conditionIndex)}
          <JoinConditionRow
            {store}
            {table}
            {condition}
            tableIndex={index}
            {conditionIndex}
            {columns}
            {options}
          />
        {/each}
        <button class="add" onclick={() => store.addCondition(index)}>+ условие</button>
      </div>
    {/if}
  </div>
{/if}

<style>
  .card {
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 8px;
    margin-bottom: 8px;
    background: var(--panel);
  }
  .head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .head .lg {
    font-size: 14px;
  }
  .spacer {
    flex: 1;
  }
  .head select {
    width: auto;
    padding: 2px 6px;
  }
  .columns {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 12px;
    margin-top: 6px;
  }
  .fields-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 8px;
    flex-wrap: wrap;
  }
  .mini {
    padding: 1px 8px;
    font-size: 11px;
  }
  .col {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    font-family: ui-monospace, monospace;
  }
  .col input {
    width: auto;
  }
  .conditions {
    margin-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .cond-title {
    color: var(--muted);
    text-transform: uppercase;
    font-size: 11px;
    letter-spacing: 0.05em;
  }
  .add {
    align-self: flex-start;
    padding: 2px 8px;
    font-size: 12px;
  }
</style>
