import { Component, type ReactNode, type ErrorInfo } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
}

function isChunkLoadError(error: Error): boolean {
  const name = error.name || '';
  const message = error.message || '';
  return name === 'ChunkLoadError'
    || message.includes('Loading chunk')
    || message.includes('Failed to fetch dynamically imported module')
    || message.includes('Importing a module script failed');
}

export class ChunkErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    if (isChunkLoadError(error)) {
      console.warn('Chunk load failed — likely deploy hash mismatch or network error', { message: error.message, stack: info.componentStack });
      return;
    }
    console.error('Unexpected runtime error in lazy boundary', error, info.componentStack);
  }

  handleReload = () => {
    window.location.reload();
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    return (
      <div className="page-root">
        <div className="flex-1 flex flex-col items-center justify-center gap-4 px-4 text-center">
          <span className="text-4xl">⚠</span>
          <div>
            <p className="text-foreground font-semibold text-base mb-1">페이지를 불러오지 못했습니다</p>
            <p className="text-muted text-xs">새로고침하거나 잠시 후 다시 시도해주세요.</p>
          </div>
          <button
            onClick={this.handleReload}
            className="h-10 px-5 bg-primary text-surface rounded-xl font-bold text-sm hover:brightness-110 transition-all"
          >
            새로고침
          </button>
        </div>
      </div>
    );
  }
}
