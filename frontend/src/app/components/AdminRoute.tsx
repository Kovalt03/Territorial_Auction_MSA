import { ReactNode } from 'react';
import { Navigate } from 'react-router';

import { getTokenRole } from '../api/jwt';

interface Props {
  children: ReactNode;
}

export function AdminRoute({ children }: Props) {
  if (getTokenRole() !== 'ADMIN') return <Navigate to="/admin/login" replace />;
  return <>{children}</>;
}
