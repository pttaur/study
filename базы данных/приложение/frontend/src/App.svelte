<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import type { Instance, Table, TableSelection } from './lib/types';
  import { InstanceStore, selectedTableKey } from './lib/instances.svelte';
  import { JoinStore } from './lib/join.svelte';
  import InstanceDock from './components/InstanceDock.svelte';
  import JoinBuilder from './components/JoinBuilder.svelte';
  import SchemaDiffDrawer from './components/SchemaDiffDrawer.svelte';
  import DataPanel from './components/DataPanel.svelte';
  import QueryResultView from './components/QueryResultView.svelte';

  const store = new InstanceStore();
  const join = new JoinStore();

  let selected = $state<TableSelection | null>(null);
  let view = $state<'data' | 'join'>('data');
  let dockCollapsed = $state(false);
  let dockWidth = $state(340);
  let showSchemaDiff = $state(true);
  let clientError = $state('');
  let stopResize: (() => void) | null = null;

  function onWindowError(event: ErrorEvent): void {
    clientError = event.message || 'Ошибка на клиенте';
  }

  function onUnhandledRejection(event: PromiseRejectionEvent): void {
    clientError =
      event.reason instanceof Error
        ? event.reason.message
        : String(event.reason ?? 'Ошибка на клиенте');
  }

  onMount(() => {
    window.addEventListener('error', onWindowError);
    window.addEventListener('unhandledrejection', onUnhandledRejection);
    void store.refresh();
    return () => {
      window.removeEventListener('error', onWindowError);
      window.removeEventListener('unhandledrejection', onUnhandledRejection);
      store.dispose();
    };
  });

  onDestroy(() => {
    stopResize?.();
  });

  let selectedKey = $derived(selected ? selectedTableKey(selected) : '');

  let selectedInstance = $derived.by((): Instance | null => {
    if (!selected) {
      return null;
    }
    const current = selected;
    return store.instances.find((i) => i.id === current.instanceId) ?? null;
  });

  let selectedTable = $derived.by((): Table | null => {
    if (!selected) {
      return null;
    }
    const current = selected;
    const catalog = store.catalogs.get(current.instanceId);
    const schema = catalog?.schemas.find((s) => s.schemaName === current.schemaName);
    return schema?.tables.find((t) => t.tableName === current.tableName) ?? null;
  });

  function selectTable(selection: TableSelection): void {
    selected = selection;
    view = 'data';
  }

  function startResize(event: PointerEvent): void {
    if (dockCollapsed) {
      return;
    }
    const startX = event.clientX;
    const startWidth = dockWidth;
    const onMove = (e: PointerEvent): void => {
      dockWidth = Math.min(720, Math.max(220, startWidth + e.clientX - startX));
    };
    const onEnd = (): void => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
      stopResize = null;
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
    stopResize = onEnd;
  }
</script>

<main class="app">
  <header class="topbar">
    <h1>Schema Catalog Aggregator</h1>
    {#if store.initialError}
      <span class="error">Не удалось загрузить инстансы: {store.initialError}</span>
    {/if}
    {#if store.error}<span class="error">{store.error}</span>{/if}
    {#if clientError}<span class="error">Клиентская ошибка: {clientError}</span>{/if}
  </header>

  <div class="body">
    <InstanceDock
      {store}
      {selectedKey}
      onSelectTable={selectTable}
      onAddToJoin={(ref) => {
        join.addTable(ref);
        view = 'join';
      }}
      collapsed={dockCollapsed}
      width={dockWidth}
      onOpenDiff={() => (showSchemaDiff = true)}
    />

    <div
      class="resizer"
      class:hidden={dockCollapsed}
      role="separator"
      aria-orientation="vertical"
      aria-label="Изменить ширину панели"
      onpointerdown={startResize}
    ></div>

    <button
      class="dock-toggle"
      title={dockCollapsed ? 'Развернуть панель' : 'Свернуть панель'}
      onclick={() => (dockCollapsed = !dockCollapsed)}
    >
      {dockCollapsed ? '»' : '«'}
    </button>

    <section class="content">
      <div class="view-tabs">
        <button class:active={view === 'data'} onclick={() => (view = 'data')}>Данные</button>
        <button class:active={view === 'join'} onclick={() => (view = 'join')}>Джойн</button>
      </div>

      {#if view === 'join'}
        <JoinBuilder store={join} instances={store.instances} catalogs={store.catalogs} />
        <QueryResultView
          result={join.result}
          history={join.history}
          bind:tab={join.tab}
          running={join.running}
          historyLoading={join.historyLoading}
          error={join.error}
          onPrevPage={() => void join.prevPage()}
          onNextPage={() => void join.nextPage()}
          onLoadHistory={() => void join.loadHistory()}
        />
      {:else}
        {#if selected && selectedTable}
          <DataPanel
            instanceId={selected.instanceId}
            schemaName={selected.schemaName}
            table={selectedTable}
          />
        {:else}
          <p class="muted placeholder">Выберите таблицу в дереве слева, чтобы увидеть данные.</p>
        {/if}

        {#if selectedInstance && store.diffs.get(selectedInstance.id)}
          <SchemaDiffDrawer
            instance={selectedInstance}
            diff={store.diffs.get(selectedInstance.id) ?? null}
            bind:open={showSchemaDiff}
          />
        {/if}
      {/if}
    </section>
  </div>
</main>

<style>
  .app {
    height: 100vh;
    display: flex;
    flex-direction: column;
  }
  .topbar {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 12px;
    border-bottom: 1px solid var(--border);
  }
  .topbar h1 {
    font-size: 16px;
    margin: 0;
  }
  .body {
    flex: 1;
    display: flex;
    min-height: 0;
  }
  .resizer {
    flex: 0 0 auto;
    width: 5px;
    cursor: col-resize;
  }
  .resizer:hover {
    background: var(--accent);
  }
  .resizer.hidden {
    display: none;
  }
  .dock-toggle {
    flex: 0 0 auto;
    width: 20px;
    padding: 0;
    border: none;
    border-radius: 0;
    background: transparent;
    color: var(--muted);
  }
  .dock-toggle:hover {
    color: var(--text);
  }
  .content {
    flex: 1;
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 8px;
  }
  .view-tabs {
    flex: 0 0 auto;
    display: flex;
    gap: 4px;
  }
  .view-tabs button.active {
    background: #1f6feb;
  }
  .placeholder {
    margin: 0;
  }
</style>
