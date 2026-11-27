import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/svelte';
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
});
