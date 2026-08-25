interface Props {
  message?: string;
  className?: string;
}

export function LoadingState({ message = '불러오는 중...', className = 'py-8' }: Props) {
  return (
    <div className={`flex flex-col items-center justify-center gap-3 text-center ${className}`}>
      <span className="w-6 h-6 rounded-full border-2 border-outline border-t-primary animate-spin" />
      <p className="text-muted text-sm">{message}</p>
    </div>
  );
}
