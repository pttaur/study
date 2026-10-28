<script lang="ts">
  import type { InstanceStore } from '../lib/instances.svelte';
  import type { TableSelection } from '../lib/types';
  import InstanceForm from './InstanceForm.svelte';
  import SchemaTree from './SchemaTree.svelte';

  let {
    store,
    selectedKey,
    onSelectTable,
    onAddToJoin,
    collapsed,
    width,
    onOpenDiff,
  }: {
    store: InstanceStore;
    selectedKey: string;
    onSelectTable: (selection: TableSelection) => void;
    onAddToJoin: (ref: string) => void;
    collapsed: boolean;
    width: number;
    onOpenDiff: () => void;
  } = $props();

  async function checkDrift(instanceId: number): Promise<void> {
    if (await store.check(instanceId)) {
      onOpenDiff();
    }
  }
</script>

<aside class="dock" class:collapsed style={`--dock-w: ${width}px`}>
  <div class="dock-body">
    <InstanceForm onCreate={(instance) => void store.create(instance)} />

    {#each store.instances as instance (instance.id)}
      <div class="instance-node">
        <div class="instance-head">
          <span class="lg">{instance.label}</span>
          <button title="Обновить схему" onclick={() => void store.loadSchema(instance.id)}>
            ↻
          </button>
          <button
            title="Сравнить схему с текущей БД"
            onclick={() => void checkDrift(instance.id)}
            disabled={store.checking.has(instance.id)}
          >
            {store.checking.has(instance.id) ? '…' : 'Check drift'}
          </button>
        </div>
        <SchemaTree
          catalog={store.catalogs.get(instance.id) ?? null}
          instanceLabel={instance.label}
          instanceName={instance.label}
          {selectedKey}
          {onSelectTable}
          {onAddToJoin}
        />
      </div>
    {/each}
  </div>
</aside>

<style>
  .dock {
    width: var(--dock-w, 340px);
    flex: 0 0 auto;
    display: flex;
    flex-direction: column;
    min-height: 0;
    background: var(--panel);
    border-right: 1px solid var(--border);
    overflow: hidden;
    transition: width 0.15s ease;
  }
  .dock.collapsed {
    width: 0;
    border-right: none;
  }
  .dock-body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: auto;
    padding: 8px;
  }
  .instance-node {
    margin-top: 12px;
    padding: 8px;
    border: 1px solid var(--border);
    border-radius: 8px;
  }
  .instance-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .instance-head .lg {
    flex: 1;
  }
</style>
