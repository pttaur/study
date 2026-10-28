import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import JoinBuilder from './JoinBuilder.svelte';
import { JoinStore } from '../lib/join.svelte';
import { runJoin } from '../lib/api';
import type { Catalog, Column, Instance } from '../lib/types';

vi.mock('../lib/api', () => ({
  runJoin: vi.fn(),
  explainJoin: vi.fn(),
  joinHistory: vi.fn(),
}));

const instance: Instance = {
  id: 1,
  label: 'ops',
  connectionUrl: 'jdbc:postgresql://db:5432/ops',
  username: 'u',
  kind: 'POSTGRES',
};

function column(id: number, tableId: number, name: string, position: number): Column {
  return {
    id,
    tableId,
    columnName: name,
    ordinalPosition: position,
    dataType: 'bigint',
    nullable: false,
    defaultValue: null,
    generated: false,
  };
}

const catalog: Catalog = {
  instanceId: 1,
  schemas: [
    {
      id: 1,
      instanceId: 1,
      schemaName: 'public',
      owner: null,
      comment: null,
      tables: [
        {
          id: 11,
          schemaId: 1,
          tableName: 'employee',
          kind: 'TABLE',
          columns: [
            column(111, 11, 'id', 1),
            column(112, 11, 'organization_id', 2),
            column(113, 11, 'full_name', 3),
          ],
          constraints: [],
          indexes: [],
        },
        {
          id: 12,
          schemaId: 1,
          tableName: 'organization',
          kind: 'TABLE',
          columns: [column(121, 12, 'id', 1), column(122, 12, 'name', 2)],
          constraints: [],
          indexes: [],
        },
      ],
    },
  ],
};

function comboboxAt(index: number): HTMLSelectElement {
  const element = screen.getAllByRole('combobox')[index];
  if (!element) {
    throw new Error(`No combobox at index ${index}`);
  }
  return element as HTMLSelectElement;
}

async function addTable(user: ReturnType<typeof userEvent.setup>, ref: string): Promise<void> {
  const search = screen.getByPlaceholderText(/Поиск таблицы/);
  await user.clear(search);
  await user.type(search, ref);
  await user.click(screen.getByRole('button', { name: ref }));
}

describe('JoinBuilder', () => {
  it('adds a table from the picker and shows its columns', async () => {
    const user = userEvent.setup();
    const store = new JoinStore();

    render(JoinBuilder, {
      props: { store, instances: [instance], catalogs: new Map([[1, catalog]]) },
    });

    await addTable(user, 'ops.public.employee');

    expect(screen.getAllByText('ops.public.employee').length).toBeGreaterThan(1);
    expect(screen.getByText('full_name')).toBeInTheDocument();
  });

  it('creates a join condition row when a second table is added', async () => {
    const user = userEvent.setup();
    const store = new JoinStore();

    render(JoinBuilder, {
      props: { store, instances: [instance], catalogs: new Map([[1, catalog]]) },
    });

    await addTable(user, 'ops.public.employee');
    await addTable(user, 'ops.public.organization');

    expect(screen.getByText('Условие соединения')).toBeInTheDocument();
    expect(store.tables).toHaveLength(2);
    expect(store.tables[1]?.on).toHaveLength(1);
  });

  it('builds a join request from selected conditions and result fields', async () => {
    const user = userEvent.setup();
    const store = new JoinStore();

    render(JoinBuilder, {
      props: { store, instances: [instance], catalogs: new Map([[1, catalog]]) },
    });

    await addTable(user, 'ops.public.employee');
    await addTable(user, 'ops.public.organization');

    // comboboxes: 0 join type, 1 left column, 2 right table, 3 right column
    await user.selectOptions(comboboxAt(1), 'id');
    await user.selectOptions(comboboxAt(3), 'organization_id');

    await user.click(screen.getByRole('checkbox', { name: 'full_name' }));
    await user.click(screen.getByRole('checkbox', { name: 'name' }));

    await user.click(screen.getByText('▶ Выполнить'));

    const request = vi.mocked(runJoin).mock.calls.at(0)?.[0];
    if (!request) {
      throw new Error('runJoin was not called');
    }
    expect(request.tables).toHaveLength(2);
    expect(request.tables[1]?.on[0]).toEqual({
      leftTable: 'ops.public.organization',
      leftColumn: 'id',
      rightTable: 'ops.public.employee',
      rightColumn: 'organization_id',
    });
    expect(request.select).toEqual(
      expect.arrayContaining([
        { table: 'ops.public.employee', column: 'full_name' },
        { table: 'ops.public.organization', column: 'name' },
      ]),
    );
  });

  it('shows a hint when no tables are selected', () => {
    render(JoinBuilder, {
      props: { store: new JoinStore(), instances: [instance], catalogs: new Map([[1, catalog]]) },
    });

    expect(screen.getByText(/Добавьте таблицы/)).toBeInTheDocument();
  });
});
