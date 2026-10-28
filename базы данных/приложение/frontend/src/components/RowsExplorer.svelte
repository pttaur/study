<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import type { RowGrid } from '../lib/types';
  import {
    annotateLiveRows,
    diffRows,
    keyIndices,
    keyOfValues,
    type LiveRowAnnotation,
    type RowDiffResult,
  } from '../lib/rowDiff';
  import { getRows } from '../lib/api';
  import { toErrorMessage } from '../lib/errors';
  import { formatCell } from '../lib/format';
  import RowDiffView from './RowDiffView.svelte';

  let {
    instanceId,
    schemaName,
    tableName,
    keyColumns = [],
  }: {
    instanceId: number;
    schemaName: string;
    tableName: string;
    keyColumns?: string[];
  } = $props();

  const PAGE_SIZE = 50;

  let grid = $state<RowGrid | null>(null);
  let diff = $state<RowDiffResult | null>(null);
  let annotation = $state<LiveRowAnnotation | null>(null);
  let selectedRowKey = $state<string | null>(null);
  let flashRow = $state<number | null>(null);
  let offset = $state(0);
  let loading = $state(false);
  let comparing = $state(false);
  let error = $state('');
  let gridEl = $state<HTMLElement | null>(null);
  let flashTimer: ReturnType<typeof setTimeout> | null = null;

  onDestroy(() => {
    if (flashTimer) {
      clearTimeout(flashTimer);
    }
  });

  onMount(() => {
    void reload();
  });

  async function reload(): Promise<void> {
    loading = true;
    error = '';
    diff = null;
    annotation = null;
    selectedRowKey = null;
    flashRow = null;
    try {
      grid = await getRows(instanceId, schemaName, tableName, PAGE_SIZE, offset);
    } catch (e) {
      error = toErrorMessage(e);
    } finally {
      loading = false;
    }
  }

  /**
   * Обновляет страницу свежими строками из БД и показывает, что изменилось относительно
   * того, что было на экране (подсветка в таблице + панель «Было → Стало»).
   */
  async function compareAndRefresh(): Promise<void> {
    const previous = grid;
    if (!previous) {
      return;
    }
    comparing = true;
    error = '';
    try {
      const live = await getRows(instanceId, schemaName, tableName, PAGE_SIZE, offset);
      const result = diffRows(previous.columns, previous.rows, live.rows, keyColumns);
      grid = live;
      diff = result;
      annotation = annotateLiveRows(live.columns, live.rows, result);
      selectedRowKey = null;
    } catch (e) {
      error = toErrorMessage(e);
    } finally {
      comparing = false;
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

  function rowClass(index: number): string {
    const classes: string[] = [];
    const status = annotation?.statusByRow.get(index);
    if (status === 'modified') {
      classes.push('row-modified');
    } else if (status === 'added') {
      classes.push('row-added');
    }
    if (flashRow === index) {
      classes.push('flash');
    }
    return classes.join(' ');
  }

  function cellClass(index: number, col: number): string {
    return annotation?.changedCellsByRow.get(index)?.has(col) ? 'cell-changed' : '';
  }

  function cellTitle(index: number, col: number): string | undefined {
    if (!annotation?.changedCellsByRow.get(index)?.has(col)) {
      return undefined;
    }
    const previous = annotation.previousByRow.get(index);
    return previous ? `Было: ${formatCell(previous[col])}` : undefined;
  }

  function scrollToRow(index: number): void {
    const el = gridEl?.querySelector<HTMLElement>(`tr[data-row="${index}"]`);
    el?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    flashRow = index;
    if (flashTimer) {
      clearTimeout(flashTimer);
    }
    flashTimer = setTimeout(() => {
      if (flashRow === index) {
        flashRow = null;
      }
    }, 1200);
  }

  function selectRowByKey(keyValues: unknown[]): void {
    const current = grid;
    if (!current) {
      return;
    }
    const key = keyOfValues(keyValues);
    selectedRowKey = key;
    const indices = keyIndices(current.columns, keyColumns);
    const index = current.rows.findIndex((row) => keyOfValues(indices.map((i) => row[i])) === key);
    if (index >= 0) {
      scrollToRow(index);
    }
  }

  function selectGridRow(index: number): void {
    const current = grid;
    if (!current || !annotation?.statusByRow.has(index)) {
      return;
    }
    const row = current.rows[index];
    if (!row) {
      return;
    }
    const indices = keyIndices(current.columns, keyColumns);
    selectedRowKey = keyOfValues(indices.map((i) => row[i]));
  }
</script>

<div class="rows-explorer">
  <div class="head">
    <span class="lg">{schemaName}.{tableName}</span>
    <span class="spacer"></span>
    <button class="compare" onclick={() => void compareAndRefresh()} disabled={comparing || !grid}>
      {comparing ? 'Обновляю…' : 'Обновить и сравнить'}
    </button>
  </div>

  {#if error}<p class="error">{error}</p>{/if}

  {#if grid}
    <div class="grid-wrap" bind:this={gridEl}>
      <table>
        <thead>
          <tr>
            {#each grid.columns as col (col)}<th>{col}</th>{/each}
          </tr>
        </thead>
        <tbody>
          {#each grid.rows as row, i (i)}
            <tr
              data-row={i}
              class={rowClass(i)}
              role="button"
              tabindex="0"
              onclick={() => selectGridRow(i)}
              onkeydown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  selectGridRow(i);
                }
              }}
            >
              {#each row as cell, j (j)}
                <td class={cellClass(i, j)} title={cellTitle(i, j)}>{formatCell(cell)}</td>
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

    {#if diff}
      <div class="diff-host">
        {#key diff}
          <RowDiffView
            {diff}
            columns={grid.columns}
            selectedKey={selectedRowKey}
            onSelectRow={selectRowByKey}
          />
        {/key}
      </div>
    {/if}
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
  .diff-host {
    flex: 0 0 auto;
    max-height: 45%;
    overflow: auto;
  }
  .head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .spacer {
    flex: 1;
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
  tbody tr {
    cursor: pointer;
  }
  tbody tr.row-modified td {
    background: rgba(187, 128, 9, 0.12);
  }
  tbody tr.row-added td {
    background: rgba(46, 160, 67, 0.12);
  }
  tbody tr.flash td {
    animation: row-flash 1.2s ease-out;
  }
  td.cell-changed {
    outline: 2px solid #d29922;
    outline-offset: -2px;
    font-weight: 600;
  }
  @keyframes row-flash {
    from {
      background: #30363d;
    }
  }
  .pager {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 8px;
  }
</style>
