import { SvelteMap, SvelteSet } from 'svelte/reactivity';
import type { Catalog, DiffResult, Instance, TableSelection } from './types';
import { catalogEvents, checkChanges, listInstances, loadSchema } from './api';
import { toErrorMessage } from './errors';

export function selectedTableKey(selection: TableSelection): string {
  return `${selection.instanceId}.${selection.schemaName}.${selection.tableName}`;
}

function parseCatalog(payload: string): Catalog | null {
  try {
    const parsed: unknown = JSON.parse(payload);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      Array.isArray((parsed as Catalog).schemas)
    ) {
      return parsed as Catalog;
    }
  } catch {
    return null;
  }
  return null;
}

export class InstanceStore {
  instances = $state<Instance[]>([]);
  catalogs = new SvelteMap<number, Catalog>();
  diffs = new SvelteMap<number, DiffResult>();
  checking = new SvelteSet<number>();
  error = $state('');
  initialError = $state('');

  #sources = new SvelteMap<number, EventSource>();

  async refresh(): Promise<void> {
    try {
      this.instances = await listInstances();
      await Promise.all(
        this.instances.map(async (instance) => {
          await this.loadSchema(instance.id);
          this.#subscribe(instance.id);
        }),
      );
    } catch (e) {
      this.initialError = toErrorMessage(e);
    }
  }

  async loadSchema(instanceId: number): Promise<void> {
    this.error = '';
    try {
      this.catalogs.set(instanceId, await loadSchema(instanceId));
    } catch (e) {
      this.error = toErrorMessage(e);
    }
  }

  async create(instance: Instance): Promise<void> {
    this.instances = [...this.instances, instance];
    await this.loadSchema(instance.id);
    this.#subscribe(instance.id);
  }

  async check(instanceId: number): Promise<boolean> {
    this.error = '';
    this.checking.add(instanceId);
    try {
      this.diffs.set(instanceId, await checkChanges(instanceId));
      return true;
    } catch (e) {
      this.error = toErrorMessage(e);
      return false;
    } finally {
      this.checking.delete(instanceId);
    }
  }

  dispose(): void {
    for (const source of this.#sources.values()) {
      source.close();
    }
    this.#sources.clear();
  }

  #subscribe(instanceId: number): void {
    if (this.#sources.has(instanceId)) {
      return;
    }
    const source = catalogEvents(instanceId);
    source.onmessage = (ev: MessageEvent<string>) => {
      const catalog = parseCatalog(ev.data);
      if (catalog) {
        this.catalogs.set(instanceId, catalog);
      }
    };
    source.onerror = () => {
      this.error = 'Соединение с сервером событий потеряно';
    };
    this.#sources.set(instanceId, source);
  }
}
