import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { SiteLayout } from './components/SiteLayout'
import { AboutPage } from './pages/AboutPage'
import { ContentDetailPage } from './pages/ContentDetailPage'
import { ContentListPage } from './pages/ContentListPage'
import { FeedbackPage } from './pages/FeedbackPage'
import { FixtureSourcePage } from './pages/FixtureSourcePage'
import { HomePage } from './pages/HomePage'
import { JoinPage } from './pages/JoinPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SignInPage } from './pages/SignInPage'
import { WorkspacePage } from './pages/WorkspacePage'
import { ActivationPage } from './pages/ActivationPage'

function LegacyContentRedirect() { const { id = '' } = useParams(); return <Navigate to={`/content/${id}`} replace /> }
export default function App() {
  return <BrowserRouter><Routes>
    <Route element={<SiteLayout />}>
      <Route index element={<HomePage />} />
      <Route path="competitions" element={<ContentListPage kind="COMPETITION" />} />
      <Route path="competitions/:id" element={<LegacyContentRedirect />} />
      <Route path="resources" element={<ContentListPage kind="RESOURCE" />} />
      <Route path="resources/:id" element={<LegacyContentRedirect />} />
      <Route path="recruitments" element={<ContentListPage kind="RECRUITMENT" />} />
      <Route path="recruitments/:id" element={<LegacyContentRedirect />} />
      <Route path="content/:id" element={<ContentDetailPage />} />
      <Route path="feedback" element={<FeedbackPage />} />
      <Route path="local-fixtures/source" element={<FixtureSourcePage />} />
      <Route path="join" element={<JoinPage />} />
      <Route path="about" element={<AboutPage />} />
    </Route>
    <Route path="/auth/sign-in" element={<SignInPage />} />
    <Route path="/auth/activate" element={<ActivationPage />} />
    <Route path="/workspace/*" element={<WorkspacePage />} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes></BrowserRouter>
}
