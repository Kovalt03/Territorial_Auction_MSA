import { useState } from 'react';

import { sendUserNotification } from '../api/admin';
import { ApiError } from '../api/client';

interface Props {
  userId: number;
}

export function SendNotificationForm({ userId }: Props) {
  const [message, setMessage] = useState('');
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);

  const handleSend = async () => {
    if (isSending || !message.trim()) return;
    setIsSending(true); setError(null); setResult(null);
    try {
      await sendUserNotification(userId, message.trim());
      setResult('알림을 보냈습니다.');
      setMessage('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '알림 발송에 실패했습니다.');
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div>
      <textarea value={message} onChange={e => setMessage(e.target.value)} maxLength={500} rows={3}
        placeholder="사용자에게 보낼 메시지 (최대 500자)"
        className="w-full bg-elevated border border-outline rounded-md px-2 py-2 text-foreground text-xs outline-none focus:border-primary mb-2 resize-none" />
      {error && <p className="text-danger text-[11px] mb-2">{error}</p>}
      {result && <p className="text-gp text-[11px] mb-2">{result}</p>}
      <button onClick={() => void handleSend()} disabled={isSending || !message.trim()}
        className="w-full h-9 rounded-lg bg-primary text-surface text-xs font-bold hover:brightness-110 disabled:opacity-40">
        {isSending ? '전송 중...' : '알림 보내기'}
      </button>
    </div>
  );
}
