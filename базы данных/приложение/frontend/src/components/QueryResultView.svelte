<script lang="ts">
  import type { QueryHistoryEntry, QueryResult } from '../lib/types';
  import { formatCell } from '../lib/format';
  import { formatSql, formatTimestamp } from '../lib/sqlFormat';

  let {
    result,
    history,
    tab = $bindable<'result' | 'history'>('result'),
    running = false,
    historyLoading = false,
    error = '',
    onPrevPage,
    onNextPage,
    onLoadHistory,
  }: {
    result: QueryResult | null;
    history: QueryHistoryEntry[];
    tab?: 'result' | 'history';
    running?: boolean;
    historyLoading?: boolean;
    error?: string;
    onPrevPage?: () => void;
    onNextPage?: () => void;
    onLoadHistory?: () => void;
  } = $props();

  async function copySql(sql: string): Promise<void> {
    try {
      await navigator.clipboard?.writeText(sql);
    } catch {
      // буфер обмена может быть недоступен — молча игнорируем
    }
  }
</script>

<div class="result-view">
  <div class="tabs">
    <button class:active={tab === 'result'} onclick={() => (tab = 'result')}>Результат</button>
    <button
      class:active={tab === 'history'}
      onclick={() => {
        tab = 'history';
        onLoadHistory?.();
      }}
    >
      История
    </button>
  </div>

  {#if error}<p class="error">{error}</p>{/if}
  {#if running}<p class="muted">Выполняю…</p>{/if}

  {#if tab === 'result'}
    {#if result}
      <div class="head">
        <span class="muted">
          {result.rows.length} из {result.totalRows} строк · {result.durationMs} мс{result.truncated
            ? ' · показаны не все'
            : ''}
        </span>
        <span class="spacer"></span>
        {#if result.totalRows > 0}
          <button onclick={() => onPrevPage?.()} disabled={running || result.offset === 0}>
            ← назад
          </button>
          <span class="muted">
            {result.offset + 1}–{result.offset + result.rows.length} из {result.totalRows}
          </span>
          <button
            onclick={() => onNextPage?.()}
            disabled={running || result.offset + result.rows.length >= result.totalRows}
          >
            вперёд →
          </button>
        {/if}
      </div>
      <div class="grid-wrap">
        <table>
          <thead>
            <tr>
              {#each result.columns as column (column.name)}
                <th>{column.name}<span class="type">{column.type}</span></th>
              {/each}
            </tr>
          </thead>
          <tbody>
            {#each result.rows as row, i (i)}
              <tr>
                {#each row as value, j (j)}<td>{formatCell(value)}</td>{/each}
              </tr>
            {/each}
          </tbody>
        </table>
        {#if result.rows.length === 0}
          <p class="muted">Нет строк.</p>
        {/if}
      </div>
    {:else if !running && !error}
      <p class="muted">Соберите джойн слева и нажмите «Выполнить».</p>
    {/if}
  {:else if historyLoading}
    <p class="muted">Загружаю…</p>
  {:else if history.length > 0}
    <div class="grid-wrap history">
      {#each history as entry (entry.id)}
        <div class="hist-item">
          <div class="hist-head">
            <span class="status" class:ok={entry.status === 'OK'}>{entry.status}</span>
            <span class="muted">{formatTimestamp(entry.createdAt)}</span>
            <span class="muted">{entry.rowCount ?? '—'} строк</span>
            <span class="muted">{entry.durationMs ?? '—'} мс</span>
            <span class="spacer"></span>
            <button class="mini" onclick={() => void copySql(entry.sql)}>копировать</button>
          </div>
          <pre class="sql-text">{formatSql(entry.sql)}</pre>
          {#if entry.error}<p class="error hist-error">{entry.error}</p>{/if}
        </div>
      {/each}
    </div>
  {:else}
    <p class="muted">История пуста.</p>
  {/if}
</div>

<style>
  .result-view {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px;
  }
  .tabs {
    flex: 0 0 auto;
    display: flex;
    gap: 4px;
  }
  .tabs button.active {
    background: #1f6feb;
  }
  .head {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-top: 8px;
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
  .history {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .hist-item {
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 8px;
    background: var(--panel);
  }
  .hist-head {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    font-size: 12px;
  }
  .status {
    padding: 1px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 700;
    background: rgba(248, 81, 73, 0.15);
    color: var(--removed-fg);
  }
  .status.ok {
    background: rgba(46, 160, 67, 0.15);
    color: var(--added-fg);
  }
  .sql-text {
    margin: 6px 0 0;
    padding: 8px;
    background: var(--inset);
    border: 1px solid var(--border);
    border-radius: 6px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    white-space: pre-wrap;
    word-break: break-word;
    color: var(--text);
  }
  .hist-error {
    margin: 6px 0 0;
    font-size: 12px;
  }
  .mini {
    padding: 1px 8px;
    font-size: 11px;
  }
  .type {
    display: block;
    font-size: 10px;
    color: var(--muted);
  }
</style>
