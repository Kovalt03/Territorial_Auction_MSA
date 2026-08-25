import { ReactNode } from 'react';
import { Navigate } from 'react-router';

import { useApp } from '../context/AppContext';

interface Props {
  children: ReactNode;
}

export function PrivateRoute({ children }: Props) {
  const { isLoggedIn, isAuthLoading } = useApp();

  if (isAuthLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-surface">
        <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (!isLoggedIn) return <Navigate to="/login" replace />;

  return <>{children}</>;
}
