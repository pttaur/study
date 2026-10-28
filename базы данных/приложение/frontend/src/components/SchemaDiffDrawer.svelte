<script lang="ts">
  import type { DiffResult, Instance } from '../lib/types';
  import DiffViewer from './DiffViewer.svelte';

  let {
    instance,
    diff,
    open = $bindable(false),
  }: { instance: Instance; diff: DiffResult | null; open?: boolean } = $props();
</script>

<div class="diff-drawer" class:collapsed={!open}>
  <button class="drawer-head" onclick={() => (open = !open)}>
    <span class="chev">{open ? '▾' : '▸'}</span>
    Схема-дифф: {instance.label}
  </button>
  {#if open}
    <div class="drawer-body">
      <DiffViewer {instance} {diff} />
    </div>
  {/if}
</div>

<style>
  .diff-drawer {
    flex: 0 0 auto;
    display: flex;
    flex-direction: column;
    max-height: 45%;
    border: 1px solid var(--border);
    border-radius: 8px;
    overflow: hidden;
  }
  .diff-drawer.collapsed {
    max-height: none;
  }
  .drawer-head {
    text-align: left;
    border: none;
    border-radius: 0;
    background: var(--panel);
  }
  .drawer-head .chev {
    display: inline-block;
    width: 14px;
  }
  .drawer-body {
    flex: 1;
    min-height: 0;
    overflow: auto;
    padding: 8px;
  }
</style>
