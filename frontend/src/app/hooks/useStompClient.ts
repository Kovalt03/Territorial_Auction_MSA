import { useEffect, useRef, useCallback } from 'react';
import { Client, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

let sharedClient: Client | null = null;
let connectPromise: Promise<void> | null = null;
const SOCKET_URL = import.meta.env.VITE_WS_URL ?? '/ws';

function getOrCreateClient(): Client {
  if (sharedClient) return sharedClient;
  sharedClient = new Client({
    webSocketFactory: () => new SockJS(SOCKET_URL) as WebSocket,
    reconnectDelay: 3000,
    // 매 (재)연결 직전 localStorage에서 최신 토큰을 다시 읽는다.
    // REST(apiClient)가 401 시 refresh로 localStorage 토큰을 갱신하므로,
    // 이 훅으로 재연결 때 갱신분을 반영하지 않으면 만료된 토큰으로 영구 거절된다.
    beforeConnect: () => {
      const token = localStorage.getItem('accessToken');
      sharedClient!.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
  });
  return sharedClient;
}

function ensureConnected(): Promise<void> {
  const client = getOrCreateClient();
  if (client.connected) return Promise.resolve();
  if (connectPromise) return connectPromise;

  connectPromise = new Promise<void>((resolve, reject) => {
    client.onConnect = () => { connectPromise = null; resolve(); };
    client.onDisconnect = () => { connectPromise = null; };
    client.onStompError = (frame) => { connectPromise = null; reject(new Error(frame.headers['message'] ?? 'STOMP error')); };
    client.onWebSocketError = (err) => { connectPromise = null; reject(err); };
    if (!client.active) client.activate();
  });
  return connectPromise;
}

export function useStompSubscribe<T>(
  destination: string | null,
  onMessage: (payload: T) => void,
) {
  const cbRef = useRef(onMessage);
  cbRef.current = onMessage;

  useEffect(() => {
    if (!destination) return;

    let sub: StompSubscription | null = null;
    let mounted = true;

    ensureConnected().then(() => {
      if (!mounted) return;
      const client = getOrCreateClient();
      if (!client.connected) return;
      sub = client.subscribe(destination, (frame) => {
        try {
          cbRef.current(JSON.parse(frame.body) as T);
        } catch {
          // ignore malformed frames
        }
      });
      if (!mounted) { sub.unsubscribe(); sub = null; }
    });

    return () => {
      mounted = false;
      sub?.unsubscribe();
    };
  }, [destination]);
}

export function useStompPublish() {
  return useCallback((destination: string, body: unknown) => {
    ensureConnected()
      .then(() => {
        const client = getOrCreateClient();
        if (client.connected) {
          client.publish({ destination, body: JSON.stringify(body) });
        }
      })
      .catch((err) => console.warn('STOMP publish failed', err));
  }, []);
}

export function subscribeMultiple(destinations: string[], callback: () => void): () => void {
  let subs: StompSubscription[] = [];
  let cancelled = false;

  ensureConnected().then(() => {
    if (cancelled) return;
    const client = getOrCreateClient();
    if (!client.connected) return;
    subs = destinations.map(dest => client.subscribe(dest, callback));
  }).catch((e) => console.warn('[STOMP] subscribeMultiple connect failed', e));

  return () => {
    cancelled = true;
    subs.forEach(s => s.unsubscribe());
  };
}

export function disconnectStomp() {
  sharedClient?.deactivate();
  sharedClient = null;
  connectPromise = null;
}
