import { ReactElement, useState, useEffect } from 'react';
import collapseGrayIcon from '@/assets/imgs/sidebar/collapseGray.svg';
import SidebarLogo from './sidebar-logo';
import CreateButton from './create-button';
import BottomLogin from './bottom-login';
import PersonalCenter from './personal-center';
import MenuList from './menu-list';
import useUserStore from '@/store/user-store';
import { postChatList } from '@/services/chat';
import { PostChatItem } from '@/types/chat';
import eventBus from '@/utils/event-bus';

interface User {
  nickname?: string;
  login?: string;
  avatar?: string;
  uid?: string;
}

interface SidebarProps {
  className?: string;

  // Logo props
  languageCode?: string;

  // Create button props
  isLogin?: boolean;
  onCreateClick?: () => void;
  onCreateAnalytics?: () => void;
  onNotLogin?: () => void;

  // Bottom login props
  user?: User;
  OrderTypeComponent?: ReactElement;
}

const Sidebar = ({
  className = '',

  languageCode = 'zh',

  // Create button props
  onCreateClick,
  onCreateAnalytics,
  onNotLogin,

  // Bottom login props
  user,
  OrderTypeComponent,
}: SidebarProps): ReactElement => {
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [isPersonCenterOpen, setIsPersonCenterOpen] = useState(false);

  // Shared chat data state
  const [mixedChatList, setMixedChatList] = useState<PostChatItem[]>([]);

  const getIsLogin = useUserStore.getState().getIsLogin;

  // Fetch chat list
  const getChatList = async () => {
    try {
      const res = await postChatList();
      setMixedChatList(res);
    } catch (error) {
      console.log(error);
    }
  };

  // Effect to fetch data on mount and setup event listeners
  useEffect(() => {
    getChatList();

    // Setup event bus listeners for data changes
    eventBus.on('chatListChange', getChatList);

    return () => {
      eventBus.off('chatListChange', getChatList);
    };
  }, []);

  return (
    <div
      className={`
        relative bg-white flex flex-col flex-shrink-0 p-4 h-full
        ${
          isCollapsed
            ? 'w-[76px] items-center justify-between'
            : 'w-[232px] rounded-r-3xl'
        }
        ${className}
      `}
    >
      {/* Collapse Icon */}
      <div
        className="
          absolute -right-4 top-1/2 -translate-y-1/2 z-[997] 
          flex items-center justify-center
          w-8 h-8 bg-white rounded-full cursor-pointer
          shadow-[0px_0px_20px_0px_rgba(0,18,70,0.08)]
          hover:bg-[#275EFF] transition-colors duration-300
          group
        "
        onClick={() => setIsCollapsed(!isCollapsed)}
      >
        <img
          src={collapseGrayIcon}
          alt="collapse"
          className={`
            transform rotate-180 transition-all duration-300 cursor-pointer z-[998]
            group-hover:brightness-0 group-hover:saturate-100 group-hover:invert
            ${isCollapsed ? 'rotate-[360deg]' : 'rotate-180'}
          `}
        />
      </div>

      {/* Main Content */}
      <div className="flex flex-col h-full">
        {/* Logo Section */}
        <SidebarLogo isCollapsed={isCollapsed} languageCode={languageCode} />

        {/* Create Button */}
        <CreateButton
          isCollapsed={isCollapsed}
          isLogin={getIsLogin()}
          onClick={onCreateClick}
          onAnalytics={onCreateAnalytics}
          onNotLogin={onNotLogin}
        />

        <MenuList
          isCollapsed={isCollapsed}
          mixedChatList={mixedChatList}
          onRefreshData={() => {
            getChatList();
          }}
        />

        {/* Bottom Login */}
        <BottomLogin
          isCollapsed={isCollapsed}
          user={user}
          OrderTypeComponent={OrderTypeComponent}
          isPersonCenterOpen={isPersonCenterOpen}
          setIsPersonCenterOpen={setIsPersonCenterOpen}
        />

        <PersonalCenter
          open={isPersonCenterOpen}
          onCancel={() => {
            setIsPersonCenterOpen(false);
          }}
          mixedChatList={mixedChatList}
          onRefreshData={() => {
            getChatList();
          }}
          onRefreshRecentData={getChatList}
        />
      </div>
    </div>
  );
};

export default Sidebar;
