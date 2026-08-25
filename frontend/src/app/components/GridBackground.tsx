export function GridBackground() {
  const STEP = 30;
  const vLines = Array.from({ length: 51 }, (_, i) => i * STEP);
  const hLines = Array.from({ length: 34 }, (_, i) => i * STEP);

  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      <svg
        className="w-full h-full opacity-[0.06]"
        xmlns="http://www.w3.org/2000/svg"
      >
        {vLines.map(x => (
          <line key={`v-${x}`} x1={`${x}px`} y1="0" x2={`${x}px`} y2="100%" stroke="#00f5ff" strokeWidth="1" />
        ))}
        {hLines.map(y => (
          <line key={`h-${y}`} x1="0" y1={`${y}px`} x2="100%" y2={`${y}px`} stroke="#00f5ff" strokeWidth="1" />
        ))}
      </svg>
    </div>
  );
}
