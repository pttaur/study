<script lang="ts">
  import type { CreateInstanceRequest, Instance, InstanceKind } from '../lib/types';
  import { createInstance } from '../lib/api';
  import { toErrorMessage } from '../lib/errors';

  let { onCreate }: { onCreate: (instance: Instance) => void } = $props();

  let label = $state('');
  let connectionUrl = $state('');
  let username = $state('');
  let password = $state('');
  let kind = $state<InstanceKind>('POSTGRES');
  let error = $state('');
  let busy = $state(false);

  async function submit(): Promise<void> {
    error = '';
    const body: CreateInstanceRequest = {
      label,
      connectionUrl,
      username,
      password,
      kind,
    };
    try {
      busy = true;
      const instance = await createInstance(body);
      onCreate(instance);
      label = '';
      connectionUrl = '';
      username = '';
      password = '';
    } catch (e) {
      error = toErrorMessage(e);
    } finally {
      busy = false;
    }
  }
</script>

<form
  class="panel"
  onsubmit={(e) => {
    e.preventDefault();
    void submit();
  }}
>
  <div class="lg">Новый инстанс</div>

  <div class="grid">
    <input placeholder="Название" bind:value={label} required />
    <input placeholder="jdbc:postgresql://host:5432/db" bind:value={connectionUrl} required />
    <input placeholder="Пользователь" bind:value={username} required />
    <input type="password" placeholder="Пароль" bind:value={password} required />
    <select bind:value={kind}>
      <option value="POSTGRES">PostgreSQL</option>
      <option value="MARIADB">MariaDB</option>
    </select>
  </div>

  {#if error}<p class="error">{error}</p>{/if}

  <button type="submit" disabled={busy}>
    {#if busy}<span class="spinner"></span>{:else}Создать{/if}
  </button>
</form>

<style>
  .grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin: 8px 0;
  }
  button {
    margin-top: 4px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
</style>
