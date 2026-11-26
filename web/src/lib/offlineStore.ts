'use client';

export type OfflineSyncStatus = 'PENDING' | 'RETRY_SCHEDULED' | 'FAILED' | 'SYNCED';

export interface OfflineSnapshotRecord<T = unknown> {
  key: string;
  data: T;
  cachedAt: string;
}

export interface BrowserSyncQueueItem {
  id: string;
  payload: Record<string, unknown>;
  sync_status: OfflineSyncStatus;
  retry_count: number;
  last_attempt_at: string | null;
  created_at: string;
}

const DB_NAME = 'monzi_offline_db';
const DB_VERSION = 1;
const STORE_SNAPSHOTS = 'snapshots';
const STORE_SYNC_QUEUE = 'sync_queue';

let dbPromise: Promise<IDBDatabase> | null = null;
let activeFlushPromise: Promise<{ synced: number; failed: number }> | null = null;

function isIndexedDbAvailable(): boolean {
  return typeof window !== 'undefined' && typeof window.indexedDB !== 'undefined';
}

function openOfflineDb(): Promise<IDBDatabase> {
  if (!isIndexedDbAvailable()) {
    return Promise.reject(new Error('IndexedDB is unavailable'));
  }
  if (dbPromise) return dbPromise;

  dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
    try {
      const request = window.indexedDB.open(DB_NAME, DB_VERSION);

      request.onupgradeneeded = () => {
        const db = request.result;
        if (!db.objectStoreNames.contains(STORE_SNAPSHOTS)) {
          db.createObjectStore(STORE_SNAPSHOTS, { keyPath: 'key' });
        }
        if (!db.objectStoreNames.contains(STORE_SYNC_QUEUE)) {
          const queueStore = db.createObjectStore(STORE_SYNC_QUEUE, { keyPath: 'id' });
          queueStore.createIndex('sync_status', 'sync_status', { unique: false });
          queueStore.createIndex('created_at', 'created_at', { unique: false });
        }
      };

      request.onsuccess = () => {
        const db = request.result;
        db.onversionchange = () => {
          db.close();
          dbPromise = null;
        };
        resolve(db);
      };

      request.onerror = () => {
        dbPromise = null;
        reject(request.error || new Error('Failed to open IndexedDB'));
      };
    } catch (err) {
      dbPromise = null;
      reject(err);
    }
  });

  return dbPromise;
}

function generateLocalId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `sync_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`;
}

export async function saveOfflineSnapshot<T>(key: string, data: T): Promise<void> {
  if (!isIndexedDbAvailable() || !key) return;
  try {
    const db = await openOfflineDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE_SNAPSHOTS, 'readwrite');
      const store = tx.objectStore(STORE_SNAPSHOTS);
      const record: OfflineSnapshotRecord<T> = {
        key,
        data,
        cachedAt: new Date().toISOString(),
      };
      store.put(record);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } catch (err) {
    console.warn('[offlineStore] saveOfflineSnapshot failed:', err);
  }
}

export async function loadOfflineSnapshot<T>(
  key: string
): Promise<{ data: T; cachedAt: string } | null> {
  if (!isIndexedDbAvailable() || !key) return null;
  try {
    const db = await openOfflineDb();
    return await new Promise<{ data: T; cachedAt: string } | null>((resolve) => {
      const tx = db.transaction(STORE_SNAPSHOTS, 'readonly');
      const store = tx.objectStore(STORE_SNAPSHOTS);
      const req = store.get(key);
      req.onsuccess = () => {
        const record = req.result as OfflineSnapshotRecord<T> | undefined;
        if (record && record.data !== undefined) {
          resolve({ data: record.data, cachedAt: record.cachedAt });
        } else {
          resolve(null);
        }
      };
      req.onerror = () => resolve(null);
    });
  } catch {
    return null;
  }
}

export async function enqueueBrowserSync(payload: Record<string, unknown>): Promise<string> {
  const id = generateLocalId();
  if (!isIndexedDbAvailable()) return id;

  try {
    const db = await openOfflineDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(STORE_SYNC_QUEUE, 'readwrite');
      const store = tx.objectStore(STORE_SYNC_QUEUE);
      const item: BrowserSyncQueueItem = {
        id,
        payload,
        sync_status: 'PENDING',
        retry_count: 0,
        last_attempt_at: null,
        created_at: new Date().toISOString(),
      };
      store.put(item);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } catch (err) {
    console.warn('[offlineStore] enqueueBrowserSync failed:', err);
  }

  return id;
}

export async function getBrowserSyncQueue(): Promise<BrowserSyncQueueItem[]> {
  if (!isIndexedDbAvailable()) return [];
  try {
    const db = await openOfflineDb();
    return await new Promise<BrowserSyncQueueItem[]>((resolve) => {
      const tx = db.transaction(STORE_SYNC_QUEUE, 'readonly');
      const store = tx.objectStore(STORE_SYNC_QUEUE);
      const req = store.getAll();
      req.onsuccess = () => {
        const items = Array.isArray(req.result) ? (req.result as BrowserSyncQueueItem[]) : [];
        items.sort((a, b) => a.created_at.localeCompare(b.created_at));
        resolve(items);
      };
      req.onerror = () => resolve([]);
    });
  } catch {
    return [];
  }
}

function isPendingStatus(status: OfflineSyncStatus): boolean {
  return status === 'PENDING' || status === 'RETRY_SCHEDULED' || status === 'FAILED';
}

export async function getPendingBrowserSyncCount(): Promise<number> {
  const items = await getBrowserSyncQueue();
  return items.filter((item) => isPendingStatus(item.sync_status)).length;
}

async function putQueueItem(item: BrowserSyncQueueItem): Promise<void> {
  if (!isIndexedDbAvailable()) return;
  const db = await openOfflineDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_SYNC_QUEUE, 'readwrite');
    tx.objectStore(STORE_SYNC_QUEUE).put(item);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

async function deleteQueueItem(id: string): Promise<void> {
  if (!isIndexedDbAvailable()) return;
  const db = await openOfflineDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_SYNC_QUEUE, 'readwrite');
    tx.objectStore(STORE_SYNC_QUEUE).delete(id);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function flushBrowserSyncQueue(): Promise<{ synced: number; failed: number }> {
  if (!isIndexedDbAvailable() || (typeof navigator !== 'undefined' && !navigator.onLine)) {
    return { synced: 0, failed: 0 };
  }

  if (activeFlushPromise) {
    return activeFlushPromise;
  }

  activeFlushPromise = (async () => {
    let synced = 0;
    let failed = 0;

    try {
      const queue = await getBrowserSyncQueue();
      const pendingItems = queue.filter((item) => isPendingStatus(item.sync_status));

      for (const item of pendingItems) {
        const nowIso = new Date().toISOString();
        try {
          const res = await fetch('/api/sync', {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(item.payload),
          });

          if (res.ok) {
            await deleteQueueItem(item.id);
            synced += 1;
            continue;
          }

          const isClientPayloadError = res.status >= 400 && res.status < 500 && res.status !== 401 && res.status !== 408 && res.status !== 429;
          await putQueueItem({
            ...item,
            sync_status: isClientPayloadError ? 'FAILED' : 'RETRY_SCHEDULED',
            retry_count: item.retry_count + 1,
            last_attempt_at: nowIso,
          });
          failed += 1;
        } catch {
          await putQueueItem({
            ...item,
            sync_status: 'RETRY_SCHEDULED',
            retry_count: item.retry_count + 1,
            last_attempt_at: nowIso,
          });
          failed += 1;
        }
      }
    } finally {
      activeFlushPromise = null;
    }

    return { synced, failed };
  })();

  return activeFlushPromise;
}
