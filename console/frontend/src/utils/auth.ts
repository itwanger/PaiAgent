import { performLogout, casdoorSdk } from '@/config/casdoor';

// 检查是否启用了 Casdoor
const isCasdoorEnabled = (): boolean => {
  const config = (casdoorSdk as any).config;
  return !!(config?.serverUrl && config.serverUrl !== '');
};

export const handleLoginRedirect = (): void => {
  // 如果 Casdoor 未启用，跳转到本地登录页
  if (!isCasdoorEnabled()) {
    window.location.href = '/login';
    return;
  }
  
  sessionStorage.setItem(
    'postLoginRedirect',
    window.location.pathname + window.location.search
  );
  casdoorSdk.signin_redirect();
};

export const handleLogout = (): void => {
  performLogout(window.location.origin);
};
