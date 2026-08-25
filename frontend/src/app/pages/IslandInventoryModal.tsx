import type { InventoryItem } from '../types/island';

import { buildingColors, buildingLabels, type BuildingType } from './islandGrid';

interface Props {
  inventory: InventoryItem[];
  onDeploy: (idx: number) => void;
  onClose: () => void;
}

export function IslandInventoryModal({ inventory, onDeploy, onClose }: Props) {
  return (
    <div className="modal-center-overlay">
      <div className="modal-backdrop" onClick={onClose} />
      <div className="relative rounded-2xl overflow-hidden flex flex-col w-[480px] max-h-[70vh] bg-panel border-[1.5px] border-secondary">
        <div className="modal-header-secondary bg-[#1a0a35]">
          <div>
            <h3 className="text-secondary font-bold text-xl">📦 보관함</h3>
            <p className="text-muted text-xs">건물 {inventory.length}개 보관 중 · 배치하기를 눌러 그리드에 재배치</p>
          </div>
          <button onClick={onClose} className="btn-close">✕</button>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {inventory.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 gap-3">
              <span className="text-[40px]">📭</span>
              <p className="text-muted text-sm">보관함이 비어 있습니다</p>
              <p className="text-outline text-xs">건물 셀을 클릭한 뒤 "보관함에 담기"를 선택하세요</p>
            </div>
          ) : (
            <div className="space-y-2">
              {inventory.map((item, idx) => {
                const itemType = item.buildingType.toLowerCase() as BuildingType;
                const color = buildingColors[itemType] ?? '#8892b0';
                return (
                  <div key={item.inventoryId} className="rounded-xl p-3 flex items-center gap-3 bg-elevated" style={{ border: `1px solid ${color}50` }}>
                    <div className="w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0" style={{ background: color + '25', border: `1px solid ${color}60` }}>
                      <span className="text-[22px]">{buildingLabels[itemType] ?? '🏗'}</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-sm" style={{ color }}>{item.buildingTypeName}</p>
                      <p className="text-muted text-[11px]">수량: {item.quantity}개</p>
                    </div>
                    <button
                      onClick={() => onDeploy(idx)}
                      className="h-9 px-4 rounded-lg font-semibold transition-all hover:brightness-110 text-xs"
                      style={{ background: color + '30', color, border: `1px solid ${color}` }}
                    >
                      배치하기
                    </button>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
