import { ReactNode } from 'react';

export type BadgeVariant = 'success' | 'danger' | 'warning' | 'info' | 'secondary';

interface Props {
  variant: BadgeVariant;
  children: ReactNode;
  className?: string;
}

const VARIANT: Record<BadgeVariant, string> = {
  success:   'bg-gp/10      text-gp',
  danger:    'bg-danger/10  text-danger',
  warning:   'bg-gold/10    text-gold',
  info:      'bg-primary/10 text-primary',
  secondary: 'bg-secondary/10 text-secondary',
};

export function Badge({ variant, children, className = '' }: Props) {
  return (
    <span
      className={[
        'px-2 py-0.5 rounded text-[11px] font-medium',
        VARIANT[variant],
        className,
      ].filter(Boolean).join(' ')}
    >
      {children}
    </span>
  );
}
