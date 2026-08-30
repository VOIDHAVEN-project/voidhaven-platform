export function LoadingState() { return <div className="state" role="status">正在整理信息…</div> }
export function EmptyState({ label }: { label: string }) { return <div className="state"><strong>暂时没有{label}</strong><span>请稍后再来，或通过投稿/反馈提供线索。</span></div> }
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) { return <div className="state state-error" role="alert"><strong>暂时无法加载</strong><span>{message}</span>{onRetry ? <button onClick={onRetry}>重新尝试</button> : null}</div> }
