<script lang="ts">
  import {
    changedColumnNames,
    keyIndices,
    keyOfValues,
    type ModifiedRow,
    type RowDiffResult,
  } from '../lib/rowDiff';
  import { formatCell } from '../lib/format';

  let {
    diff,
    columns,
    onSelectRow,
    selectedKey = null,
  }: {
    diff: RowDiffResult;
    columns: string[];
    onSelectRow?: (keyValues: unknown[]) => void;
    selectedKey?: string | null;
  } = $props();

  const PAGE = 25;

  type Item =
    | { id: string; kind: 'added'; row: unknown[] }
    | { id: string; kind: 'removed'; row: unknown[] }
    | { id: string; kind: 'modified'; change: ModifiedRow };

  type Filter = 'all' | 'added' | 'removed' | 'modified';

  let page = $state(0);
  let filter = $state<Filter>('all');
  let showAllColumns = $state(false);
  let collapsed = $state<Record<string, boolean>>({});
  let listEl = $state<HTMLElement | null>(null);

  const keyCols = $derived(diff.keyColumns);
  const indexOfColumn = $derived(new Map(columns.map((c, i) => [c, i])));
  const hasRowChanges = $derived(diff.added.length + diff.removed.length > 0);
  const dataColumns = $derived.by(() => {
    const source = showAllColumns || hasRowChanges ? columns : changedColumnNames(columns, diff);
    return source.filter((c) => !keyCols.includes(c));
  });

  let items = $derived.by((): Item[] => {
    const result: Item[] = [];
    diff.added.forEach((row, i) => result.push({ id: `added-${i}`, kind: 'added', row }));
    diff.removed.forEach((row, i) => result.push({ id: `removed-${i}`, kind: 'removed', row }));
    diff.modified.forEach((change, i) =>
      result.push({ id: `modified-${i}`, kind: 'modified', change }),
    );
    return result;
  });

  let filtered = $derived(items.filter((item) => filter === 'all' || item.kind === filter));
  let pageCount = $derived(Math.max(1, Math.ceil(filtered.length / PAGE)));
  let visible = $derived(filtered.slice(page * PAGE, page * PAGE + PAGE));

  $effect(() => {
    void visible;
    if (!selectedKey || !listEl) {
      return;
    }
    const node = Array.from(listEl.querySelectorAll<HTMLElement>('[data-key]')).find(
      (el) => el.dataset.key === selectedKey,
    );
    node?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  });

  function itemKeyValues(item: Item): unknown[] {
    if (item.kind === 'modified') {
      return item.change.key;
    }
    return keyIndices(columns, keyCols).map((i) => item.row[i]);
  }

  function itemKeyLabel(item: Item): string {
    const values = itemKeyValues(item);
    if (keyCols.length === 0) {
      return item.kind !== 'modified' && columns.length > 0
        ? `${columns[0]}=${formatCell(item.row[0])}`
        : 'строка';
    }
    return keyCols.map((name, i) => `${name}=${formatCell(values[i])}`).join(' · ');
  }

  function itemNote(item: Item): string {
    if (item.kind === 'modified') {
      return `${item.change.changedColumns.length} изм.`;
    }
    return item.kind === 'added' ? 'новая строка' : 'удалённая строка';
  }

  function mark(item: Item): string {
    return item.kind === 'added' ? '+' : item.kind === 'removed' ? '−' : '~';
  }

  function baseValue(item: Item, col: number): unknown {
    if (item.kind === 'modified') {
      return item.change.baseline[col];
    }
    return item.kind === 'removed' ? item.row[col] : undefined;
  }

  function liveValue(item: Item, col: number): unknown {
    if (item.kind === 'modified') {
      return item.change.live[col];
    }
    return item.kind === 'added' ? item.row[col] : undefined;
  }

  function showBase(item: Item): boolean {
    return item.kind !== 'added';
  }

  function showLive(item: Item): boolean {
    return item.kind !== 'removed';
  }

  function isChangedCell(item: Item, col: number): boolean {
    return item.kind === 'modified' && item.change.changedColumns.includes(col);
  }

  function isSelected(item: Item): boolean {
    return selectedKey !== null && keyOfValues(itemKeyValues(item)) === selectedKey;
  }

  function isCollapsed(id: string): boolean {
    return collapsed[id] ?? false;
  }

  function toggleCollapsed(id: string): void {
    collapsed = { ...collapsed, [id]: !collapsed[id] };
  }

  function toggleFilter(value: 'added' | 'removed' | 'modified'): void {
    filter = filter === value ? 'all' : value;
    page = 0;
  }
</script>

<div class="row-diff">
  <div class="summary">
    <button class="tag same" class:active={filter === 'all'} onclick={() => (filter = 'all')}>
      Все ({items.length})
    </button>
    <button
      class="tag add"
      class:active={filter === 'added'}
      disabled={diff.added.length === 0}
      onclick={() => toggleFilter('added')}
    >
      +{diff.added.length} добавлено
    </button>
    <button
      class="tag del"
      class:active={filter === 'removed'}
      disabled={diff.removed.length === 0}
      onclick={() => toggleFilter('removed')}
    >
      −{diff.removed.length} удалено
    </button>
    <button
      class="tag mod"
      class:active={filter === 'modified'}
      disabled={diff.modified.length === 0}
      onclick={() => toggleFilter('modified')}
    >
      ~{diff.modified.length} изменено
    </button>
    <span class="tag same">={diff.unchanged} без изменений</span>
    {#if !hasRowChanges}
      <span class="spacer"></span>
      <button
        class="toggle"
        onclick={() => {
          showAllColumns = !showAllColumns;
          page = 0;
        }}
      >
        {showAllColumns ? 'все колонки' : 'только изменённые'}
      </button>
    {/if}
  </div>

  {#if items.length === 0}
    <p class="muted">Изменений с момента загрузки нет.</p>
  {:else if filtered.length === 0}
    <p class="muted">Нет строк выбранного типа.</p>
  {:else}
    <div class="list" bind:this={listEl}>
      {#each visible as item (item.id)}
        <div
          class={`item ${item.kind}`}
          class:selected={isSelected(item)}
          data-key={keyOfValues(itemKeyValues(item))}
        >
          <div class="item-bar">
            <button class="item-head" onclick={() => toggleCollapsed(item.id)}>
              <span class="chev">{isCollapsed(item.id) ? '▸' : '▾'}</span>
              <span class="mark">{mark(item)}</span>
              <span class="key">{itemKeyLabel(item)}</span>
              <span class="note">{itemNote(item)}</span>
            </button>
            {#if item.kind !== 'added' && onSelectRow}
              <button
                class="jump"
                title="Показать строку в таблице"
                onclick={() => onSelectRow(itemKeyValues(item))}
              >
                → к строке
              </button>
            {/if}
          </div>

          {#if !isCollapsed(item.id)}
            <div class="scroll">
              <table>
                <thead>
                  <tr>
                    {#each keyCols as kc (kc)}<th class="kc" rowspan="2">{kc}</th>{/each}
                    {#if dataColumns.length > 0}
                      <th class="grp base" colspan={dataColumns.length}>Эталон (экран)</th>
                      <th class="grp live" colspan={dataColumns.length}>БД (live)</th>
                    {/if}
                  </tr>
                  {#if dataColumns.length > 0}
                    <tr>
                      {#each dataColumns as c (c)}<th class="base">{c}</th>{/each}
                      {#each dataColumns as c (`live-${c}`)}<th class="live">{c}</th>{/each}
                    </tr>
                  {/if}
                </thead>
                <tbody>
                  <tr>
                    {#each itemKeyValues(item) as value, k (k)}<td class="kc"
                        >{formatCell(value)}</td
                      >{/each}
                    {#if dataColumns.length > 0}
                      {#each dataColumns as c (c)}
                        {@const col = indexOfColumn.get(c) ?? -1}
                        {#if showBase(item)}
                          <td class="base" class:changed={isChangedCell(item, col)}>
                            {formatCell(baseValue(item, col))}
                          </td>
                        {:else}
                          <td class="base empty">—</td>
                        {/if}
                      {/each}
                      {#each dataColumns as c (`live-${c}`)}
                        {@const col = indexOfColumn.get(c) ?? -1}
                        {#if showLive(item)}
                          <td class="live" class:changed={isChangedCell(item, col)}>
                            {formatCell(liveValue(item, col))}
                          </td>
                        {:else}
                          <td class="live empty">—</td>
                        {/if}
                      {/each}
                    {/if}
                  </tr>
                </tbody>
              </table>
            </div>
          {/if}
        </div>
      {/each}
    </div>

    {#if pageCount > 1}
      <div class="pager">
        <button onclick={() => (page = Math.max(0, page - 1))} disabled={page === 0}>
          ← назад
        </button>
        <span class="muted">
          {page * PAGE + 1}–{Math.min((page + 1) * PAGE, filtered.length)} из {filtered.length}
        </span>
        <button
          onclick={() => (page = Math.min(pageCount - 1, page + 1))}
          disabled={page >= pageCount - 1}
        >
          вперёд →
        </button>
      </div>
    {/if}
  {/if}
</div>

<style>
  .row-diff {
    margin-top: 8px;
    padding: 8px;
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  .summary {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .spacer {
    flex: 1;
  }
  .tag {
    padding: 1px 8px;
    border: 1px solid transparent;
    border-radius: 999px;
    font-size: 12px;
    cursor: pointer;
    font-family: inherit;
  }
  .tag:disabled {
    cursor: default;
    opacity: 0.5;
  }
  .tag.active {
    border-color: currentColor;
    outline: 1px solid currentColor;
  }
  .tag.add {
    background: rgba(46, 160, 67, 0.15);
    color: #7ee787;
  }
  .tag.del {
    background: rgba(248, 81, 73, 0.15);
    color: #ffa198;
  }
  .tag.mod {
    background: rgba(187, 128, 9, 0.15);
    color: #e3b341;
  }
  .tag.same {
    background: #161b22;
    color: var(--muted);
  }
  .toggle {
    font-size: 12px;
    padding: 2px 8px;
    cursor: pointer;
  }
  .list {
    margin-top: 8px;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .item {
    border: 1px solid var(--border);
    border-radius: 4px;
    overflow: hidden;
  }
  .item.selected {
    outline: 2px solid #58a6ff;
  }
  .item-bar {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 2px 6px;
  }
  .item.add .item-bar {
    background: rgba(46, 160, 67, 0.15);
  }
  .item.del .item-bar {
    background: rgba(248, 81, 73, 0.15);
  }
  .item.mod .item-bar {
    background: rgba(187, 128, 9, 0.15);
  }
  .item-head {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    background: none;
    border: none;
    color: inherit;
    font: inherit;
    font-size: 13px;
    text-align: left;
    padding: 2px;
    cursor: pointer;
  }
  .item.add .item-head {
    color: #7ee787;
  }
  .item.del .item-head {
    color: #ffa198;
  }
  .item.mod .item-head {
    color: #e3b341;
  }
  .chev {
    width: 12px;
  }
  .mark {
    font-weight: 700;
  }
  .key {
    font-weight: 600;
  }
  .note {
    opacity: 0.75;
  }
  .jump {
    font-size: 12px;
    white-space: nowrap;
  }
  .scroll {
    overflow-x: auto;
    border-top: 1px solid var(--border);
  }
  table {
    border-collapse: collapse;
    font-size: 12px;
    white-space: nowrap;
  }
  th,
  td {
    border: 1px solid var(--border);
    padding: 2px 8px;
    text-align: left;
  }
  th.kc,
  td.kc {
    background: #161b22;
    font-weight: 600;
  }
  th.base,
  td.base {
    background: rgba(248, 81, 73, 0.06);
  }
  th.live,
  td.live {
    background: rgba(46, 160, 67, 0.06);
  }
  th.grp {
    text-align: center;
    font-size: 11px;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  th.grp.base {
    color: #ffa198;
  }
  th.grp.live {
    color: #7ee787;
  }
  td.changed {
    font-weight: 600;
  }
  td.base.changed {
    background: rgba(248, 81, 73, 0.15);
    color: #ffa198;
  }
  td.live.changed {
    background: rgba(46, 160, 67, 0.15);
    color: #7ee787;
  }
  td.empty {
    color: var(--muted);
    text-align: center;
  }
  .pager {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-top: 8px;
  }
</style>
