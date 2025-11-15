import { performLogout, casdoorSdk } from '@/config/casdoor';

export const handleLoginRedirect = (): void => {
  // 本地开发模式下,如果没有配置 Casdoor,则模拟登录成功
  const casdoorUrl = import.meta.env.CONSOLE_CASDOOR_URL || import.meta.env.VITE_CASDOOR_SERVER_URL;
  
  if (!casdoorUrl || casdoorUrl === '') {
    console.log('🔓 本地开发模式: 绕过 Casdoor 认证,模拟登录成功');
    // 设置一个假的 token,让前端认为已登录
    localStorage.setItem('accessToken', 'local-dev-token');
    localStorage.setItem('mockUser', JSON.stringify({
      nickname: '本地开发用户',
      login: 'local-dev',
      avatar: '',
      uid: 'local-dev-uid'
    }));
    // 刷新页面以更新登录状态
    window.location.reload();
    return;
  }
  
  // 正常环境下使用 Casdoor 登录
  sessionStorage.setItem(
    'postLoginRedirect',
    window.location.pathname + window.location.search
  );
  casdoorSdk.signin_redirect();
};

export const handleLogout = (): void => {
  performLogout(window.location.origin);
};
