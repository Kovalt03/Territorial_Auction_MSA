import { useEffect, useState } from 'react';

import { fetchChatRooms, fetchChatMessages, deleteChatMessage } from '../api/admin';
import { ApiError } from '../api/client';

import type { AdminChatRoom, AdminChatMessage } from '../types/admin';

const PAGE_SIZE = 30;

export function AdminChatPage() {
  const [rooms, setRooms] = useState<AdminChatRoom[]>([]);
  const [roomId, setRoomId] = useState<number | null>(null);
  const [keyword, setKeyword] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [messages, setMessages] = useState<AdminChatMessage[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [page, setPage] = useState(0);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchChatRooms()
      .then(setRooms)
      .catch(e => { setError(e instanceof ApiError ? e.message : '채팅방을 불러올 수 없습니다.'); console.warn('[AdminChat] rooms', e); });
  }, []);

  const load = () => {
    fetchChatMessages({ roomId: roomId ?? undefined, keyword: keyword || undefined, page, size: PAGE_SIZE })
      .then(r => { setMessages(r.messages); setTotalCount(r.totalCount); setError(null); })
      .catch(e => { setError(e instanceof ApiError ? e.message : '메시지를 불러올 수 없습니다.'); console.warn('[AdminChat] messages', e); });
  };
  useEffect(load, [roomId, keyword, page]);

  const handleDelete = async (messageId: number) => {
    if (deletingId != null) return;
    if (!window.confirm('이 메시지를 삭제할까요? 되돌릴 수 없습니다.')) return;
    setDeletingId(messageId);
    try {
      await deleteChatMessage(messageId);
      setMessages(prev => prev.filter(m => m.messageId !== messageId));
      setTotalCount(c => Math.max(0, c - 1));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '삭제에 실패했습니다.');
    } finally {
      setDeletingId(null);
    }
  };

  const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));

  return (
    <div className="h-full overflow-auto p-6">
      <div className="flex items-center gap-2 mb-4">
        <select value={roomId ?? ''} onChange={e => { setRoomId(e.target.value ? Number(e.target.value) : null); setPage(0); }}
          className="h-9 px-2 rounded-md bg-elevated border border-outline text-foreground text-xs outline-none focus:border-primary">
          <option value="">전체 채팅방</option>
          {rooms.map(r => <option key={r.roomId} value={r.roomId}>{r.label}</option>)}
        </select>
        <input value={keywordInput} onChange={e => setKeywordInput(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') { setKeyword(keywordInput.trim()); setPage(0); } }}
          placeholder="내용 검색"
          className="bg-elevated border border-outline rounded-md px-3 h-9 text-foreground text-xs outline-none focus:border-primary w-56" />
        <button onClick={() => { setKeyword(keywordInput.trim()); setPage(0); }}
          className="h-9 px-4 rounded-md bg-primary text-surface text-xs font-bold hover:brightness-110">검색</button>
        <span className="ml-auto text-[11px] text-muted">총 {totalCount}건</span>
      </div>

      {error && <p className="text-danger text-xs mb-3">⚠ {error}</p>}

      <table className="w-full text-xs">
        <thead className="text-dim text-[11px] border-b border-outline">
          <tr>
            <th className="text-left font-medium py-2 px-2 w-36">시각</th>
            <th className="text-left font-medium py-2 px-2 w-28">채팅방</th>
            <th className="text-left font-medium py-2 px-2 w-28">발신자</th>
            <th className="text-left font-medium py-2 px-2">내용</th>
            <th className="text-right font-medium py-2 px-2 w-16"></th>
          </tr>
        </thead>
        <tbody>
          {messages.map(m => (
            <tr key={m.messageId} className="border-b border-outline-soft hover:bg-panel align-top">
              <td className="py-2 px-2 text-muted">{m.sentAt.slice(0, 16).replace('T', ' ')}</td>
              <td className="py-2 px-2 text-muted">{m.roomLabel}</td>
              <td className="py-2 px-2">{m.senderNickname} <span className="text-dim">#{m.senderId}</span></td>
              <td className="py-2 px-2 break-all">{m.content}</td>
              <td className="py-2 px-2 text-right">
                <button onClick={() => void handleDelete(m.messageId)} disabled={deletingId === m.messageId}
                  className="text-danger hover:brightness-125 font-bold disabled:opacity-40">삭제</button>
              </td>
            </tr>
          ))}
          {messages.length === 0 && (
            <tr><td colSpan={5} className="py-8 text-center text-muted">메시지가 없습니다.</td></tr>
          )}
        </tbody>
      </table>

      <div className="flex items-center justify-center gap-3 mt-4 text-xs">
        <button disabled={page <= 0} onClick={() => setPage(p => p - 1)}
          className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">이전</button>
        <span className="text-dim">{page + 1} / {totalPages}</span>
        <button disabled={page + 1 >= totalPages} onClick={() => setPage(p => p + 1)}
          className="px-3 h-8 rounded-md border border-outline text-muted disabled:opacity-30 hover:text-foreground-soft">다음</button>
      </div>
    </div>
  );
}
