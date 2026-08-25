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
