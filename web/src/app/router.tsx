import { createBrowserRouter } from 'react-router'
import { AppLayout } from '@/layouts/AppLayout'
import { AppDetailPage } from '@/pages/AppDetailPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { LoginPage } from '@/pages/LoginPage'
import { NotFoundPage } from '@/pages/NotFoundPage'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <AppLayout />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'apps/:appId', element: <AppDetailPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])
