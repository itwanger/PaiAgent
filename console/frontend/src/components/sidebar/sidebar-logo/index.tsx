import { ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
import agentLog from '@/assets/imgs/sidebar/agentLog.svg';

interface SidebarLogoProps {
  isCollapsed: boolean;
  isEnterprise?: boolean;
  enterpriseLogo?: string | undefined;
  languageCode?: string;
}

const SidebarLogo = ({ isCollapsed }: SidebarLogoProps): ReactElement => {
  const navigate = useNavigate();

  const handleLogoClick = (): void => {
    navigate('/');
  };

  return (
    <div className="flex items-center justify-center gap-1">
      <img
        src={agentLog}
        className="w-[40px] cursor-pointer"
        alt="Pai Agent"
        style={{ height: isCollapsed ? '34px' : 'auto' }}
        onClick={handleLogoClick}
      />
      {!isCollapsed && <span className="font-bold">Pai Agent</span>}
    </div>
  );
};

export default SidebarLogo;
