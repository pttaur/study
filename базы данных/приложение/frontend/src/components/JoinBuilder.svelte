<script lang="ts">
  import type { JoinStore } from '../lib/join.svelte';
  import type { Catalog, Instance } from '../lib/types';
  import { availableTables } from '../lib/tables';
  import JoinTableCard from './JoinTableCard.svelte';

  let {
    store,
    instances,
    catalogs,
  }: { store: JoinStore; instances: Instance[]; catalogs: Map<number, Catalog> } = $props();

  const MAX_RESULTS = 200;

  let options = $derived(availableTables(instances, catalogs));
  let search = $state('');
  let notice = $state('');

  let filtered = $derived.by(() => {
    const query = search.trim().toLowerCase();
    if (!query) {
      return options;
    }
    return options.filter((option) => option.ref.toLowerCase().includes(query));
  });
  let visible = $derived(filtered.slice(0, MAX_RESULTS));

  function addTable(ref: string): void {
    if (store.hasTable(ref)) {
      notice = `Таблица уже добавлена: ${ref}`;
      return;
    }
    store.addTable(ref);
    notice = '';
  }

  function onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && visible[0]) {
      event.preventDefault();
      addTable(visible[0].ref);
    }
  }
</script>

<div class="builder">
  <div class="toolbar">
    <input
      class="search"
      bind:value={search}
      placeholder="Поиск таблицы: instance.schema.table"
      spellcheck="false"
      onkeydown={onSearchKeydown}
    />
    <span class="muted">найдено: {filtered.length}</span>
    <span class="spacer"></span>
    <button class="run" onclick={() => void store.run()} disabled={store.running}>
      {store.running ? 'Выполняю…' : '▶ Выполнить'}
    </button>
    <button onclick={() => store.clear()}>Очистить</button>
  </div>

  {#if notice}<p class="error">{notice}</p>{/if}
  {#if store.error}<p class="error">{store.error}</p>{/if}

  {#if options.length === 0}
    <p class="muted">
      Нет доступных таблиц. Загрузите схемы инстансов (название используется в ссылках
      instance.schema.table).
    </p>
  {:else}
    <div class="results">
      {#each visible as option (option.ref)}
        <button
          class="result"
          class:added={store.hasTable(option.ref)}
          title={store.hasTable(option.ref) ? 'Уже добавлена' : 'Добавить в джойн'}
          onclick={() => addTable(option.ref)}
        >
          {option.ref}
        </button>
      {/each}
      {#if filtered.length === 0}
        <span class="muted">ничего не найдено</span>
      {:else if filtered.length > visible.length}
        <span class="muted">… ещё {filtered.length - visible.length}, уточните поиск</span>
      {/if}
    </div>
  {/if}

  {#if store.tables.length === 0}
    <p class="muted">
      Добавьте таблицы (кнопкой выше или «+» в дереве слева), отметьте колонки и задайте условия
      соединения.
    </p>
  {:else}
    {#each store.tables as table, index (table.ref)}
      <JoinTableCard {store} {index} {options} />
    {/each}
  {/if}
</div>

<style>
  .builder {
    flex: 0 0 auto;
    display: flex;
    flex-direction: column;
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 12px;
  }
  .toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
    margin-bottom: 8px;
  }
  .search {
    flex: 1;
    min-width: 260px;
  }
  .spacer {
    flex: 1;
  }
  .results {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    max-height: 170px;
    overflow: auto;
    margin-bottom: 8px;
    padding: 4px;
    border: 1px solid var(--border);
    border-radius: 6px;
  }
  .result {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    padding: 2px 8px;
    font-weight: normal;
  }
  .result.added {
    opacity: 0.5;
  }
</style>
