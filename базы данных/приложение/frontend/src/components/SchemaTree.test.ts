import { describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import SchemaTree from './SchemaTree.svelte';
import type { Catalog } from '../lib/types';

const catalog: Catalog = {
  instanceId: 10,
  schemas: [
    {
      id: 1,
      instanceId: 10,
      schemaName: 'public',
      owner: 'postgres',
      comment: null,
      tables: [
        {
          id: 11,
          schemaId: 1,
          tableName: 'users',
          kind: 'TABLE',
          columns: [
            {
              id: 111,
              tableId: 11,
              columnName: 'id',
              ordinalPosition: 1,
              dataType: 'bigint',
              nullable: false,
              defaultValue: null,
              generated: false,
            },
            {
              id: 112,
              tableId: 11,
              columnName: 'email',
              ordinalPosition: 2,
              dataType: 'text',
              nullable: true,
              defaultValue: null,
              generated: false,
            },
          ],
          constraints: [
            {
              id: 121,
              tableId: 11,
              constraintName: 'users_pkey',
              type: 'PRIMARY_KEY',
              expression: null,
              refTable: null,
              onDelete: null,
              onUpdate: null,
              columns: [{ columnName: 'id', position: 1, refColumnName: null }],
            },
          ],
          indexes: [],
        },
      ],
    },
    {
      id: 2,
      instanceId: 10,
      schemaName: 'audit',
      owner: 'postgres',
      comment: null,
      tables: [],
    },
  ],
};

describe('SchemaTree', () => {
  it('shows the instance label and schema count', () => {
    render(SchemaTree, { props: { catalog, instanceLabel: 'prod' } });
    expect(screen.getByText(/prod/)).toBeInTheDocument();
    expect(screen.getByText('схем: 2')).toBeInTheDocument();
  });

  it('shows not loaded state when no catalog', () => {
    render(SchemaTree, { props: { catalog: null, instanceLabel: 'empty' } });
    expect(screen.getByText('не загружено')).toBeInTheDocument();
  });

  it('expands schemas and tables on click', async () => {
    const user = userEvent.setup();
    render(SchemaTree, { props: { catalog, instanceLabel: 'prod' } });

    await user.click(screen.getByText(/public/));
    await user.click(screen.getByText(/users/));

    await waitFor(() => {
      expect(screen.getByText(/Колонки/)).toBeInTheDocument();
      expect(screen.getByText(/id · 1 · bigint/)).toBeInTheDocument();
      expect(screen.getByText(/users_pkey/)).toBeInTheDocument();
      expect(screen.getByText(/email/)).toBeInTheDocument();
    });
  });

  it('reports the selected table', async () => {
    const user = userEvent.setup();
    const onSelectTable = vi.fn();
    render(SchemaTree, { props: { catalog, instanceLabel: 'prod', onSelectTable } });

    await user.click(screen.getByText(/public/));
    await user.click(screen.getByText(/users/));

    expect(onSelectTable).toHaveBeenCalledWith({
      instanceId: 10,
      schemaName: 'public',
      tableName: 'users',
    });
  });
});
