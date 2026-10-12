import archiveScene from '../assets/ip/status-archive-scene.jpg'
import archiveSceneLight from '../assets/ip/status-archive-scene-light.jpg'

export type PublicEmptyFigure = 'path' | 'shelf' | 'harbor' | 'prompt' | 'search'

export function StatusFigure({ kind, className }: { kind: PublicEmptyFigure; className?: string }) {
  return (
    <span className={className ? `status-figure ${className}` : 'status-figure'} aria-hidden="true">
      <img
        className="status-figure-light"
        src={archiveSceneLight}
        alt=""
        width={1536}
        height={864}
        loading={kind === 'path' ? 'eager' : 'lazy'}
        decoding="async"
      />
      <img
        className="status-figure-dark"
        src={archiveScene}
        alt=""
        width={1536}
        height={864}
        loading={kind === 'path' ? 'eager' : 'lazy'}
        decoding="async"
      />
    </span>
  )
}
