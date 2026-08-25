interface Props {
  hp: number;
  maxHp: number;
  color: string;
  height?: string;
  bg?: string;
  className?: string;
}

export function HealthBar({ hp, maxHp, color, height = 'h-2', bg = 'bg-panel', className }: Props) {
  const pct = maxHp > 0 ? Math.min(100, (hp / maxHp) * 100) : 0;
  return (
    <div className={`${bg} ${height} rounded-full overflow-hidden${className ? ` ${className}` : ''}`}>
      <div className="h-full rounded-full" style={{ width: `${pct}%`, background: color }} />
    </div>
  );
}
