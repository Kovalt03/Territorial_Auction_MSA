import { render } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { HealthBar } from '../HealthBar';

describe('HealthBar', () => {
  it('hp가 maxHp의 50%이면 width 50%로 렌더링', () => {
    const { container } = render(
      <HealthBar hp={50} maxHp={100} color="#00ff88" />,
    );
    const fill = container.querySelector('.h-full') as HTMLElement;
    expect(fill.style.width).toBe('50%');
    expect(fill.style.background).toBe('rgb(0, 255, 136)');
  });

  it('hp가 maxHp를 초과하면 width를 100%로 제한', () => {
    const { container } = render(
      <HealthBar hp={200} maxHp={100} color="#ff3333" />,
    );
    const fill = container.querySelector('.h-full') as HTMLElement;
    expect(fill.style.width).toBe('100%');
  });

  it('maxHp가 0이면 width 0% (나눗셈 방어)', () => {
    const { container } = render(
      <HealthBar hp={0} maxHp={0} color="#ff3333" />,
    );
    const fill = container.querySelector('.h-full') as HTMLElement;
    expect(fill.style.width).toBe('0%');
  });

  it('height prop이 기본값 h-2로 적용', () => {
    const { container } = render(
      <HealthBar hp={1} maxHp={1} color="#fff" />,
    );
    const bar = container.firstChild as HTMLElement;
    expect(bar.className).toContain('h-2');
  });

  it('height prop 커스텀 적용', () => {
    const { container } = render(
      <HealthBar hp={1} maxHp={1} color="#fff" height="h-1.5" />,
    );
    const bar = container.firstChild as HTMLElement;
    expect(bar.className).toContain('h-1.5');
  });

  it('className prop이 래퍼에 추가', () => {
    const { container } = render(
      <HealthBar hp={1} maxHp={1} color="#fff" className="mt-3" />,
    );
    const bar = container.firstChild as HTMLElement;
    expect(bar.className).toContain('mt-3');
  });
});
