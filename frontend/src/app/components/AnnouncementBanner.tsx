import { useEffect, useState } from 'react';

import { fetchAnnouncement } from '../api/admin';

export function AnnouncementBanner() {
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    fetchAnnouncement()
      .then(r => setMessage(r.active && r.message ? r.message : null))
      .catch(e => console.warn('[AnnouncementBanner] fetch failed', e));
  }, []);

  if (!message) return null;

  return (
    <div className="flex items-center gap-2 px-4 py-1.5 bg-primary/15 border-b border-primary/30 text-primary text-xs font-medium flex-shrink-0">
      <span aria-hidden>📢</span>
      <span className="truncate">{message}</span>
    </div>
  );
}
