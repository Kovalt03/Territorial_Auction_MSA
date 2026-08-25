import { useState, useEffect, useCallback } from 'react';

import { fetchMyGuild } from '../api/guild';
import type { MyGuild } from '../api/guild';

export function useMyGuild(enabled: boolean) {
  const [myGuild, setMyGuild] = useState<MyGuild | null>(null);

  const refresh = useCallback(() => {
    if (!enabled) return;
    fetchMyGuild().then(setMyGuild).catch(() => setMyGuild(null));
  }, [enabled]);

  useEffect(() => { refresh(); }, [refresh]);

  return { myGuild, refresh };
}
