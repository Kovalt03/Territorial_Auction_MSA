import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { EmptyState } from '../EmptyState';

describe('EmptyState', () => {
  it('message를 렌더링', () => {
    render(<EmptyState message="보유한 영토가 없습니다." />);
    expect(screen.getByText('보유한 영토가 없습니다.')).toBeInTheDocument();
  });

  it('emoji prop이 있을 때만 렌더링', () => {
    const { rerender } = render(<EmptyState message="없음" />);
    expect(screen.queryByText('📭')).not.toBeInTheDocument();

    rerender(<EmptyState message="없음" emoji="📭" />);
    expect(screen.getByText('📭')).toBeInTheDocument();
  });

  it('subMessage prop이 있을 때만 렌더링', () => {
    const { rerender } = render(<EmptyState message="없음" />);
    expect(screen.queryByText('도움말')).not.toBeInTheDocument();

    rerender(<EmptyState message="없음" subMessage="도움말" />);
    expect(screen.getByText('도움말')).toBeInTheDocument();
  });

  it('className 기본값은 py-8', () => {
    const { container } = render(<EmptyState message="없음" />);
    expect(container.firstChild as HTMLElement).toHaveClass('py-8');
  });

  it('className prop 커스텀 적용', () => {
    const { container } = render(<EmptyState message="없음" className="py-20" />);
    expect(container.firstChild as HTMLElement).toHaveClass('py-20');
  });
});
