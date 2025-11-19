import { performLogout, casdoorSdk } from '@/config/casdoor';

// 检查是否启用了 Casdoor
const isCasdoorEnabled = (): boolean => {
  const config = (casdoorSdk as any).config;
  const serverUrl = config?.serverUrl || '';
  // 如果 serverUrl 为空或者是 localhost:3000（fallback值），则认为未启用
  return !!(serverUrl && serverUrl !== '' && serverUrl !== 'http://localhost:3000');
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
