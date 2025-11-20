import React, { useState, useEffect, useRef, useMemo, FC } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Popover, Modal, message } from 'antd';
import { menuList } from '@/constants';
import useUserStore from '@/store/user-store';
import eventBus from '@/utils/event-bus';
import useChat from '@/hooks/use-chat';
import { useEnterprise } from '@/hooks/use-enterprise';
import useSpaceStore from '@/store/space-store';
import { useTranslation } from 'react-i18next';
import { PersonSpace } from '@/components/space/person-space';
import SpaceModal from '@/components/space/space-modal';

// Assets
import spaceMore from '@/assets/imgs/space/space-more.svg';
import { deleteChatList } from '@/services/chat';
import { PostChatItem } from '@/types/chat';

// 最近使用列表组件Props接口
interface RecentListProps {
  isCollapsed: boolean;
  showRecent: boolean;
  setShowRecent: (show: boolean) => void;
  mixedChatList: any[];
  handleNavigateToChat: (item: any) => void;
  handleDeleteChat: (item: any, e: any) => void;
}

const RecentList: FC<RecentListProps> = ({
  isCollapsed,
  showRecent,
  setShowRecent,
  mixedChatList,
  handleNavigateToChat,
  handleDeleteChat,
}) => {
  const { t } = useTranslation();

  if (isCollapsed) return null;

  return (
    <div className="flex flex-col ml-3 flex-shrink-0 overflow-hidden">
      {/* 最近使用标题 */}
      <div
        className="flex items-center justify-between cursor-pointer pl-[3px] pb-[5px] pr-2.5 flex-shrink-0"
        onClick={() => setShowRecent(!showRecent)}
      >
        <span className="text-xs font-medium text-black/50">
          {t('sidebar.recentlyUsed')}
        </span>
        <img
          src={require('@/assets/imgs/sidebar/arrow-top.svg')}
          alt=""
          className={`transition-transform duration-300 ${
            showRecent ? '' : 'rotate-180'
          }`}
        />
      </div>

      {/* 最近使用列表容器 - 固定padding，避免动画 */}
      <div className="w-full pr-2.5 overflow-hidden">
        {/* 内部滚动区域 - 只做高度动画 */}
        <div
          className={`flex flex-col w-full overflow-x-hidden transition-[height,max-height] duration-300 ease-out  ${
            showRecent
              ? 'min-h-[50px] max-h-[300px] overflow-y-auto scrollbar-hide'
              : 'h-0 max-h-0 overflow-hidden'
          }`}
          style={{
            scrollbarWidth: 'none',
            msOverflowStyle: 'none',
          }}
        >
          {/* 内容区域 - 固定间距 */}
          <div
            className={`flex flex-col gap-2.5 py-2.5 w-full ${showRecent ? '' : 'opacity-0'}`}
          >
            {showRecent &&
              mixedChatList?.length > 0 &&
              mixedChatList.map((item: any) => (
                <div
                  key={item.botId}
                  className="group flex items-center cursor-pointer py-1 px-2 rounded hover:bg-[rgba(39,94,255,0.05)] flex-shrink-0 w-full"
                  onClick={() => handleNavigateToChat(item)}
                >
                  <img
                    src={item?.botAvatar}
                    alt=""
                    className="w-[18px] h-[18px] rounded-full flex-shrink-0"
                  />
                  <span className="ml-2 text-sm text-[#333] flex-1 overflow-hidden text-ellipsis whitespace-nowrap min-w-0">
                    {item?.botName}
                  </span>
                  <div
                    className="hidden group-hover:block w-2 h-2 bg-[url('@/assets/imgs/sidebar/close.svg')] bg-no-repeat bg-center hover:bg-[url('@/assets/imgs/sidebar/close-hover.svg')] flex-shrink-0 ml-1"
                    onClick={e => handleDeleteChat(item, e)}
                  />
                </div>
              ))}
          </div>
        </div>
      </div>
    </div>
  );
};

interface MenuListProps {
  isCollapsed: boolean;
  mixedChatList: PostChatItem[];
  onRefreshData?: () => void;
}

// Helper functions for MenuList component
const useMenuListHelpers = (
  t: any,
  user: any,
  setMobile: any,
  checkNeedCreateTeamFn: any,
  setMenuActiveKey: any,
  setIsShowSpacePopover: any,
  spaceButtonRef: any,
  handleToChat: any,
  setChatListId: any,
  setDeleteOpen: any,
  chatListId: string,
  onRefreshData?: () => void
) => {
  // 动态设置 Popover 的最大高度
  const updatePopoverMaxHeight = () => {
    if (spaceButtonRef.current) {
      const rect = spaceButtonRef.current.getBoundingClientRect();
      const topPosition = rect.top;
      document.documentElement.style.setProperty(
        '--popover-top',
        `${topPosition}px`
      );
    }
  };

  const handleShowSpacePopover = () => {
    updatePopoverMaxHeight(); // 更新 CSS 变量
    setIsShowSpacePopover((prev: boolean) => !prev);
  };

  // Chat and favorites management
  const handleNavigateToChat = (item: any) => {
    handleToChat(item?.botId);
  };

  const handleDeleteChat = (item: any, e: any) => {
    e.stopPropagation();
    setChatListId(item?.id);
    setDeleteOpen(true);
  };

  const handleDeleteChatConfirm = () => {
    deleteChatList({
      chatListId: Number(chatListId),
    })
      .then((res: any) => {
        setDeleteOpen(false);
        message.success(t('commonModal.agentDelete.success'));
        // Refresh data after successful deletion
        if (onRefreshData) {
          onRefreshData();
        }
      })
      .catch((err: any) => {
        console.log(err);
        setDeleteOpen(false);
        message.error(t('commonModal.agentDelete.failed'));
      });
  };

  // Check login
  const checkLogin = () => {
    // checkUserInfo().then((res: any) => {
    //   checkNeedCreateTeamFn();
    //   setMobile(res?.mobile);
    // });
    checkNeedCreateTeamFn();
    setMobile(user?.mobile);
  };

  // Effects handlers
  const initializeActiveMenu = (location: any) => {
    const path = window.location.pathname.replace(
      '/application-development',
      ''
    );
    menuList.map(item => {
      item.tabs.map(tab => {
        if (path.includes(tab.activeTab)) {
          setMenuActiveKey(tab.activeTab);
        }
      });
    });
  };

  const initializeApp = () => {
    checkLogin();
  };

  return {
    handleShowSpacePopover,
    handleNavigateToChat,
    handleDeleteChat,
    handleDeleteChatConfirm,
    initializeActiveMenu,
    initializeApp,
  };
};

// Helper function for dynamic menu list generation
const useDynamicMenuList = (
  isTeamSpaceEmpty: boolean,
  spaceType: any,
  spaceId: any,
  spaceName: any,
  t: any
) => {
  return useMemo(() => {
    // 无团队空间展示 智能体广场及插件广场
    if (isTeamSpaceEmpty) {
      return menuList.slice(0, 1);
    }

    return menuList.map(menuGroup => ({
      ...menuGroup,
      tabs: menuGroup.tabs.map(tab => {
        // 如果是 '我的智能体' 这个 tab，根据 spaceType 和 spaceId 动态设置 subTitle
        if (tab.activeTab === 'agent') {
          let dynamicSubTitle = t('sidebar.myAgents'); // 默认值
          if ((spaceType === 'personal' && spaceId) || spaceType === 'team') {
            dynamicSubTitle = t('sidebar.myAgentsManagement');
          }
          return {
            ...tab,
            subTitle: dynamicSubTitle,
          };
        }
        return tab;
      }),
    }));
  }, [spaceType, spaceId, spaceName, isTeamSpaceEmpty, t]);
};

// Menu Tab Component
const MenuTab: FC<{
  tab: any;
  isCollapsed: boolean;
  menuActiveKey: string;
  hoverTab: string;
  setMenuActiveKey: (key: string) => void;
  setHoverTab: (key: string) => void;
  navigate: any;
}> = ({
  tab,
  isCollapsed,
  menuActiveKey,
  hoverTab,
  setMenuActiveKey,
  setHoverTab,
  navigate,
}) => (
  <div
    key={`${tab?.subTitle}`}
    className={`group relative flex items-center px-3 py-3 gap-2 cursor-pointer rounded-[10px] hover:bg-[#F8FAFF] hover:text-[#275EFF] ${
      [menuActiveKey, hoverTab].includes(tab.activeTab)
        ? 'bg-[#F8FAFF] text-[#275EFF]'
        : 'text-[#7F7F7F]'
    } ${
      tab.subTitle === '插件广场' || tab.subTitle === '智能体广场'
        ? ''
        : !isCollapsed && 'ml-6'
    }`}
    onClick={() => {
      setMenuActiveKey(tab.activeTab);
      navigate(tab.path);
    }}
    onMouseEnter={() => setHoverTab(tab.activeTab)}
    onMouseLeave={() => setHoverTab('')}
  >
    <img
      src={
        [menuActiveKey, hoverTab].includes(tab.activeTab)
          ? tab.iconAct
          : tab.icon
      }
      className="w-[18px] h-[18px] flex-shrink-0"
      alt=""
    />
    {!isCollapsed && <span className="relative text-sm">{tab.subTitle}</span>}
    {isCollapsed && (
      <div
        className={`rounded-lg bg-white shadow-[0px_0px_20px_0px_rgba(0,18,70,0.08)] text-[#333333] whitespace-nowrap py-3 px-5 absolute -top-1.5 left-[54px] z-[3] ${
          hoverTab === tab.activeTab ? 'block' : 'hidden'
        }`}
      >
        {tab.subTitle}
      </div>
    )}
  </div>
);

// Delete Modal Component
const DeleteModal: FC<{
  deleteOpen: boolean;
  setDeleteOpen: (open: boolean) => void;
  handleDeleteChatConfirm: () => void;
  t: any;
}> = ({ deleteOpen, setDeleteOpen, handleDeleteChatConfirm, t }) => (
  <Modal
    open={deleteOpen}
    onCancel={() => setDeleteOpen(false)}
    closeIcon={null}
    className="[&_.ant-modal-content]:!py-8 [&_.ant-modal-content]:!px-8 [&_.ant-modal-content]:!pb-6 [&_.ant-btn]:mt-3 [&_.ant-btn]:w-[63px] [&_.ant-btn]:h-8"
    centered
    width={352}
    maskClosable={false}
    onOk={handleDeleteChatConfirm}
  >
    <div className="text-black/85 flex items-center gap-2.5 text-base font-medium leading-[1.4] overflow-hidden">
      <img
        src={require('@/assets/imgs/sidebar/warning.svg')}
        alt=""
        className="w-[22px] h-[22px]"
      />
      <span>{t('sidebar.confirmRemove')}</span>
    </div>
  </Modal>
);

const MenuList: FC<MenuListProps> = ({
  isCollapsed,
  mixedChatList,
  onRefreshData,
}) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();

  // User store
  const user = useUserStore((state: any) => state.user);
  const setMobile = useUserStore((state: any) => state.setMobile);

  // Space store
  const {
    isShowSpacePopover,
    spaceName,
    spaceType,
    spaceId,
    spaceAvatar,
    setIsShowSpacePopover,
  } = useSpaceStore();

  // Enterprise hooks
  const { checkNeedCreateTeamFn, isTeamSpaceEmpty } = useEnterprise();

  // Local state - using local state instead of recoil
  const [hoverTab, setHoverTab] = useState('');
  const [menuActiveKey, setMenuActiveKey] = useState('');
  const [showRecent, setShowRecent] = useState(true);
  const [chatListId, setChatListId] = useState('');
  const [deleteOpen, setDeleteOpen] = useState(false);

  // Refs
  const spaceButtonRef = useRef<HTMLDivElement | null>(null);

  // Custom hooks
  const { handleToChat } = useChat();

  // Helper functions
  const {
    handleShowSpacePopover,
    handleNavigateToChat,
    handleDeleteChat,
    handleDeleteChatConfirm,
    initializeActiveMenu,
    initializeApp,
  } = useMenuListHelpers(
    t,
    user,
    setMobile,
    checkNeedCreateTeamFn,
    setMenuActiveKey,
    setIsShowSpacePopover,
    spaceButtonRef,
    handleToChat,
    setChatListId,
    setDeleteOpen,
    chatListId,
    onRefreshData
  );

  // Dynamic menu list
  const getDynamicMenuList = useDynamicMenuList(
    isTeamSpaceEmpty,
    spaceType,
    spaceId,
    spaceName,
    t
  );

  // Effects
  useEffect(() => {
    initializeActiveMenu(location);
  }, [location]);

  useEffect(() => {
    initializeApp();
  }, []);

  return (
    <div
      className={`flex flex-col flex-1 min-h-0 mt-6 gap-4 ${
        isShowSpacePopover ? 'overflow-hidden' : 'overflow-y-auto'
      } scrollbar-none`}
    >
      {getDynamicMenuList.map((item, index) => (
        <div
          key={`${index}-${item?.title}`}
          className="text-gray-500 font-medium"
        >
          {item.tabs.map((tab: any, i) => (
            <MenuTab
              key={`${i}-${tab?.subTitle}`}
              tab={tab}
              isCollapsed={isCollapsed}
              menuActiveKey={menuActiveKey}
              hoverTab={hoverTab}
              setMenuActiveKey={setMenuActiveKey}
              setHoverTab={setHoverTab}
              navigate={navigate}
            />
          ))}
        </div>
      ))}

      <RecentList
        isCollapsed={isCollapsed}
        showRecent={showRecent}
        setShowRecent={setShowRecent}
        mixedChatList={mixedChatList}
        handleNavigateToChat={handleNavigateToChat}
        handleDeleteChat={handleDeleteChat}
      />

      <DeleteModal
        deleteOpen={deleteOpen}
        setDeleteOpen={setDeleteOpen}
        handleDeleteChatConfirm={handleDeleteChatConfirm}
        t={t}
      />
    </div>
  );
};

export default MenuList;
