import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import RowDiffView from './RowDiffView.svelte';
import { diffRows, type RowDiffResult } from '../lib/rowDiff';

function diffWith(addedCount: number): RowDiffResult {
  return {
    keyColumns: ['id'],
    added: Array.from({ length: addedCount }, (_, i) => [i + 1, `u${i}@b.c`]),
    removed: [],
    modified: [],
    unchanged: 0,
  };
}

describe('RowDiffView', () => {
  it('paginates large change sets', async () => {
    render(RowDiffView, { props: { diff: diffWith(30), columns: ['id', 'email'] } });

    expect(screen.getByText('1–25 из 30')).toBeInTheDocument();
    expect(screen.queryByText('26–30 из 30')).not.toBeInTheDocument();

    await userEvent.setup().click(screen.getByText('вперёд →'));

    expect(screen.getByText('26–30 из 30')).toBeInTheDocument();
  });

  it('does not paginate a small change set', () => {
    render(RowDiffView, { props: { diff: diffWith(3), columns: ['id', 'email'] } });

    expect(screen.getByText('+3 добавлено')).toBeInTheDocument();
    expect(screen.queryByText('вперёд →')).not.toBeInTheDocument();
  });

  it('reports no changes for an empty diff', () => {
    render(RowDiffView, { props: { diff: diffWith(0), columns: ['id'] } });
    expect(screen.getByText('Изменений с момента загрузки нет.')).toBeInTheDocument();
  });

  it('renders baseline and live values side by side for modified rows', () => {
    const columns = ['id', 'email'];
    const diff = diffRows(columns, [[1, 'a@b.c']], [[1, 'z@b.c']], ['id']);

    render(RowDiffView, { props: { diff, columns } });

    expect(screen.getByText('Эталон (экран)')).toBeInTheDocument();
    expect(screen.getByText('БД (live)')).toBeInTheDocument();
    expect(screen.getByText('id=1')).toBeInTheDocument();
    expect(screen.getByText('a@b.c')).toBeInTheDocument();
    expect(screen.getByText('z@b.c')).toBeInTheDocument();
  });

  it('filters rows by change type', async () => {
    const columns = ['id', 'email'];
    const diff = diffRows(
      columns,
      [[1, 'a@b.c']],
      [
        [1, 'z@b.c'],
        [2, 'n@b.c'],
      ],
      ['id'],
    );

    render(RowDiffView, { props: { diff, columns } });

    expect(screen.getByText('id=1')).toBeInTheDocument();
    expect(screen.getByText('id=2')).toBeInTheDocument();

    await userEvent.setup().click(screen.getByText('~1 изменено'));

    expect(screen.getByText('id=1')).toBeInTheDocument();
    expect(screen.queryByText('id=2')).not.toBeInTheDocument();
  });

  it('toggles between changed-only and all columns', async () => {
    const columns = ['id', 'email', 'name'];
    const diff = diffRows(columns, [[1, 'a@b.c', 'Al']], [[1, 'z@b.c', 'Al']], ['id']);

    render(RowDiffView, { props: { diff, columns } });

    expect(screen.queryByText('name')).not.toBeInTheDocument();

    await userEvent.setup().click(screen.getByText('только изменённые'));

    expect(screen.getAllByText('name')).toHaveLength(2);
  });
});
