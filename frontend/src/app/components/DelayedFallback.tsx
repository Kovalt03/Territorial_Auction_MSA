import { useState, useEffect } from 'react';

import { LoadingState } from './LoadingState';

interface Props {
  delayMs?: number;
}

export function DelayedFallback({ delayMs = 200 }: Props) {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setIsVisible(true), delayMs);
    return () => clearTimeout(t);
  }, [delayMs]);

  if (!isVisible) return null;

  return (
    <div className="page-root">
      <LoadingState className="flex-1" />
    </div>
  );
}
