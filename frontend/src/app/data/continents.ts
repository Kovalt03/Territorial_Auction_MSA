export interface ContinentDef {
  id: string;            // String(continentId) after API load; slotId before
  slotId: string;        // stable orbit slot key, never changes
  continentId: number;   // 0 = placeholder; real id from API
  name: string;
  desc: string;
  color: string;
  grade: string;
  trophyReq: number | null;
  orbitRx: number;
  orbitRy: number;
  orbitAngle0: number;
  orbitSpeed: number;
  orbitRotation: number;
  halfHeight: number;
  cx: number;
  cy: number;
}

// Solar system center (must match SUN_X/SUN_Y in mapDraw.ts)
const SX = 700;
const SY = 550;

const TWO_PI = Math.PI * 2;

function pos(rx: number, ry: number, a: number, rot: number) {
  const cosR = Math.cos(rot), sinR = Math.sin(rot);
  const px = rx * Math.cos(a), py = ry * Math.sin(a);
  return {
    cx: Math.round(SX + px * cosR - py * sinR),
    cy: Math.round(SY + px * sinR + py * cosR),
  };
}

// 8 orbital slots, innermost (slot0 = free/lowest trophy) → outermost (slot7 = highest trophy)
// Visual data is placeholder — overridden by API seed data via useContinent
export const CONTINENTS: ContinentDef[] = [
  {
    id: 'slot0', slotId: 'slot0', continentId: 0,
    name: '행성 1', desc: '', color: '#8b50ff', grade: 'S', trophyReq: null,
    orbitRx: 90,  orbitRy: 70,  orbitAngle0: Math.PI / 4,         orbitSpeed: TWO_PI / 60_000,
    orbitRotation: 0.10, halfHeight: 35,
    ...pos(90, 70, Math.PI / 4, 0.10),
  },
  {
    id: 'slot1', slotId: 'slot1', continentId: 0,
    name: '행성 2', desc: '', color: '#ff6644', grade: 'C', trophyReq: 300,
    orbitRx: 160, orbitRy: 125, orbitAngle0: (5 * Math.PI) / 4,   orbitSpeed: TWO_PI / 90_000,
    orbitRotation: -0.15, halfHeight: 37,
    ...pos(160, 125, (5 * Math.PI) / 4, -0.15),
  },
  {
    id: 'slot2', slotId: 'slot2', continentId: 0,
    name: '행성 3', desc: '', color: '#00ff88', grade: 'B', trophyReq: 800,
    orbitRx: 230, orbitRy: 180, orbitAngle0: Math.PI / 2,          orbitSpeed: TWO_PI / 125_000,
    orbitRotation: 0.20, halfHeight: 40,
    ...pos(230, 180, Math.PI / 2, 0.20),
  },
  {
    id: 'slot3', slotId: 'slot3', continentId: 0,
    name: '행성 4', desc: '', color: '#44aaff', grade: 'B', trophyReq: 1000,
    orbitRx: 300, orbitRy: 235, orbitAngle0: (7 * Math.PI) / 4,   orbitSpeed: TWO_PI / 165_000,
    orbitRotation: -0.10, halfHeight: 42,
    ...pos(300, 235, (7 * Math.PI) / 4, -0.10),
  },
  {
    id: 'slot4', slotId: 'slot4', continentId: 0,
    name: '행성 5', desc: '', color: '#ff8c00', grade: 'B', trophyReq: 1000,
    orbitRx: 370, orbitRy: 290, orbitAngle0: (7 * Math.PI) / 6,   orbitSpeed: TWO_PI / 210_000,
    orbitRotation: 0.15, halfHeight: 45,
    ...pos(370, 290, (7 * Math.PI) / 6, 0.15),
  },
  {
    id: 'slot5', slotId: 'slot5', continentId: 0,
    name: '행성 6', desc: '', color: '#ffd700', grade: 'A', trophyReq: 2000,
    orbitRx: 440, orbitRy: 345, orbitAngle0: Math.PI / 3,          orbitSpeed: TWO_PI / 260_000,
    orbitRotation: -0.20, halfHeight: 48,
    ...pos(440, 345, Math.PI / 3, -0.20),
  },
  {
    id: 'slot6', slotId: 'slot6', continentId: 0,
    name: '행성 7', desc: '', color: '#ff1493', grade: 'A', trophyReq: 3000,
    orbitRx: 510, orbitRy: 400, orbitAngle0: (3 * Math.PI) / 2,   orbitSpeed: TWO_PI / 315_000,
    orbitRotation: 0.08, halfHeight: 50,
    ...pos(510, 400, (3 * Math.PI) / 2, 0.08),
  },
  {
    id: 'slot7', slotId: 'slot7', continentId: 0,
    name: '행성 8', desc: '', color: '#00f5ff', grade: 'S', trophyReq: 5000,
    orbitRx: 580, orbitRy: 455, orbitAngle0: (11 * Math.PI) / 6,  orbitSpeed: TWO_PI / 375_000,
    orbitRotation: -0.12, halfHeight: 53,
    ...pos(580, 455, (11 * Math.PI) / 6, -0.12),
  },
];
