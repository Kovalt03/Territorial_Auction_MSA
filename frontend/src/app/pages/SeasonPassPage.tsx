import { useState } from 'react';

import { useApp } from '../context/AppContext';
import { useSeasonPass } from '../hooks/useSeasonPass';
import {
  purchaseSeasonPass,
  purchaseSeasonLevel,
  claimMissionApi,
  claimRewardApi,
} from '../api/season';
import { fetchMyWallet } from '../api/user';
import { ApiError } from '../api/client';

import { GNB } from '../components/GNB';
import { Button } from '../components/Button';
import { LoadingState } from '../components/LoadingState';
import { SeasonPassHeader } from './SeasonPassHeader';
import { SeasonRewardTrack } from './SeasonRewardTrack';
import { SeasonMissionPanel } from './SeasonMissionPanel';
import { SeasonBenefits } from './SeasonBenefits';
import { SeasonPurchaseModal } from './SeasonPurchaseModal';

const MAX_LEVEL = 30;

export function SeasonPassPage() {
  const { ap, hasPass, syncAP, syncGP, syncPass } = useApp();
  const { progress, missions, isLoading, error, reload } = useSeasonPass();
  const [showPurchase, setShowPurchase] = useState(false);
  const [isProcessing, setIsProcessing] = useState(false);
  const [purchaseError, setPurchaseError] = useState<string | null>(null);
  const [isLevelProcessing, setIsLevelProcessing] = useState(false);
  const [levelError, setLevelError] = useState<string | null>(null);
  const [claimingId, setClaimingId] = useState<number | null>(null);

  const handlePurchase = async () => {
    setIsProcessing(true);
    setPurchaseError(null);
    try {
      const result = await purchaseSeasonPass();
      syncAP(result.remainingAP);
      syncPass(true, result.expiresAt);
      setShowPurchase(false);
      void reload();
    } catch (e) {
      setPurchaseError(
        e instanceof ApiError && e.status >= 400 && e.status < 500
          ? e.message
          : '구매에 실패했습니다. 다시 시도해주세요.',
      );
    } finally {
      setIsProcessing(false);
    }
  };

  const handleLevelPurchase = async () => {
    setIsLevelProcessing(true);
    setLevelError(null);
    try {
      const result = await purchaseSeasonLevel();
      syncAP(result.remainingAP);
      await reload();
    } catch (e) {
      setLevelError(
        e instanceof ApiError && e.status >= 400 && e.status < 500
          ? e.message
          : '레벨 구매에 실패했습니다. 다시 시도해주세요.',
      );
    } finally {
      setIsLevelProcessing(false);
    }
  };

  const handleClaim = async (id: number, claim: (id: number) => Promise<unknown>) => {
    setClaimingId(id);
    try {
      await claim(id);
      await reload();
      // 보상 지급(GP/아이템)·미션 XP 반영 후 지갑 잔액 동기화
      const wallet = await fetchMyWallet();
      syncAP(wallet.availableAP);
      syncGP(wallet.availableGP);
    } catch (e) {
      console.warn('[SeasonPassPage] claim failed', e);
    } finally {
      setClaimingId(null);
    }
  };

  return (
    <div className="page-root">
      <GNB />

      <div className="page-body">
        <div className="max-w-3xl mx-auto">
          {isLoading ? (
            <LoadingState className="card p-10" />
          ) : error ? (
            <div className="card p-6 text-center">
              <p className="text-danger text-sm">⚠ {error}</p>
            </div>
          ) : progress ? (
            <>
              <SeasonPassHeader progress={progress} />

              {!hasPass && (
                <div className="card p-4 mb-5 flex items-center justify-between gap-3">
                  <p className="text-muted text-[13px]">
                    프리미엄 패스로 같은 레벨에서 추가 보상을 받으세요.
                  </p>
                  <Button
                    onClick={() => setShowPurchase(true)}
                    disabled={ap < progress.passCostAp}
                    className="flex-shrink-0"
                  >
                    구매 ({progress.passCostAp.toLocaleString()} AP)
                  </Button>
                </div>
              )}

              {progress.currentLevel < MAX_LEVEL && (
                <div className="card p-4 mb-5 flex items-center justify-between gap-3">
                  <div>
                    <p className="text-foreground text-[13px] font-semibold">레벨 즉시 구매</p>
                    <p className="text-muted text-[11px] mt-0.5">
                      AP로 시즌 패스 레벨을 1 올립니다.
                    </p>
                    {levelError && <p className="text-danger text-[11px] mt-1">⚠ {levelError}</p>}
                  </div>
                  <Button
                    onClick={() => void handleLevelPurchase()}
                    disabled={ap < progress.levelUpCostAp || isLevelProcessing}
                    className="flex-shrink-0"
                  >
                    {isLevelProcessing
                      ? '처리 중...'
                      : `+1 레벨 (${progress.levelUpCostAp.toLocaleString()} AP)`}
                  </Button>
                </div>
              )}

              <SeasonRewardTrack
                rewards={progress.rewards}
                currentLevel={progress.currentLevel}
                hasPass={hasPass}
                claimingId={claimingId}
                onClaim={id => void handleClaim(id, claimRewardApi)}
              />
              <SeasonMissionPanel
                missions={missions}
                claimingId={claimingId}
                onClaim={id => void handleClaim(id, claimMissionApi)}
              />
              <SeasonBenefits hasPass={hasPass} />
            </>
          ) : null}
        </div>
      </div>

      {showPurchase && (
        <SeasonPurchaseModal
          ap={ap}
          cost={progress?.passCostAp ?? 0}
          isProcessing={isProcessing}
          error={purchaseError}
          onClose={() => setShowPurchase(false)}
          onConfirm={() => void handlePurchase()}
        />
      )}
    </div>
  );
}
