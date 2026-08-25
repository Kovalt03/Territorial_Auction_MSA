import { useLandTax } from '../hooks/useLandTax';

import { LandTaxGraceBanner } from './LandTaxGraceBanner';
import { LandTaxStatusSection } from './LandTaxStatusSection';
import { LandTaxLogList } from './LandTaxLogList';

export function LandTaxView() {
  const {
    status,
    isStatusLoading,
    statusError,
    latestLog,
    logs,
    totalCount,
    isLogsLoading,
    logsError,
    page,
    statusFilter,
    setPage,
    setStatusFilter,
  } = useLandTax();

  return (
    <>
      <LandTaxGraceBanner latestLog={latestLog} />
      <LandTaxStatusSection status={status} isLoading={isStatusLoading} error={statusError} />
      <LandTaxLogList
        logs={logs}
        totalCount={totalCount}
        page={page}
        statusFilter={statusFilter}
        isLoading={isLogsLoading}
        error={logsError}
        onPageChange={setPage}
        onFilterChange={setStatusFilter}
      />
    </>
  );
}
