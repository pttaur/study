import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import DiffViewer from './DiffViewer.svelte';
import type { DiffResult, Instance } from '../lib/types';

const instance: Instance = {
  id: 1,
  label: 'pg-authorities-dict',
  connectionUrl: 'jdbc:postgresql://db.example.com:5432/demo',
  username: 'demo',
  kind: 'POSTGRES',
};

const diff: DiffResult = {
  baseInstanceId: 1,
  compareInstanceId: 2,
  schemas: [
    {
      name: 'public',
      type: 'MODIFIED',
      tables: [
        {
          name: 'users',
          type: 'MODIFIED',
          columns: [
            { name: 'email', type: 'ADDED' },
            { name: 'old_col', type: 'REMOVED' },
            { name: 'age', type: 'MODIFIED' },
          ],
          constraints: [],
          indexes: [],
        },
        {
          name: 'removed_t',
          type: 'REMOVED',
          columns: [],
          constraints: [],
          indexes: [],
        },
      ],
    },
    {
      name: 'new_schema',
      type: 'ADDED',
      tables: [],
    },
  ],
};

describe('DiffViewer', () => {
  it('renders nothing when there is no diff', () => {
    render(DiffViewer, { props: { diff: null, instance } });
    expect(screen.queryByText(/Дрифт: pg-authorities-dict/)).not.toBeInTheDocument();
  });

  it('shows the instance label and counts', () => {
    render(DiffViewer, { props: { diff, instance } });
    expect(screen.getByText(/Дрифт: pg-authorities-dict/)).toBeInTheDocument();
    expect(screen.getByText('добавлено: 2')).toBeInTheDocument();
    expect(screen.getByText('удалено: 2')).toBeInTheDocument();
    expect(screen.getByText(/изменено: 3/)).toBeInTheDocument();
  });

  it('lists changed elements with their diff type tags', () => {
    render(DiffViewer, { props: { diff, instance } });
    expect(screen.getByText('new_schema')).toBeInTheDocument();
    expect(screen.getByText('users')).toBeInTheDocument();
    expect(screen.getByText('removed_t')).toBeInTheDocument();
    expect(screen.getByText(/колонка email/)).toBeInTheDocument();
    expect(screen.getByText(/колонка old_col/)).toBeInTheDocument();
  });

  it('shows no-differences message for empty schemas', () => {
    const empty: DiffResult = { ...diff, schemas: [] };
    render(DiffViewer, { props: { diff: empty, instance } });
    expect(screen.getByText('Различий нет.')).toBeInTheDocument();
  });
});
