import { apiClient } from './client';

export interface ChatHistoryMessage {
  messageId: number;
  roomId: string;
  senderId: number;
  senderNickname: string;
  content: string;
  sentAt: string;
}

export interface ChatHistoryResponse {
  messages: ChatHistoryMessage[];
  hasNext: boolean;
}

export function fetchChatHistory(roomId: string, params?: { before?: number; size?: number }) {
  const q = new URLSearchParams();
  if (params?.before != null) q.set('before', String(params.before));
  if (params?.size != null) q.set('size', String(params.size));
  const qs = q.toString() ? `?${q}` : '';
  return apiClient.get<ChatHistoryResponse>(`/chat/rooms/${roomId}/messages${qs}`);
}

// 채팅 전송은 REST로 한다: social-service가 저장 후 Redis(chat.message)로 발행 →
// realtime-service가 /sub/chat/{roomId}로 브로드캐스트(REST 전송·STOMP 수신 규약).
export function sendChatMessage(roomId: string, content: string) {
  return apiClient.post<ChatHistoryMessage>(`/chat/rooms/${roomId}/messages`, { content });
}
