import { QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router'
import { queryClient } from '@/app/query-client'
import { router } from '@/app/router'
import '@/styles/index.css'

const root = document.getElementById('root')
if (!root) throw new Error('#root 엘리먼트가 없습니다.')

createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      {import.meta.env.DEV && <ReactQueryDevtools buttonPosition="bottom-left" />}
    </QueryClientProvider>
  </StrictMode>,
)
