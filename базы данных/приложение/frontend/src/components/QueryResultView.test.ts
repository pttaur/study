import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import QueryResultView from './QueryResultView.svelte';

const result = {
  columns: [
    { name: 'id', type: 'int4' },
    { name: 'email', type: 'text' },
  ],
  rows: [[1, 'a@b.c']],
  truncated: false,
  offset: 0,
  totalRows: 1,
  durationMs: 12,
};

const historyEntry = {
  id: 1,
  sql: 'SELECT 1',
  status: 'OK',
  rowCount: 1,
  durationMs: 5,
  error: null,
  createdAt: '2026-01-01T00:00:00Z',
};

describe('QueryResultView', () => {
  it('renders columns and rows', () => {
    render(QueryResultView, { props: { result, history: [] } });

    expect(screen.getByText('id')).toBeInTheDocument();
    expect(screen.getByText('a@b.c')).toBeInTheDocument();
    expect(screen.getByText(/1 строк/)).toBeInTheDocument();
  });

  it('shows a hint before the first run', () => {
    render(QueryResultView, { props: { result: null, history: [] } });

    expect(screen.getByText(/Соберите джойн/)).toBeInTheDocument();
  });

  it('shows an error', () => {
    render(QueryResultView, { props: { result: null, history: [], error: 'boom' } });

    expect(screen.getByText(/boom/)).toBeInTheDocument();
  });

  it('loads and shows history when the tab is opened', async () => {
    const onLoadHistory = vi.fn();
    render(QueryResultView, {
      props: { result: null, history: [historyEntry], onLoadHistory },
    });

    await userEvent.setup().click(screen.getByText('История'));

    expect(onLoadHistory).toHaveBeenCalled();
    expect(screen.getByText('SELECT 1')).toBeInTheDocument();
    expect(screen.getByText('OK')).toBeInTheDocument();
  });

  it('shows an empty state when there is no history', async () => {
    render(QueryResultView, { props: { result: null, history: [] } });

    await userEvent.setup().click(screen.getByText('История'));

    expect(screen.getByText('История пуста.')).toBeInTheDocument();
  });
});
