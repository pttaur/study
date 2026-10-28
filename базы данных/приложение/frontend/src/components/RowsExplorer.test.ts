import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import RowsExplorer from './RowsExplorer.svelte';
import { getRows } from '../lib/api';

vi.mock('../lib/api', () => ({
  getRows: vi.fn(),
}));

const props = { instanceId: 1, schemaName: 'public', tableName: 'users' };

describe('RowsExplorer', () => {
  it('renders grid columns and rows', async () => {
    vi.mocked(getRows).mockResolvedValue({
      columns: ['id', 'email'],
      rows: [[1, 'a@b.c']],
      totalRows: 1,
    });

    render(RowsExplorer, { props });

    await waitFor(() => {
      expect(screen.getByText('id')).toBeInTheDocument();
      expect(screen.getByText('a@b.c')).toBeInTheDocument();
    });
  });

  it('refreshes data and shows a row-level diff after "Обновить и сравнить"', async () => {
    vi.mocked(getRows)
      .mockResolvedValueOnce({ columns: ['id', 'email'], rows: [[1, 'a@b.c']], totalRows: 1 })
      .mockResolvedValueOnce({ columns: ['id', 'email'], rows: [[1, 'z@b.c']], totalRows: 1 });

    render(RowsExplorer, { props: { ...props, keyColumns: ['id'] } });
    await waitFor(() => expect(screen.getByText('a@b.c')).toBeInTheDocument());

    await userEvent.setup().click(screen.getByText('Обновить и сравнить'));

    await waitFor(() => {
      expect(screen.getByText('~1 изменено')).toBeInTheDocument();
      expect(screen.getByText('БД (live)')).toBeInTheDocument();
      const changed = document.querySelectorAll('td.cell-changed');
      expect(changed).toHaveLength(1);
      expect(changed[0]).toHaveTextContent('z@b.c');
      expect(changed[0]).toHaveAttribute('title', 'Было: a@b.c');
    });
  });
});
