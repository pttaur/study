<script lang="ts">
  import type { DiffResult, DiffType, Instance, LeafDiff } from '../lib/types';

  let { instance, diff }: { instance: Instance; diff: DiffResult | null } = $props();

  const typeLabel: Record<DiffType, string> = {
    ADDED: 'added',
    REMOVED: 'removed',
    MODIFIED: 'modified',
  };

  function classes(tag: DiffType): string {
    return `d ${typeLabel[tag]}`;
  }

  function count(kind: 'ADDED' | 'REMOVED' | 'MODIFIED'): number {
    if (!diff) {
      return 0;
    }
    let total = 0;
    const countIn = (list: LeafDiff[]): void => {
      total += list.filter((d) => d.type === kind).length;
    };
    for (const schema of diff.schemas) {
      if (schema.type === kind) total += 1;
      for (const table of schema.tables) {
        if (table.type === kind) total += 1;
        countIn(table.columns);
        countIn(table.constraints);
        countIn(table.indexes);
      }
    }
    return total;
  }
</script>

{#if diff}
  <div class="panel">
    <div class="summary">
      <span class="lg">Дрифт: {instance.label}</span>
      <span>эталон → текущее</span>
      <span class="badges">
        <span class="b added">добавлено: {count('ADDED')}</span>
        <span class="b removed">удалено: {count('REMOVED')}</span>
        <span class="b modified">изменено: {count('MODIFIED')}</span>
      </span>
    </div>

    {#if diff.schemas.length === 0}
      <p class="muted">Различий нет.</p>
    {/if}

    {#each diff.schemas as schema (schema.name)}
      <div class="group">
        <div class="head {classes(schema.type)}">
          <span class="glyph"
            >{schema.type === 'ADDED' ? '+' : schema.type === 'REMOVED' ? '−' : '~'}</span
          >
          {schema.name}
          <span class="tag {typeLabel[schema.type]}">{typeLabel[schema.type]}</span>
        </div>
        {#if schema.tables.length > 0}
          {#each schema.tables as table (table.name)}
            <div class="table diff-row {classes(table.type)}">
              <span class="glyph"
                >{table.type === 'ADDED' ? '+' : table.type === 'REMOVED' ? '−' : '~'}</span
              >
              {table.name}
              <span class="tag {typeLabel[table.type]}">{typeLabel[table.type]}</span>

              {#if table.columns.length > 0}
                <ul>
                  {#each table.columns as col (col.name)}
                    <li class="leaf {classes(col.type)}">
                      {col.type === 'ADDED' ? '+' : col.type === 'REMOVED' ? '−' : '~'} колонка {col.name}
                    </li>
                  {/each}
                </ul>
              {/if}
              {#if table.constraints.length > 0}
                <ul>
                  {#each table.constraints as c (c.name)}
                    <li class="leaf {classes(c.type)}">
                      {c.type === 'ADDED' ? '+' : c.type === 'REMOVED' ? '−' : '~'} ограничение {c.name}
                    </li>
                  {/each}
                </ul>
              {/if}
              {#if table.indexes.length > 0}
                <ul>
                  {#each table.indexes as idx (idx.name)}
                    <li class="leaf {classes(idx.type)}">
                      {idx.type === 'ADDED' ? '+' : idx.type === 'REMOVED' ? '−' : '~'} индекс {idx.name}
                    </li>
                  {/each}
                </ul>
              {/if}
            </div>
          {/each}
        {/if}
      </div>
    {/each}
  </div>
{/if}

<style>
  .summary {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }
  .badges {
    display: inline-flex;
    gap: 8px;
    margin-left: auto;
  }
  .b {
    font-size: 12px;
    border-radius: 4px;
    padding: 2px 8px;
    color: #0d1117;
    font-weight: 700;
  }
  .b.added {
    background: var(--added);
  }
  .b.removed {
    background: var(--removed);
  }
  .b.modified {
    background: var(--modified);
  }
  .group {
    margin-top: 10px;
  }
  .head {
    font-weight: 600;
    padding: 4px 6px;
    border-radius: 6px;
  }
  .diff-row {
    margin-left: 16px;
    margin-top: 4px;
  }
  .d {
    border: 1px solid var(--border);
  }
  .d.added {
    background: rgba(74, 222, 128, 0.08);
  }
  .d.removed {
    background: rgba(248, 113, 113, 0.08);
  }
  .d.modified {
    background: rgba(251, 191, 36, 0.08);
  }
  .glyph {
    display: inline-block;
    width: 16px;
    font-weight: 700;
  }
  ul {
    margin: 2px 0 6px 20px;
    padding-left: 16px;
  }
  .leaf {
    list-style: none;
  }
</style>
