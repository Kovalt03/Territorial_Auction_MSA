import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { useFetch } from '../useFetch';

describe('useFetch', () => {
  it('성공 시 data 반환, isLoading false', async () => {
    const fetchFn = vi.fn().mockResolvedValue({ items: [1, 2, 3] });

    const { result } = renderHook(() => useFetch(fetchFn));

    expect(result.current.isLoading).toBe(true);

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toEqual({ items: [1, 2, 3] });
    expect(result.current.error).toBeNull();
  });

  it('실패 시 기본 에러 메시지 설정', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('network error'));

    const { result } = renderHook(() => useFetch(fetchFn));

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.data).toBeNull();
    expect(result.current.error).toBe('데이터를 불러올 수 없습니다.');
  });

  it('실패 시 커스텀 에러 메시지 사용', async () => {
    const fetchFn = vi.fn().mockRejectedValue(new Error('network error'));

    const { result } = renderHook(() =>
      useFetch(fetchFn, '랭킹 데이터를 불러올 수 없습니다.'),
    );

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.error).toBe('랭킹 데이터를 불러올 수 없습니다.');
  });

  it('fetchFn은 마운트 시 딱 한 번만 호출', async () => {
    const fetchFn = vi.fn().mockResolvedValue(null);

    const { result } = renderHook(() => useFetch(fetchFn));

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(fetchFn).toHaveBeenCalledTimes(1);
  });
});
