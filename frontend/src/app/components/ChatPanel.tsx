import { useState, useEffect, useRef, useCallback } from 'react';

import { useApp } from '../context/AppContext';
import { fetchChatHistory, type ChatHistoryMessage } from '../api/chat';
import { useStompSubscribe, useStompPublish } from '../hooks/useStompClient';

interface Props {
  roomId: string;
}

export function ChatPanel({ roomId }: Props) {
  const { isLoggedIn } = useApp();
  const [messages, setMessages] = useState<ChatHistoryMessage[]>([]);
  const [input, setInput] = useState('');
  const [hasNext, setHasNext] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const publish = useStompPublish();

  useEffect(() => {
    setMessages([]);
    setHasNext(false);
    fetchChatHistory(roomId, { size: 30 })
      .then(res => {
        setMessages([...res.messages].reverse());
        setHasNext(res.hasNext);
      })
      .catch((e) => console.warn('[ChatPanel] history load failed', e));
  }, [roomId]);

  useStompSubscribe<ChatHistoryMessage>(
    isLoggedIn ? `/sub/chat/${roomId}` : null,
    (msg) => setMessages(prev => [...prev, msg]),
  );

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleLoadMore = useCallback(() => {
    if (messages.length === 0) return;
    const oldest = messages[0].messageId;
    fetchChatHistory(roomId, { before: oldest, size: 30 })
      .then(res => {
        setMessages(prev => [...[...res.messages].reverse(), ...prev]);
        setHasNext(res.hasNext);
      })
      .catch((e) => console.warn('[ChatPanel] paging failed', e));
  }, [roomId, messages]);

  const handleSend = () => {
    if (!input.trim() || !isLoggedIn) return;
    publish(`/pub/chat/${roomId}`, { content: input.trim() });
    setInput('');
  };

  const formatTime = (iso: string) =>
    new Date(iso).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });

  return (
    <>
      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {hasNext && (
          <button
            onClick={handleLoadMore}
            className="w-full text-center text-[10px] text-muted hover:text-foreground-soft py-1 transition-colors"
          >
            이전 메시지 더 보기
          </button>
        )}
        {messages.length === 0 && (
          <p className="text-center text-muted text-[10px] pt-6">아직 메시지가 없습니다.</p>
        )}
        {messages.map(msg => (
          <div key={msg.messageId} className="text-xs">
            <span className="text-primary font-semibold">{msg.senderNickname}</span>
            <span className="text-muted"> {formatTime(msg.sentAt)}</span>
            <p className="text-foreground-soft mt-0.5 break-words">{msg.content}</p>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <div className="flex-shrink-0 p-3 border-t border-outline-soft flex gap-2">
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && !e.nativeEvent.isComposing && handleSend()}
          placeholder={isLoggedIn ? '메시지 입력...' : '로그인 후 이용 가능'}
          disabled={!isLoggedIn}
          className="flex-1 h-8 bg-panel-deep border border-outline-soft rounded-lg px-3 text-foreground-soft outline-none focus:border-primary transition-colors text-xs disabled:opacity-50"
        />
        <button
          onClick={handleSend}
          disabled={!isLoggedIn || !input.trim()}
          className="w-8 h-8 bg-primary rounded-lg text-surface font-bold flex items-center justify-center text-sm disabled:opacity-40"
        >
          →
        </button>
      </div>
    </>
  );
}
