import type { ContinentDef } from '../data/continents';

export interface Particle {
  x: number;
  y: number;
  size: number;
  phase: number;
  angle: number;
  orbitR: number;
  speed: number;
}

function hexToRgba(hex: string, alpha: number): string {
  const r = parseInt(hex.slice(1, 3), 16);
  const g = parseInt(hex.slice(3, 5), 16);
  const b = parseInt(hex.slice(5, 7), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}

// ── 배경 (우주) ───────────────────────────────────────────────────────────

function drawNebula(ctx: CanvasRenderingContext2D, w: number, h: number): void {
  const clouds = [
    { x: w * 0.12, y: h * 0.22, r: w * 0.3,  rgb: [80, 0, 160] },
    { x: w * 0.83, y: h * 0.65, r: w * 0.35, rgb: [0, 50, 160] },
    { x: w * 0.5,  y: h * 0.9,  r: w * 0.22, rgb: [160, 0, 80] },
    { x: w * 0.68, y: h * 0.12, r: w * 0.2,  rgb: [0, 110, 130] },
    { x: w * 0.3,  y: h * 0.75, r: w * 0.18, rgb: [60, 0, 120] },
  ];
  for (const n of clouds) {
    const g = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.r);
    g.addColorStop(0, `rgba(${n.rgb[0]},${n.rgb[1]},${n.rgb[2]},0.09)`);
    g.addColorStop(0.5, `rgba(${n.rgb[0]},${n.rgb[1]},${n.rgb[2]},0.03)`);
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, w, h);
  }
}

function drawStars(ctx: CanvasRenderingContext2D, t: number, w: number, h: number): void {
  for (let i = 0; i < 280; i++) {
    const seed = i * 137.508;
    const x = ((seed * 57.3) % w + w) % w;
    const y = ((seed * 83.1) % h + h) % h;
    const size = ((seed * 0.13) % 1.6) + 0.15;
    const twinkle = (Math.sin(seed * 0.3 + t * 0.0008) + 1) * 0.5;
    const brightness = ((seed * 0.07) % 0.55) + 0.45;
    const blueish = (seed * 31) % 1;
    const r = Math.round(200 + blueish * 55);
    const gb = Math.round(215 + blueish * 40);
    ctx.beginPath();
    ctx.arc(x, y, size, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(${r},${gb},255,${(brightness * 0.65 + twinkle * 0.35).toFixed(2)})`;
    ctx.fill();
  }
  for (let i = 0; i < 12; i++) {
    const seed = i * 273.9 + 1000;
    const x = ((seed * 43.7) % w + w) % w;
    const y = ((seed * 91.3) % h + h) % h;
    const glow = ctx.createRadialGradient(x, y, 0, x, y, 3.5);
    glow.addColorStop(0, 'rgba(220,240,255,0.9)');
    glow.addColorStop(1, 'rgba(180,210,255,0)');
    ctx.fillStyle = glow;
    ctx.fillRect(x - 3.5, y - 3.5, 7, 7);
  }
}

function drawSpaceBackground(ctx: CanvasRenderingContext2D, t: number, w: number, h: number): void {
  const g = ctx.createRadialGradient(w * 0.35, h * 0.28, 0, w * 0.5, h * 0.5, Math.max(w, h));
  g.addColorStop(0,   '#0e0a20');
  g.addColorStop(0.4, '#06041a');
  g.addColorStop(1,   '#010108');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, w, h);

  drawNebula(ctx, w, h);
  drawStars(ctx, t, w, h);

  const vg = ctx.createRadialGradient(w / 2, h / 2, Math.min(w, h) * 0.2, w / 2, h / 2, Math.max(w, h) * 0.78);
  vg.addColorStop(0, 'rgba(0,0,0,0)');
  vg.addColorStop(1, 'rgba(0,0,0,0.65)');
  ctx.fillStyle = vg;
  ctx.fillRect(0, 0, w, h);
}

// ── 태양계 중심 ───────────────────────────────────────────────────────────

export const SUN_X = 700;
export const SUN_Y = 550;

function drawOrbitalRings(ctx: CanvasRenderingContext2D, continents: ContinentDef[]): void {
  ctx.save();
  ctx.setLineDash([4, 10]);
  for (const c of continents) {
    ctx.beginPath();
    ctx.ellipse(SUN_X, SUN_Y, c.orbitRx, c.orbitRy, c.orbitRotation, 0, Math.PI * 2);
    ctx.strokeStyle = 'rgba(55, 75, 115, 0.4)';
    ctx.lineWidth = 1;
    ctx.stroke();
  }
  ctx.setLineDash([]);
  ctx.restore();
}

function drawSun(ctx: CanvasRenderingContext2D, t: number): void {
  const pulse = 0.88 + Math.sin(t * 0.0009) * 0.12;

  const outerHalo = ctx.createRadialGradient(SUN_X, SUN_Y, 0, SUN_X, SUN_Y, 160 * pulse);
  outerHalo.addColorStop(0,   'rgba(255, 200, 40, 0.10)');
  outerHalo.addColorStop(0.4, 'rgba(255, 130, 10, 0.04)');
  outerHalo.addColorStop(1,   'rgba(255, 80, 0, 0)');
  ctx.fillStyle = outerHalo;
  ctx.beginPath();
  ctx.arc(SUN_X, SUN_Y, 160 * pulse, 0, Math.PI * 2);
  ctx.fill();

  const halo = ctx.createRadialGradient(SUN_X, SUN_Y, 0, SUN_X, SUN_Y, 100 * pulse);
  halo.addColorStop(0,    'rgba(255, 220, 80, 0.22)');
  halo.addColorStop(0.5,  'rgba(255, 150, 30, 0.09)');
  halo.addColorStop(1,    'rgba(255, 100, 0, 0)');
  ctx.fillStyle = halo;
  ctx.beginPath();
  ctx.arc(SUN_X, SUN_Y, 100 * pulse, 0, Math.PI * 2);
  ctx.fill();

  const core = ctx.createRadialGradient(SUN_X - 14, SUN_Y - 14, 0, SUN_X, SUN_Y, 50);
  core.addColorStop(0,    'rgba(255, 252, 220, 0.98)');
  core.addColorStop(0.30, 'rgba(255, 210, 70, 0.92)');
  core.addColorStop(0.70, 'rgba(245, 140, 25, 0.68)');
  core.addColorStop(1,    'rgba(200, 80, 10, 0)');
  ctx.shadowBlur = 45;
  ctx.shadowColor = '#ffb040';
  ctx.fillStyle = core;
  ctx.beginPath();
  ctx.arc(SUN_X, SUN_Y, 50, 0, Math.PI * 2);
  ctx.fill();
  ctx.shadowBlur = 0;
}

// ── 파티클 (궤도 위성) ────────────────────────────────────────────────────

export function createParticles(c: ContinentDef, count = 10): Particle[] {
  return Array.from({ length: count }, (_, i) => {
    const orbitR = c.halfHeight * (0.75 + Math.random() * 0.55);
    const angle = (i / count) * Math.PI * 2 + Math.random() * 0.4;
    return {
      x: c.cx + Math.cos(angle) * orbitR,
      y: c.cy + Math.sin(angle) * orbitR,
      size: Math.random() * 1.6 + 0.4,
      phase: Math.random() * Math.PI * 2,
      angle,
      orbitR,
      speed: 0.00012 + Math.random() * 0.00008,
    };
  });
}

export function updateParticles(particles: Particle[], c: ContinentDef, dt: number): void {
  for (const p of particles) {
    p.angle += p.speed * dt;
    p.x = c.cx + Math.cos(p.angle) * p.orbitR;
    p.y = c.cy + Math.sin(p.angle) * p.orbitR;
    p.phase += dt * 0.0014;
  }
}

// ── 행성 고리 (S등급 전용) ────────────────────────────────────────────────

function drawRing(ctx: CanvasRenderingContext2D, c: ContinentDef, isHovered: boolean): void {
  ctx.save();
  ctx.translate(c.cx, c.cy);
  const rx = c.halfHeight * 1.72;
  const ry = c.halfHeight * 0.28;

  ctx.beginPath();
  ctx.ellipse(0, 0, rx, ry, 0, 0, Math.PI * 2);
  ctx.strokeStyle = hexToRgba(c.color, isHovered ? 0.6 : 0.38);
  ctx.lineWidth = isHovered ? 5 : 3.5;
  ctx.shadowBlur = isHovered ? 20 : 12;
  ctx.shadowColor = c.color;
  ctx.stroke();

  ctx.beginPath();
  ctx.ellipse(0, 0, rx * 0.76, ry * 0.76, 0, 0, Math.PI * 2);
  ctx.strokeStyle = hexToRgba(c.color, isHovered ? 0.32 : 0.18);
  ctx.lineWidth = isHovered ? 2 : 1.5;
  ctx.shadowBlur = 0;
  ctx.stroke();
  ctx.restore();
}

// ── 대륙 (행성) ───────────────────────────────────────────────────────────

function drawContinent(
  ctx: CanvasRenderingContext2D,
  c: ContinentDef,
  isHovered: boolean,
  particles: Particle[],
): void {
  // Path2D constructed per-frame using current position
  const path = new Path2D();
  path.arc(c.cx, c.cy, c.halfHeight, 0, Math.PI * 2);

  ctx.save();

  if (isHovered) {
    ctx.translate(c.cx, c.cy);
    ctx.scale(1.06, 1.06);
    ctx.translate(-c.cx, -c.cy);
  }

  if (c.grade === 'S') {
    ctx.save();
    ctx.beginPath();
    ctx.rect(c.cx - 400, c.cy - 400, 800, 400);
    ctx.clip();
    drawRing(ctx, c, isHovered);
    ctx.restore();
  }

  ctx.shadowBlur = isHovered ? 65 : 38;
  ctx.shadowColor = c.color;
  ctx.fillStyle = hexToRgba(c.color, 0.06);
  ctx.fill(path);
  ctx.shadowBlur = 0;

  ctx.fillStyle = '#03060f';
  ctx.fill(path);

  const lx = c.cx - c.halfHeight * 0.38;
  const ly = c.cy - c.halfHeight * 0.44;
  const sphere = ctx.createRadialGradient(lx, ly, 0, c.cx, c.cy, c.halfHeight * 1.35);
  sphere.addColorStop(0,    hexToRgba(c.color, isHovered ? 0.65 : 0.50));
  sphere.addColorStop(0.35, hexToRgba(c.color, isHovered ? 0.28 : 0.20));
  sphere.addColorStop(0.68, hexToRgba(c.color, 0.08));
  sphere.addColorStop(1,    'rgba(0,0,0,0.55)');
  ctx.fillStyle = sphere;
  ctx.fill(path);

  ctx.save();
  ctx.clip(path);

  for (let b = 0; b < 7; b++) {
    const fy = c.cy - c.halfHeight + ((b + 0.5) / 7) * c.halfHeight * 2;
    const alpha = Math.sin((b / 7) * Math.PI) * 0.055;
    ctx.beginPath();
    ctx.moveTo(c.cx - 260, fy);
    ctx.lineTo(c.cx + 260, fy);
    ctx.strokeStyle = hexToRgba(c.color, alpha);
    ctx.lineWidth = 4;
    ctx.stroke();
  }

  const sx = c.cx - c.halfHeight * 0.27;
  const sy = c.cy - c.halfHeight * 0.32;
  const spec = ctx.createRadialGradient(sx, sy, 0, sx, sy, c.halfHeight * 0.36);
  spec.addColorStop(0,    'rgba(255,255,255,0.32)');
  spec.addColorStop(0.55, 'rgba(255,255,255,0.07)');
  spec.addColorStop(1,    'rgba(255,255,255,0)');
  ctx.fillStyle = spec;
  ctx.fillRect(c.cx - 300, c.cy - 300, 600, 600);
  ctx.restore();

  ctx.setLineDash([]);
  ctx.strokeStyle = hexToRgba(c.color, isHovered ? 0.92 : 0.62);
  ctx.lineWidth = isHovered ? 2.4 : 1.7;
  ctx.shadowBlur = isHovered ? 22 : 12;
  ctx.shadowColor = c.color;
  ctx.stroke(path);
  ctx.shadowBlur = 0;

  if (c.grade === 'S') {
    ctx.save();
    ctx.beginPath();
    ctx.rect(c.cx - 400, c.cy, 800, 400);
    ctx.clip();
    drawRing(ctx, c, isHovered);
    ctx.restore();
  }

  for (const p of particles) {
    const alpha = Math.max(0, 0.28 + Math.sin(p.phase) * 0.32);
    const sz = p.size * (isHovered ? 1.35 : 1);
    ctx.beginPath();
    ctx.arc(p.x, p.y, sz, 0, Math.PI * 2);
    ctx.fillStyle = hexToRgba(c.color, alpha);
    ctx.shadowBlur = 8;
    ctx.shadowColor = c.color;
    ctx.fill();
  }
  ctx.shadowBlur = 0;

  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.shadowBlur = isHovered ? 22 : 12;
  ctx.shadowColor = c.color;
  ctx.fillStyle = isHovered ? '#ffffff' : c.color;
  ctx.font = `bold ${isHovered ? 14 : 12}px 'Segoe UI', system-ui, sans-serif`;
  ctx.fillText(c.name, c.cx, c.cy - 10);

  ctx.shadowBlur = 0;
  ctx.fillStyle = hexToRgba(c.color, 0.72);
  ctx.font = `9px 'Segoe UI', system-ui, sans-serif`;
  ctx.fillText(
    c.trophyReq != null ? `🏆 ${c.trophyReq.toLocaleString()}+` : '✦ 자유',
    c.cx, c.cy + 6,
  );

  if (isHovered) {
    ctx.fillStyle = hexToRgba(c.color, 0.88);
    ctx.font = `10px 'Segoe UI', system-ui, sans-serif`;
    ctx.fillText(c.desc, c.cx, c.cy + 20);
    ctx.fillStyle = '#ffd700';
    ctx.shadowBlur = 8;
    ctx.shadowColor = '#ffd700';
    ctx.font = `bold 10px 'Segoe UI', system-ui, sans-serif`;
    ctx.fillText(`[${c.grade}]`, c.cx, c.cy + 34);
    ctx.shadowBlur = 0;
  }

  ctx.restore();
}

// ── 프레임 진입점 ─────────────────────────────────────────────────────────

export function drawFrame(
  ctx: CanvasRenderingContext2D,
  zoom: number,
  pan: { x: number; y: number },
  t: number,
  hoveredId: string | null,
  particleMap: Map<string, Particle[]>,
  continents: ContinentDef[],
  canvasWidth: number,
  canvasHeight: number,
): void {
  ctx.save();
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  drawSpaceBackground(ctx, t, canvasWidth, canvasHeight);
  ctx.restore();

  ctx.save();
  ctx.setTransform(zoom, 0, 0, zoom, pan.x, pan.y);
  drawOrbitalRings(ctx, continents);
  drawSun(ctx, t);
  for (const c of continents) {
    drawContinent(
      ctx, c, hoveredId === c.id,
      particleMap.get(c.slotId) ?? [],
    );
  }
  ctx.restore();
}
