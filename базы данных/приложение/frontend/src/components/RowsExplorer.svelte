<script lang="ts">
  import { onMount } from 'svelte';
  import type { RowGrid } from '../lib/types';
  import { getRows } from '../lib/api';
  import { toErrorMessage } from '../lib/errors';
  import { formatCell } from '../lib/format';

  let {
    instanceId,
    schemaName,
    tableName,
  }: {
    instanceId: number;
    schemaName: string;
    tableName: string;
  } = $props();

  const PAGE_SIZE = 50;

  let grid = $state<RowGrid | null>(null);
  let offset = $state(0);
  let loading = $state(false);
  let error = $state('');

  onMount(() => {
    void reload();
  });

  async function reload(): Promise<void> {
    loading = true;
    error = '';
    try {
      grid = await getRows(instanceId, schemaName, tableName, PAGE_SIZE, offset);
    } catch (e) {
      error = toErrorMessage(e);
    } finally {
      loading = false;
    }
  }

  function nextPage(): void {
    if (grid && offset + PAGE_SIZE < grid.totalRows) {
      offset += PAGE_SIZE;
      void reload();
    }
  }

  function prevPage(): void {
    if (offset > 0) {
      offset = Math.max(0, offset - PAGE_SIZE);
      void reload();
    }
  }
</script>

<div class="rows-explorer">
  <div class="head">
    <span class="lg">{schemaName}.{tableName}</span>
  </div>

  {#if error}<p class="error">{error}</p>{/if}

  {#if grid}
    <div class="grid-wrap">
      <table>
        <thead>
          <tr>
            {#each grid.columns as col (col)}<th>{col}</th>{/each}
          </tr>
        </thead>
        <tbody>
          {#each grid.rows as row, i (i)}
            <tr>
              {#each row as cell, j (j)}
                <td>{formatCell(cell)}</td>
              {/each}
            </tr>
          {/each}
        </tbody>
      </table>
      {#if grid.rows.length === 0}
        <p class="muted">Нет строк.</p>
      {/if}
    </div>

    <div class="pager">
      <button onclick={prevPage} disabled={offset === 0 || loading}>← назад</button>
      <span class="muted">{offset + 1}–{offset + grid.rows.length} из {grid.totalRows}</span>
      <button onclick={nextPage} disabled={offset + PAGE_SIZE >= grid.totalRows || loading}>
        вперёд →
      </button>
    </div>
  {/if}
</div>

<style>
  .rows-explorer {
    margin-top: 8px;
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .grid-wrap {
    flex: 1;
    min-height: 0;
    overflow: auto;
    margin-top: 8px;
  }
  table {
    border-collapse: collapse;
    font-size: 13px;
    width: 100%;
  }
  th,
  td {
    border: 1px solid var(--border);
    padding: 3px 8px;
    text-align: left;
    white-space: nowrap;
  }
  th {
    position: sticky;
    top: 0;
    background: #161b22;
  }
  .pager {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 8px;
  }
</style>
