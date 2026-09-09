import { Component, type ErrorInfo, type ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { ThemeProvider } from './components/theme'
import { PublicShell, ScrollManager } from './components/shell'
import { HomePage, CatalogPage, DetailPage, SearchPage, AboutPage, NotFoundPage } from './pages/public'
import { FeedbackPage, JoinPage } from './pages/forms'
import { SignInPage, ActivationPage } from './pages/auth'
import { Workspace } from './pages/workspace'

class AppErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state = { error: null as Error | null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('页面渲染失败', error, info)
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <main className="session-state">
        <Link className="wordmark" to="/">VOIDHAVEN／</Link>
        <section>
          <p className="eyebrow">页面状态</p>
          <h1>暂时无法显示此页面</h1>
          <p className="muted">页面遇到意外问题。请重新加载；如果仍然出现，请退出后重新登录。</p>
          <div className="button-row">
            <button className="button primary" onClick={() => window.location.reload()}>重新加载</button>
            <Link className="button ghost" to="/auth/sign-in">返回登录</Link>
          </div>
        </section>
      </main>
    )
  }
}

function RouteErrorBoundary({ children }: { children: ReactNode }) {
  const location = useLocation()
  return <AppErrorBoundary key={location.pathname}>{children}</AppErrorBoundary>
}

export default function App() {
  return (
    <ThemeProvider>
      <BrowserRouter>
        <ScrollManager />
        <RouteErrorBoundary>
          <Routes>
          <Route element={<PublicShell />}>
            <Route path="/" element={<HomePage />} />
            <Route path="/competitions" element={<CatalogPage key="competitions" kind="COMPETITION" />} />
            <Route path="/resources" element={<CatalogPage key="resources" kind="RESOURCE" />} />
            <Route path="/recruitments" element={<CatalogPage key="recruitments" kind="RECRUITMENT" />} />
            <Route path="/recruitment" element={<Navigate replace to="/recruitments" />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/content/:publicId" element={<DetailPage />} />
            <Route path="/feedback" element={<FeedbackPage />} />
            <Route path="/join" element={<JoinPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="*" element={<NotFoundPage />} />
          </Route>
          <Route path="/auth/sign-in" element={<SignInPage />} />
          <Route path="/auth/activate" element={<ActivationPage />} />
          <Route path="/workspace/*" element={<Workspace />} />
          </Routes>
        </RouteErrorBoundary>
      </BrowserRouter>
    </ThemeProvider>
  )
}
