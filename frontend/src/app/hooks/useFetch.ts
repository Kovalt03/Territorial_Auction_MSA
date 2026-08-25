import { useState, useEffect } from 'react';

export function useFetch<T>(
  fetchFn: () => Promise<T>,
  errorMsg = '데이터를 불러올 수 없습니다.',
) {
  const [data, setData] = useState<T | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setError(null);
    fetchFn()
      .then(setData)
      .catch(() => setError(errorMsg))
      .finally(() => setIsLoading(false));
  }, [fetchFn, errorMsg]);

  return { data, isLoading, error };
}
