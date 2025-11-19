import Loading from '@/components/loading';
import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import Layout from '@/layouts/index';
import ConfigPage from '@/pages/config-page';

const LoginPage = lazy(() => import('@/pages/login'));
const CallbackPage = lazy(() => import('@/pages/callback'));
const PersonalModel = lazy(
  () => import('@/pages/model-management/personal-model/personal-model-home')
);
const ModelDetail = lazy(() => import('@/pages/model-management/model-detail'));
const ResourceManagement = lazy(() => import('@/pages/resource-management'));
const WorkFlow = lazy(() => import('@/pages/workflow'));

const ChatPage = lazy(() => import('@/pages/chat-page'));
const SpacePage = lazy(() => import('@/pages/space-page'));
const SharePage = lazy(() => import('@/pages/share-page'));

const routes = [
  {
    path: '/login',
    element: (
      <Suspense fallback={<Loading />}>
        <LoginPage />
      </Suspense>
    ),
  },
  {
    path: '/',
    element: (
      <Suspense fallback={<Loading />}>
        <Layout />
      </Suspense>
    ),
    children: [
      {
        index: true,
        element: <Navigate to="/space/agent" />,
      },
      {
        path: '/management/model',
        element: (
          <Suspense fallback={<Loading />}>
            <PersonalModel />
          </Suspense>
        ),
      },
      {
        path: '/management/model/personalModel',
        element: (
          <Suspense fallback={<Loading />}>
            <PersonalModel />
          </Suspense>
        ),
      },
      {
        path: '/management/model/detail/:id',
        element: (
          <Suspense fallback={<Loading />}>
            <ModelDetail />
          </Suspense>
        ),
      },
      {
        path: '/resource/*',
        element: (
          <Suspense fallback={<Loading />}>
            <ResourceManagement />
          </Suspense>
        ),
      },
    ],
  },
  {
    path: '/space',
    element: (
      <Suspense fallback={<Loading />}>
        <Layout showHeader={false} />
      </Suspense>
    ),
    children: [
      {
        path: '/space/*',
        element: (
          <Suspense fallback={<Loading />}>
            <SpacePage />
          </Suspense>
        ),
      },
    ],
  },
  {
    path: '/callback',
    element: (
      <Suspense fallback={<Loading />}>
        <CallbackPage />
      </Suspense>
    ),
  },
  {
    path: '/chat/:botId/:version?',
    element: (
      <Suspense fallback={<Loading />}>
        <ChatPage />
      </Suspense>
    ),
  },
  {
    path: '/space',
    children: [
      {
        path: '/space/config/*',
        element: <ConfigPage />,
      },
    ],
  },
  {
    path: '/work_flow/:id/arrange',
    element: (
      <Suspense fallback={<Loading />}>
        <WorkFlow />
      </Suspense>
    ),
  },
  {
    path: '/sharepage',
    children: [
      {
        path: '/sharepage',
        element: (
          <Suspense fallback={<Loading />}>
            <SharePage />
          </Suspense>
        ),
      },
    ],
  },
];

const router: ReturnType<typeof createBrowserRouter> =
  createBrowserRouter(routes);

export default router;
