import { Client, type IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { getAuthToken } from "@/lib/auth";

const WS_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

let client: Client | null = null;

export function connectWebSocket(onConnect?: () => void): Client {
  if (client?.connected) {
    onConnect?.();
    return client;
  }

  const token = getAuthToken();

  client = new Client({
    webSocketFactory: () => new SockJS(`${WS_URL}/ws`),
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
    reconnectDelay: 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      onConnect?.();
    },
  });

  client.activate();
  return client;
}

export function disconnectWebSocket() {
  if (client) {
    client.deactivate();
    client = null;
  }
}

export function subscribe<T>(
  destination: string,
  callback: (data: T) => void
): { unsubscribe: () => void } | null {
  if (!client?.connected) return null;

  const subscription = client.subscribe(destination, (message: IMessage) => {
    try {
      const data = JSON.parse(message.body) as T;
      callback(data);
    } catch (e) {
      console.error("Failed to parse WebSocket message:", e, message.body);
    }
  });

  return { unsubscribe: () => subscription.unsubscribe() };
}

export function subscribeToGuildStats(guildId: string, callback: (data: unknown) => void) {
  return subscribe(`/topic/guild.${guildId}.stats`, callback);
}

export function subscribeToGuildModeration(guildId: string, callback: (data: unknown) => void) {
  return subscribe(`/topic/guild.${guildId}.moderation`, callback);
}

export function subscribeToGuildAudio(guildId: string, callback: (data: unknown) => void) {
  return subscribe(`/topic/guild.${guildId}.audio`, callback);
}
