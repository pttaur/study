import { afterEach, describe, expect, it, vi } from 'vitest';
import { render, waitFor } from '@testing-library/svelte';
import App from './App.svelte';
import { catalogEvents, listInstances, loadSchema } from './lib/api';

vi.mock('./lib/api', () => ({
  listInstances: vi.fn(),
  createInstance: vi.fn(),
  loadSchema: vi.fn(),
  checkChanges: vi.fn(),
  catalogEvents: vi.fn(),
  getRows: vi.fn(),
  runJoin: vi.fn(),
  joinHistory: vi.fn(),
}));

class FakeEventSource {
  close = vi.fn();
  onmessage: ((ev: MessageEvent<string>) => void) | null = null;
  onerror: (() => void) | null = null;
}

describe('App', () => {
  afterEach(() => vi.clearAllMocks());

  it('closes catalog event subscriptions on destroy', async () => {
    vi.mocked(listInstances).mockResolvedValue([
      {
        id: 1,
        label: 'demo',
        connectionUrl: 'jdbc:postgresql://db:5432/demo',
        username: 'u',
        kind: 'POSTGRES',
      },
    ]);
    vi.mocked(loadSchema).mockResolvedValue({ instanceId: 1, schemas: [] });
    const source = new FakeEventSource();
    vi.mocked(catalogEvents).mockReturnValue(source as unknown as EventSource);

    const { unmount } = render(App);
    await waitFor(() => expect(catalogEvents).toHaveBeenCalledWith(1));

    unmount();

    expect(source.close).toHaveBeenCalled();
  });
});
