import React, { memo, useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { message, Select, Tooltip } from 'antd';
import { throttle } from 'lodash';
import { enableBotFavorite } from '@/services/agent'; // NOTE: 需更换接口
import { useTranslation } from 'react-i18next';
import {
  getAgentList,
  copyBot,
  GetAgentListParams,
  GetAgentListResponse,
} from '@/services/agent';
import DeleteBot from './components/delete-bot';
import { debounce } from 'lodash';
import RetractableInput from '@/components/ui/global/retract-table-input';
import useChat from '@/hooks/use-chat';
import useUserStore from '@/store/user-store';
import { jumpToLogin, downloadFileWithHeaders } from '@/utils/http';
import { getFixedUrl } from '@/components/workflow/utils';

import formSelect from '@/assets/imgs/main/icon_nav_dropdown.svg';
import agentOperationMore from '@/assets/imgs/main/agent-operation-more.svg';

import styles from './index.module.scss';
import useSpaceStore from '@/store/space-store';
import { getInputsType } from '@/services/flow';
import { handleShare } from '@/utils';
import MakeCreateModal from '@/components/make-creation';

function index() {
  const user = useUserStore((state: any) => state.user);
  const navigate = useNavigate();
  const { t } = useTranslation();
  const robotRef = useRef<HTMLDivElement | null>(null);
  const loading = useRef<boolean>(false);
  const [deleteModal, setDeleteModal] = useState(false);
  const [botDetail, setBotDetail] = useState<any>({});
  const [isHovered, setIsHovered] = useState<any>(null);
  const [searchValue, setSearchValue] = useState('');
  const [robots, setRobots] = useState<any>([]);
  const [pageIndex, setPageIndex] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [status, setStatus] = useState(0);
  const [sort, setSort] = useState('createTime');
  const [version, setVersion] = useState(0);
  const [operationId, setOperationId] = useState<string | null>(null);
  const { spaceId } = useSpaceStore();

  const { handleToChat } = useChat();
  const [makeModalVisible, setMakeModalVisible] = useState(false);

  /* statusMap为createList接口查询时参数
  NOTE: 原本为：1 审核中，2 已发布，3 审核不通过，4修改审核中， -9 || 0 未发布 => 后来改为已发布、未发布、发布中、审核不通过；
  最新版本改为(09.01): 不传 全部状态, [1,2,4] 已发布(含发布中), [0] 未发布, [3] 已下架(原审核不通过)
  回显的status为：已发布(1,2,4, 含发布中、修改审核中状态), 未发布(-9 || 0), 已下架(3)
   */
  const statusMap = [
    {
      label: t('agentPage.agentPage.allStatus'),
      value: null,
    },
    {
      label: t('agentPage.agentPage.published'),
      value: [1, 2, 4],
    },
    {
      label: t('agentPage.agentPage.unpublished'),
      value: [0],
    },
    // {
    //   label: t('agentPage.agentPage.publishing'),
    //   value: [1],
    // },
    {
      label: t('agentPage.agentPage.rejected'),
      value: [3],
    },
  ];

  useEffect(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      setOperationId(null);
    };
    window.addEventListener('click', handleOutsideClick);
    return () => window.removeEventListener('click', handleOutsideClick);
  }, []);

  useEffect(() => {
    getRobots();
  }, [status, sort, version, spaceId]);

  function getRobots(value?: string): void {
    loading.current = true;
    if (robotRef.current) {
      robotRef.current.scrollTop = 0;
    }

    const params: GetAgentListParams = {
      pageIndex: 1,
      pageSize: 200,
      botStatus: statusMap[status]?.value ?? null,
      sort,
      searchValue: value !== undefined ? value?.trim() : searchValue,
    };

    if (version !== 0) {
      params.version = version;
    }

    getAgentList(params)
      .then((data: GetAgentListResponse) => {
        setRobots(() => data.pageData);
        setPageIndex(() => 2);
        if (20 < data.totalCount) {
          setHasMore(true);
        } else {
          setHasMore(false);
        }
      })
      .finally(() => (loading.current = false));
  }

  // NOTE: 现未使用
  function handleScroll() {
    const element = robotRef.current;
    if (!element) return;

    const { scrollTop, scrollHeight, clientHeight } = element;

    if (
      scrollTop + clientHeight >= scrollHeight - 100 &&
      !loading.current &&
      hasMore
    ) {
      loading.current = true;
      moreRobots();
    }
  }

  function moreRobots() {
    const params: GetAgentListParams = {
      pageIndex: pageIndex,
      pageSize: 20,
      botStatus: statusMap[status]?.value ?? null,
      sort,
      searchValue: searchValue,
    };

    if (version !== 0) {
      params.version = version;
    }

    getAgentList(params)
      .then((data: GetAgentListResponse) => {
        setRobots([...robots, ...(data?.pageData ?? [])]);
        setPageIndex(pageIndex => pageIndex + 1);
        if (robots.length + 20 < data.totalCount) {
          setHasMore(true);
        } else {
          setHasMore(false);
        }
      })
      .finally(() => (loading.current = false));
  }

  const getRobotsDebounce = useCallback(
    debounce(e => {
      const value = e.target.value;
      setSearchValue(value);
      getRobots(value);
    }, 500),
    [searchValue]
  );

  function jumpChat(e: React.MouseEvent<HTMLDivElement>, id: string): void {
    e.stopPropagation();
    navigate(`/space/bot/${id}/chat`);
  }

  function jumpConfig(e: React.MouseEvent<HTMLDivElement>, id: string): void {
    e.stopPropagation();
    navigate('/space/config/' + id + '/base');
  }

  const handleBotFavorite = useCallback(
    throttle(robot => {
      const params = {
        botId: robot.id,
        favoriteFlag: robot?.isFavorite ? 1 : 0,
      };

      enableBotFavorite(params).then(data => {
        setRobots((robots: any[]) => {
          const currentBot = robots.find((item: any) => item.id === robot.id);
          currentBot.isFavorite = !currentBot.isFavorite;
          currentBot.favoriteCount = data;

          return [...robots];
        });
      });
    }, 1000),
    []
  );

  /** 复制操作 */
  const copyBotNow = useCallback(
    debounce((botId?: number) => {
      copyBot({ botId })
        .then(() => {
          message.success(t('agentPage.agentPage.copySuccess'));
          // if (searchValue) {
          //   setSearchValue('');
          // } else {
          //   getRobots();
          // }
          getRobots();
        })
        .catch(err => {
          console.error(err);
          err?.msg && message.error(err.msg);
        });
    }, 500),
    [status, searchValue]
  );

  //  分享智能体
  const handleShareAgent = async (
    botName: string,
    botId: number
  ): Promise<void> => {
    await handleShare(botName, botId, t);
  };

  return (
    <div className="w-full h-full overflow-hidden pb-6">
      {makeModalVisible && (
        <MakeCreateModal
          visible={makeModalVisible}
          onCancel={() => {
            setMakeModalVisible(false);
          }}
        />
      )}

      {deleteModal && (
        <DeleteBot
          botDetail={botDetail}
          setDeleteModal={setDeleteModal}
          type={true}
          initData={() => {
            getRobots();
          }}
        />
      )}

      <div className="pt-6 h-full flex flex-col overflow-hidden gap-6">
        <div
          className="flex justify-between mx-auto max-w-[1425px]"
          style={{
            width: 'calc(0.85 * (100% - 8px))',
          }}
        >
          <div className={styles.modelTitle}>
            {t('agentPage.agentPage.myAgents')}
          </div>
          <div
            className="flex items-center gap-4"
            style={{ marginRight: '4px' }}
          >
            <Select
              suffixIcon={<img src={formSelect} className="w-4 h-4 " />}
              className="search-select"
              style={{ height: 32, width: 160, marginRight: '8px' }}
              value={version}
              onChange={value => {
                setVersion(value);
                setPageIndex(1);
              }}
              options={[
                { label: t('agentPage.agentPage.allTypes'), value: 0 },
                { label: t('agentPage.agentPage.instructionType'), value: 1 },
                { label: t('agentPage.agentPage.workflowType'), value: 3 },
              ]}
            />
            <Select
              suffixIcon={<img src={formSelect} className="w-4 h-4 " />}
              className="search-select"
              style={{ height: 32, width: 160, marginRight: '8px' }}
              value={sort}
              onChange={value => {
                setSort(value);
                setPageIndex(1);
              }}
              options={[
                {
                  label: t('agentPage.agentPage.sortByCreateTime'),
                  value: 'createTime',
                },
                {
                  label: t('agentPage.agentPage.sortByUpdateTime'),
                  value: 'updateTime',
                },
              ]}
            />
            <Select
              suffixIcon={<img src={formSelect} className="w-4 h-4 " />}
              className="search-select"
              style={{ height: 32, width: 160, marginRight: '8px' }}
              value={status}
              onChange={value => {
                setStatus(value);
                setPageIndex(1);
              }}
              options={[
                { label: t('agentPage.agentPage.allStatus'), value: 0 },
                { label: t('agentPage.agentPage.published'), value: 1 },
                { label: t('agentPage.agentPage.unpublished'), value: 2 },
              ]}
            />
            <RetractableInput
              restrictFirstChar={true}
              onChange={getRobotsDebounce}
            />
          </div>
        </div>

        <div className="w-full flex-1 overflow-scroll relative">
          <div
            className="w-full h-full mx-auto max-w-[1425px]"
            style={{
              width: '85%',
            }}
            ref={robotRef}
            onScroll={handleScroll}
          >
            <div className="grid lg:grid-cols-3 xl:grid-cols-3 2xl:grid-cols-3 3xl:grid-cols-3 gap-6 items-end">
              <div
                className={`common-card-add-container relative ${
                  isHovered === null
                    ? ''
                    : isHovered
                      ? 'knowledge-no-hover'
                      : ' knowledge-hover'
                }`}
                onMouseLeave={e => {
                  setIsHovered(true);
                }}
                onMouseEnter={e => {
                  setIsHovered(false);
                }}
                onClick={() => {
                  if (!user?.login && !user?.uid) {
                    return jumpToLogin();
                  }
                  setMakeModalVisible(true);
                }}
              >
                <div className="color-mask"></div>
                <div className="knowledge-card-add flex flex-col w-full">
                  <div className="w-full flex justify-between">
                    <span className="agent-icon"></span>
                    <span className="add-icon"></span>
                  </div>
                  <div
                    className="mt-4 font-semibold add-name"
                    style={{ fontSize: 22 }}
                  >
                    {t('agentPage.agentPage.createNewAgent')}
                  </div>
                </div>
              </div>
              {robots?.map((k: any) => (
                <div
                  className={`common-card-item group h-[192px] ${styles.angentItemBox}`}
                  key={k.botId}
                  onClick={() => {
                    k.version === 1
                      ? navigate(`/space/config/base?botId=${k?.botId}`)
                      : navigate(
                          `/work_flow/${k?.maasId}/arrange?botId=${k?.botId}`
                        );
                  }}
                >
                  <div className="px-6">
                    <div className="flex items-start gap-6 overflow-hidden">
                      <span className="w-12 h-12 flex items-center justify-center rounded-lg flex-shrink-0">
                        <img
                          src={k.avatar}
                          className="w-[48px] h-[48px] rounded-[12px]"
                          alt=""
                        />
                      </span>
                      <div className="flex flex-col gap-2 overflow-hidden">
                        <div
                          className="flex-1 text-overflow font-medium text-xl title-color title-size"
                          title={k.botName}
                        >
                          {k.botName}
                        </div>
                        <div
                          className="text-[#7F7F7F] text-[14px] whitespace-nowrap overflow-hidden text-ellipsis h-8 w-full"
                          title={k.botDesc}
                        >
                          {k.botDesc}
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="flex ml-24 gap-4">
                    <div className={styles.angentType}>
                      {k.version === 1 ? '智能体' : '工作流'}
                    </div>
                  </div>

                  <div
                    className="flex justify-between items-center mt-3"
                    style={{
                      padding: '17px 24px 0 24px',
                      borderTop: '1px dashed #e2e8ff',
                      scrollbarWidth: 'none', // 隐藏滚动条
                      msOverflowStyle: 'none', // IE/Edge隐藏滚动条
                    }}
                  >
                    <span className="text-[#7F7F7F] text-xs go-setting flex items-center">
                      <span className="whitespace-nowrap">
                        {t('agentPage.agentPage.goToEdit')}
                      </span>
                      <span className="setting-icon setting-act"></span>
                    </span>
                    <div className="flex items-center text-desc flex-1 max-w-[210px] justify-between">
                      <div
                        className="card-chat cursor-pointer flex items-center"
                        onClick={e => {
                          e.stopPropagation();
                          if (k.version === 3) {
                            getInputsType({ botId: k.botId }).then(
                              (res: any) => {
                                // 合并不支持对话的条件
                                if (
                                  (res.length === 2 &&
                                    res[1].fileType === 'file' &&
                                    res[1].schema.type === 'array-string') ||
                                  (res.length === 2 &&
                                    res[1].fileType !== 'file') ||
                                  res.length > 2
                                ) {
                                  return message.info(
                                    t('agentPage.agentPage.notSupported')
                                  );
                                }
                                handleToChat(k.botId);
                              }
                            );
                          } else {
                            handleToChat(k.botId);
                          }
                        }}
                      >
                        <span
                          className={`chat-icon chat-act ${styles.only_css}`}
                        ></span>
                        <span className="ml-1 whitespace-nowrap">
                          {t('agentPage.agentPage.chat')}
                        </span>
                      </div>
                      <div
                        className="card-chat cursor-pointer flex items-center"
                        onClick={e => {
                          e.stopPropagation();
                          handleShareAgent(k.botName, k.botId);
                        }}
                      >
                        <span
                          className={`share-icon ${styles.only_css}`}
                        ></span>
                        <span className="ml-1 whitespace-nowrap">
                          {t('agentPage.agentPage.share')}
                        </span>
                      </div>
                      <div
                        className="card-chat cursor-pointer flex items-center"
                        onClick={e => {
                          e.stopPropagation();
                          copyBotNow(k.botId);
                        }}
                      >
                        <span className={`copy-icon ${styles.only_css}`}></span>
                        <span className="ml-1 whitespace-nowrap">
                          {t('agentPage.agentPage.copy')}
                        </span>
                      </div>
                      {(![1, 4].includes(k?.botStatus) || k?.version === 3) && (
                        <div
                          className="w-6 h-6 bg-[#F2F5FE] rounded flex items-center justify-center relative"
                          onClick={e => {
                            e.stopPropagation();
                            if (operationId === k.botId) {
                              setOperationId(null);
                            } else {
                              setOperationId(k.botId);
                            }
                          }}
                          onMouseEnter={e => {
                            e.stopPropagation();
                            setOperationId(k.botId);
                          }}
                        >
                          <img
                            src={agentOperationMore}
                            className="w-[14px] h-[14px]"
                            alt=""
                          />
                          {operationId === k.botId && (
                            <div
                              className="absolute top-[28px] right-0 bg-white rounded p-1 shadow-md flex flex-col gap-1 w-[48px]"
                              style={{
                                zIndex: 1,
                              }}
                              onMouseLeave={e => {
                                e.stopPropagation();
                                setOperationId(null);
                              }}
                            >
                              {k?.version === 3 && (
                                <span
                                  className="p-1 rounded hover:bg-[#F2F5FE] block"
                                  onClick={e => {
                                    e?.stopPropagation();
                                    e.preventDefault();
                                    setOperationId(null);
                                    downloadFileWithHeaders(
                                      getFixedUrl(
                                        `/workflow/export/${k?.maasId}`
                                      ),
                                      `${k?.botName}.yml`
                                    );
                                  }}
                                >
                                  {t('agentPage.agentPage.export')}
                                </span>
                              )}
                              {![1, 4].includes(k?.botStatus) && (
                                <div
                                  className="p-1 rounded hover:bg-[#F2F5FE] text-[#F74E43]"
                                  onClick={e => {
                                    e.stopPropagation();
                                    setBotDetail(k);
                                    setDeleteModal(true);
                                    setOperationId(null);
                                  }}
                                >
                                  {t('agentPage.agentPage.delete')}
                                </div>
                              )}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default memo(index);
