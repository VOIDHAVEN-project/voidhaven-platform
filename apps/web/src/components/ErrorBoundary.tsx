import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { error: Error | null }

/**
 * Without this, any uncaught render error anywhere in the tree used to blank the entire
 * page with no way back except a manual URL edit. Draft content in an open editor is not
 * recoverable either way once React unmounts the tree, but at least navigation keeps
 * working for everything else.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // eslint-disable-next-line no-console
    console.error('Unhandled render error', error, info.componentStack)
  }

  render() {
    if (this.state.error) {
      return (
        <section className="page state">
          <p className="eyebrow">出错了</p>
          <h1>页面暂时无法显示</h1>
          <p>发生了一个未预期的错误，请重试或返回首页。若持续出现，请通过反馈页告诉我们。</p>
          <div className="hero-actions">
            <button type="button" className="button primary" onClick={() => this.setState({ error: null })}>
              重试
            </button>
            <a className="button ghost" href="/">
              返回首页
            </a>
          </div>
        </section>
      )
    }
    return this.props.children
  }
}
