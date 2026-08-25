import { ButtonHTMLAttributes, ReactNode } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg' | 'xl';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  fullWidth?: boolean;
  children: ReactNode;
}

const VARIANT: Record<ButtonVariant, string> = {
  primary:   'bg-primary text-surface font-bold hover:brightness-110 active:scale-[0.98] disabled:opacity-60',
  secondary: 'bg-elevated border border-outline text-foreground hover:bg-outline transition-colors',
  ghost:     'border border-primary text-primary hover:bg-primary/10 transition-colors',
  danger:    'bg-danger/10 border border-danger/40 text-danger hover:bg-danger/20 transition-colors',
};

const SIZE: Record<ButtonSize, string> = {
  sm: 'h-8  px-3 text-xs    rounded-lg',
  md: 'h-11 px-4 text-sm    rounded-xl',
  lg: 'h-12 px-6 text-base  rounded-xl',
  xl: 'h-14 px-8 text-[17px] rounded-xl',
};

export function Button({
  variant = 'primary',
  size = 'md',
  fullWidth,
  className = '',
  children,
  ...props
}: Props) {
  return (
    <button
      className={[
        'inline-flex items-center justify-center font-semibold transition-all',
        VARIANT[variant],
        SIZE[size],
        fullWidth ? 'w-full' : '',
        className,
      ].filter(Boolean).join(' ')}
      {...props}
    >
      {children}
    </button>
  );
}
