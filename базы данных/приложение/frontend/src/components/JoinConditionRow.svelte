<script lang="ts">
  import type { JoinStore } from '../lib/join.svelte';
  import type { JoinCondition, JoinTableSpec } from '../lib/types';
  import type { TableOption } from '../lib/tables';

  let {
    store,
    table,
    condition,
    tableIndex,
    conditionIndex,
    columns,
    options,
  }: {
    store: JoinStore;
    table: JoinTableSpec;
    condition: JoinCondition;
    tableIndex: number;
    conditionIndex: number;
    columns: string[];
    options: TableOption[];
  } = $props();

  let otherOptions = $derived(options.filter((option) => option.ref !== table.ref));
  let rightColumns = $derived(
    options.find((option) => option.ref === condition.rightTable)?.columns ?? [],
  );
</script>

<div class="cond">
  <span class="tname">{table.ref.split('.').pop() ?? table.ref}</span>
  <select
    value={condition.leftColumn}
    onchange={(e) =>
      store.setCondition(tableIndex, conditionIndex, { leftColumn: e.currentTarget.value })}
  >
    <option value="">колонка</option>
    {#each columns as column (column)}<option value={column}>{column}</option>{/each}
  </select>
  <span>=</span>
  <select
    value={condition.rightTable}
    onchange={(e) =>
      store.setCondition(tableIndex, conditionIndex, {
        rightTable: e.currentTarget.value,
        rightColumn: '',
      })}
  >
    {#each otherOptions as option (option.ref)}<option value={option.ref}>{option.ref}</option
      >{/each}
  </select>
  <select
    value={condition.rightColumn}
    onchange={(e) =>
      store.setCondition(tableIndex, conditionIndex, { rightColumn: e.currentTarget.value })}
  >
    <option value="">колонка</option>
    {#each rightColumns as column (column)}<option value={column}>{column}</option>{/each}
  </select>
  <button
    class="rm"
    title="Удалить условие"
    onclick={() => store.removeCondition(tableIndex, conditionIndex)}
  >
    ✕
  </button>
</div>

<style>
  .cond {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
    font-size: 12px;
  }
  .tname {
    font-family: ui-monospace, monospace;
    color: var(--muted);
  }
  select {
    width: auto;
    padding: 2px 6px;
    font-size: 12px;
  }
  .rm {
    padding: 2px 8px;
  }
</style>
