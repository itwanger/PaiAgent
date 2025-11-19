import React, { useMemo, useState, ReactNode, memo, useEffect } from 'react';
import { useNavigate, useParams, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import useFlowsManager from '@/components/workflow/store/use-flows-manager';
import FlowEdit from '@/components/workflow/modal/flow-edit';
import dayjs from 'dayjs';

import arrowLeft from '@/assets/imgs/knowledge/workflow-back.png';
import flowEditIcon from '@/assets/imgs/workflow/flow-edit-icon.png';
import flowSuccessIcon from '@/assets/imgs/workflow/flow-success-icon.png';
import flowRunningIcon from '@/assets/imgs/workflow/flow-running-icon.png';
import flowFailedIcon from '@/assets/imgs/workflow/flow-failed-icon.png';

interface FlowHeaderProps {
  currentTab?: 'arrange' | 'overview';
  children?: ReactNode;
}

interface FlowStatusProps {
  flowResult: unknown;
  t: unknown;
}

const FlowStatus: React.FC<FlowStatusProps> = ({ flowResult, t }) => {
  const statusStyle = useMemo(() => {
    if (!flowResult?.status) return { background: '', color: '', icon: '' };
    switch (flowResult.status) {
      case 'running':
        return {
          background: '#FEE4CD',
          color: '#966941',
          icon: flowRunningIcon,
        };
      case 'success':
        return {
          background: '#D6FEC8',
          color: '#44974B',
          icon: flowSuccessIcon,
        };
      default:
        return {
          background: '#FFD2D2',
          color: '#F74E43',
          icon: flowFailedIcon,
        };
    }
  }, [flowResult]);

  if (!flowResult?.status) return null;

  return (
    <div
      className="px-1.5 py-0.5 rounded-sm flex items-center flex-wrap gap-1 text-xs"
      style={{ background: statusStyle.background, color: statusStyle.color }}
    >
      <img
        src={statusStyle.icon}
        alt=""
        className={`w-3 h-3 ${flowResult.status === 'running' ? 'flow-rotate-center' : ''}`}
      />
      <span>
        {flowResult.status === 'running'
          ? t('workflow.nodes.header.testRunning')
          : flowResult.status === 'success'
            ? t('workflow.nodes.header.runCompleted')
            : t('workflow.nodes.header.runFailed')}
      </span>
      {flowResult.status !== 'running' && (
        <>
          <span>{flowResult.timeCost}s</span>
          <span
            className="w-[2px] h-[2px] rounded-full"
            style={{ background: statusStyle.color }}
          ></span>
          <span>{flowResult.totalTokens} Tokens</span>
        </>
      )}
    </div>
  );
};

interface FlowTabsProps {
  currentTab: 'arrange' | 'overview';
  id: string;
  t: unknown;
  navigate: ReturnType<typeof useNavigate>;
}

const FlowTabs: React.FC<FlowTabsProps> = ({ currentTab, id, t, navigate }) => (
  <div className="flex items-center justify-center w-1/4 gap-4">
    {['arrange'].map(tab => (
      <div
        key={tab}
        className={`flex items-center justify-center py-2.5 px-8 rounded-xl font-medium cursor-pointer ${
          currentTab === tab ? 'config-tabs-active' : 'config-tabs-normal'
        }`}
        onClick={() => navigate(`/work_flow/${id}/${tab}`, { replace: true })}
      >
        <span className={`${tab}-icon`}></span>
        <span className="ml-2">
          {t(
            `workflow.nodes.header.${tab === 'arrange' ? 'arrange' : 'analysis'}`
          )}
        </span>
      </div>
    ))}
  </div>
);

const FlowHeader: React.FC<FlowHeaderProps> = ({ children, currentFlow }) => {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const flowResult = useFlowsManager(state => state.flowResult);
  const historyVersion = useFlowsManager(state => state.historyVersion);
  const historyVersionData = useFlowsManager(state => state.historyVersionData);
  const [currentTab, setCurrentTab] = useState('arrange');
  const [editModal, setEditModal] = useState(false);

  useEffect(() => {
    console.log('location@@', location);
    setCurrentTab(location?.pathname?.split('/')?.pop());
  }, [location]);

  return (
    <div onKeyDown={e => e.stopPropagation()}>
      {editModal && currentFlow && (
        <FlowEdit currentFlow={currentFlow} setEditModal={setEditModal} />
      )}
      <div
        className="w-full h-[80px] bg-[#fff] border-b border-[#e2e8ff] flex justify-between px-6 py-5"
        style={{ borderRadius: '0px 0px 24px 24px' }}
      >
        {/* 左侧返回 + 名称 */}
        <div className="relative flex items-center flex-1 gap-2">
          <img
            src={arrowLeft}
            className="w-[28px] cursor-pointer"
            alt=""
            onClick={() => navigate('/space/agent')}
          />
          <div className="relative flex items-center gap-4">
            <img
              src={currentFlow?.avatarIcon}
              className="w-[39px] h-[39px]"
              alt=""
            />
            <div className="flex flex-col h-full">
              <div className="flex items-center gap-1 text-center">
                <span className="font-medium">{currentFlow?.name}</span>
                {currentTab === 'arrange' && (
                  <img
                    src={flowEditIcon}
                    className="w-[14px] h-[14px] cursor-pointer"
                    alt=""
                    onClick={() => setEditModal(true)}
                  />
                )}
                {historyVersion && (
                  <span className="bg-[#E9EEFF] w-[30px] h-[18px] text-[#275EFF] text-[10px] rounded-[4px] flex items-center justify-center rounded-[7px]">
                    {historyVersionData?.name}
                  </span>
                )}
              </div>
              {currentTab === 'arrange' && (
                <div className="flex items-center gap-3 text-[14px] mt-[3px]">
                  <p
                    className="text-desc max-w-[160px] text-overflow"
                    title={currentFlow?.description}
                  >
                    {historyVersion
                      ? historyVersionData?.description
                      : currentFlow?.description}
                  </p>
                  {historyVersion == false && (
                    <div className="text-[14px] text-[#9E9E9E] rounded-sm flex items-center jusity-center">
                      {t('workflow.nodes.header.autoSaved')}{' '}
                      {dayjs(currentFlow?.updateTime)?.format(
                        'YYYY-MM-DD HH:mm:ss'
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
            <FlowStatus flowResult={flowResult} t={t} />
          </div>
        </div>

        {/* 中间 Tabs */}
        {id && (
          <FlowTabs currentTab={currentTab} id={id} t={t} navigate={navigate} />
        )}

        {/* 右侧操作按钮 */}
        <div className="flex justify-end flex-1">{children}</div>
      </div>
    </div>
  );
};

export default memo(FlowHeader);
