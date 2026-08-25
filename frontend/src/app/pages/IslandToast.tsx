interface Props {
  message: string;
  isError: boolean;
  onClose: () => void;
}

export function IslandToast({ message, isError, onClose }: Props) {
  return (
    <div
      className={`fixed top-5 left-1/2 z-[100] flex items-center gap-2 px-4 py-3 rounded-xl shadow-xl text-sm font-semibold border-[1.5px] ${isError ? 'bg-[#1a0a0a] border-danger text-[#ff6666]' : 'bg-[#0a1a0f] border-gp text-gp'}`}
      style={{ transform: 'translateX(-50%)', animation: 'fadeInDown 0.2s ease' }}
    >
      <span>{isError ? '⚠' : '✓'}</span>
      <span>{message}</span>
      <button
        onClick={onClose}
        className="ml-2 opacity-60 hover:opacity-100 text-base leading-none"
      >✕</button>
    </div>
  );
}
