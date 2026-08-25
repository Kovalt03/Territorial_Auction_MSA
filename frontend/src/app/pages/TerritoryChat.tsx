import { useRef, useEffect } from 'react';

export interface ChatMsg { user: string; text: string; time: string; mine: boolean; }

interface Props {
  continentName: string;
  messages: ChatMsg[];
  input: string;
  onChangeInput: (v: string) => void;
  onSend: () => void;
}

export function TerritoryChat({ continentName, messages, input, onChangeInput, onSend }: Props) {
  const chatEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="bg-panel-deep border border-outline-soft rounded-xl overflow-hidden flex flex-col flex-1 min-h-0">
      <div className="flex items-center gap-2 px-4 py-2.5 bg-panel-deep border-b border-outline-soft flex-shrink-0">
        <span className="text-sm">💬</span>
        <span className="text-foreground font-semibold text-[13px]">{continentName} 채팅</span>
        <div className="flex items-center gap-1 ml-2">
          <div className="w-1.5 h-1.5 bg-gp rounded-full animate-pulse" />
          <span className="text-muted text-[10px]">실시간</span>
        </div>
      </div>
      <div className="flex-1 overflow-y-auto px-4 py-2 space-y-2">
        {messages.map((msg, i) => (
          <div key={i} className={`flex items-start gap-2 ${msg.mine ? 'flex-row-reverse' : ''}`}>
            {!msg.mine && (
              <div className="w-6 h-6 rounded-full flex items-center justify-center flex-shrink-0 font-bold text-[10px] bg-elevated text-primary">
                {msg.user[0]}
              </div>
            )}
            <div className={`max-w-[70%] flex flex-col gap-0.5 ${msg.mine ? 'items-end' : 'items-start'}`}>
              {!msg.mine && (
                <span className="text-muted text-[10px]">{msg.user}</span>
              )}
              <div
                className={`px-3 py-1.5 rounded-xl text-xs text-foreground border ${msg.mine ? 'bg-primary/20 border-primary/40' : 'bg-outline-soft border-outline'}`}
                style={{
                  borderBottomRightRadius: msg.mine ? 4 : undefined,
                  borderBottomLeftRadius: !msg.mine ? 4 : undefined,
                }}
              >
                {msg.text}
              </div>
              <span className="text-muted text-[9px]">{msg.time}</span>
            </div>
          </div>
        ))}
        <div ref={chatEndRef} />
      </div>
      <div className="flex items-center gap-2 px-3 py-2 border-t border-outline-soft flex-shrink-0">
        <input
          value={input}
          onChange={e => onChangeInput(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && onSend()}
          placeholder={`${continentName} 채팅 입력...`}
          className="flex-1 h-8 bg-outline-soft border border-outline rounded-lg px-3 text-xs text-foreground outline-none focus:border-primary transition-colors"
        />
        <button
          onClick={onSend}
          disabled={!input.trim()}
          className="h-8 px-3 rounded-lg text-xs font-semibold transition-all hover:brightness-110 disabled:opacity-40 bg-primary text-surface"
        >
          전송
        </button>
      </div>
    </div>
  );
}
