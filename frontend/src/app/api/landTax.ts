import { apiClient } from './client';
import type { LandTaxStatus, LandTaxLogResponse, TaxStatus } from '../types/landTax';

export function fetchLandTaxStatus() {
  return apiClient.get<LandTaxStatus>('/land-tax/status');
}

export function fetchLandTaxLogList(
  params: { page?: number; size?: number; status?: TaxStatus } = {},
) {
  const query = new URLSearchParams();
  query.set('page', String(params.page ?? 0));
  query.set('size', String(params.size ?? 10));
  if (params.status) query.set('status', params.status);
  return apiClient.get<LandTaxLogResponse>(`/land-tax/logs?${query.toString()}`);
}
