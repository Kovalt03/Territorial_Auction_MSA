import { Suspense } from 'react';
import { RouterProvider } from 'react-router';

import { router } from './routes';
import { AppProvider } from './context/AppContext';
import { ChunkErrorBoundary } from './components/ChunkErrorBoundary';
import { DelayedFallback } from './components/DelayedFallback';

export function App() {
  return (
    <AppProvider>
      <ChunkErrorBoundary>
        <Suspense fallback={<DelayedFallback />}>
          <RouterProvider router={router} />
        </Suspense>
      </ChunkErrorBoundary>
    </AppProvider>
  );
}
