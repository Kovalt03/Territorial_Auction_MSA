import { useState, useEffect } from 'react';

import { fetchContinentList } from '../api/map';
import { CONTINENTS, type ContinentDef } from '../data/continents';
import { SUN_X, SUN_Y } from '../components/mapDraw';

export function useContinent() {
  const [continents, setContinents] = useState<ContinentDef[]>(CONTINENTS);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchContinentList()
      .then(data => {
        // Sort: null trophy = innermost slot, then ascending; tiebreak by continentId
        const sorted = [...data.continent].sort((a, b) => {
          if (a.minTrophyRequired === null) return -1;
          if (b.minTrophyRequired === null) return 1;
          return (a.minTrophyRequired - b.minTrophyRequired) || (a.continentId - b.continentId);
        });

        const merged: ContinentDef[] = CONTINENTS.map((slot, i) => {
          const api = sorted[i];
          if (!api) return slot;
          const cosR = Math.cos(slot.orbitRotation), sinR = Math.sin(slot.orbitRotation);
          const px = slot.orbitRx * Math.cos(slot.orbitAngle0);
          const py = slot.orbitRy * Math.sin(slot.orbitAngle0);
          return {
            ...slot,
            id: String(api.continentId),
            continentId: api.continentId,
            name: api.continentName,
            desc: api.description ?? slot.desc,
            color: api.themeColor ?? slot.color,
            grade: api.grade ?? slot.grade,
            trophyReq: api.minTrophyRequired,
            cx: Math.round(SUN_X + px * cosR - py * sinR),
            cy: Math.round(SUN_Y + px * sinR + py * cosR),
          };
        });
        setContinents(merged);
      })
      .catch(() => { /* keep static defaults on API failure */ })
      .finally(() => setIsLoading(false));
  }, []);

  return { continents, isLoading };
}
