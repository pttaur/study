import { afterEach, describe, expect, it, vi } from 'vitest';
import { getRows, runJoin } from './api';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api request', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('parses a JSON response', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ columns: [], rows: [], totalRows: 0 }));
    vi.stubGlobal('fetch', fetchMock);

    const grid = await getRows(1, 'public', 'users', 50, 0);

    expect(grid.totalRows).toBe(0);
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/instances/1/tables/public/users/rows?limit=50&offset=0',
      expect.anything(),
    );
  });

  it('throws with status and detail on failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(new Response('boom', { status: 500, statusText: 'Server Error' })),
    );

    await expect(
      runJoin({
        tables: [{ ref: 'a.s.t', type: null, on: [] }],
        select: [{ table: 'a.s.t', column: 'id' }],
      }),
    ).rejects.toThrow('HTTP 500: boom');
  });

  it('returns undefined for a 204 response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(getRows(1, 'public', 'users', 50, 0)).resolves.toBeUndefined();
  });
});
