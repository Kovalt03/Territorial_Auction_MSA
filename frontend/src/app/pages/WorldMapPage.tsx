import { useState } from 'react';

import { useApp } from '../context/AppContext';
import { useMyGuild } from '../hooks/useMyGuild';
import { GNB } from '../components/GNB';
import { MapCanvas } from '../components/MapCanvas';
import { ChatPanel } from '../components/ChatPanel';

type ChatTab = 'world' | 'guild';

export function WorldMapPage() {
  const [showChat, setShowChat] = useState(false);
  const [chatTab, setChatTab] = useState<ChatTab>('world');
  const { isLoggedIn } = useApp();
  const { myGuild } = useMyGuild(isLoggedIn);

  const roomId = chatTab === 'world'
    ? 'room_world'
    : myGuild != null ? `room_guild_${myGuild.guildId}` : null;

  return (
    <div className="flex flex-col h-screen bg-surface overflow-hidden">
      <GNB />

      <div className="flex flex-1 overflow-hidden">
        {/* Map canvas — takes up all remaining space */}
        <div
          className="flex-1 relative overflow-hidden"
          style={{ background: 'radial-gradient(ellipse at 50% 40%, #0c1428 0%, #040810 100%)' }}
        >
          <MapCanvas />

          {/* Chat FAB */}
          <button
            onClick={() => setShowChat(v => !v)}
            className="absolute bottom-4 right-4 z-20 w-12 h-12 bg-primary rounded-full flex items-center justify-center text-xl hover:brightness-110 transition-all"
            style={{ boxShadow: '0 0 20px #00f5ff40' }}
          >
            💬
          </button>
        </div>

        {/* Chat slide-in panel */}
        {showChat && (
          <div className="flex-shrink-0 w-[280px] bg-surface border-l border-outline-soft flex flex-col">
            {/* Header */}
            <div className="flex-shrink-0 flex items-center justify-between px-4 py-3 border-b border-outline-soft">
              <span className="text-foreground-soft font-semibold text-sm">💬 채팅</span>
              <button onClick={() => setShowChat(false)} className="text-muted hover:text-foreground-soft transition-colors">✕</button>
            </div>

            {/* Tabs */}
            <div className="flex-shrink-0 flex border-b border-outline-soft">
              {([['world', '🌍 전체'], ['guild', '🏰 길드']] as [ChatTab, string][]).map(([tab, label]) => (
                <button
                  key={tab}
                  onClick={() => { if (tab === 'guild' && myGuild == null) return; setChatTab(tab); }}
                  disabled={tab === 'guild' && myGuild == null}
                  className={`flex-1 py-2 text-[11px] transition-colors disabled:opacity-40 border-b-2 ${chatTab === tab ? 'text-primary border-primary' : 'text-muted border-transparent'}`}
                >
                  {label}
                </button>
              ))}
            </div>

            {/* Chat content */}
            {roomId != null ? (
              <ChatPanel roomId={roomId} />
            ) : (
              <div className="flex-1 flex items-center justify-center p-4">
                <p className="text-muted text-[11px] text-center">길드에 가입하면<br />길드 채팅을 이용할 수 있습니다.</p>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
