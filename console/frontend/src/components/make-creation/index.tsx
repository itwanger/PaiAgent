import React, { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Modal, message, Spin } from 'antd';
import { submitBotBaseInfo } from '@/services/spark-common';
import { useTranslation } from 'react-i18next';
import WorkflowImportModal from './components/WorkflowImportModal';
import { ImportOutlined, PlusOutlined } from '@ant-design/icons';

import styles from './index.module.scss';

interface MakeCreateModalProps {
  visible: boolean;
  onCancel: () => void;
}

const MakeCreateModal: React.FC<MakeCreateModalProps> = ({
  visible,
  onCancel,
}) => {
  const { t } = useTranslation();
  const [workflowImportModalVisible, setWorkflowImportModalVisible] =
    useState(false);

  const navigate = useNavigate();
  const [addAgentTemplateLoading, setAddAgentTemplateLoading] = useState(false);

  const addAgentTemplate = async () => {
    setAddAgentTemplateLoading(true);
    const req: any = {
      name: t('createAgent1.commonCustom') + Date.now(),
      botType: 0,
      avatar:
        'https://oss-beijing-m8.openstorage.cn/SparkBotProd/icon/common/emojiitem_00_10@2x.png',
      botDesc: '',
      botId: null,
      inputExample: ['', '', ''],
    };
    await submitBotBaseInfo(req)
      .then((res: any) => {
        navigate(`/work_flow/${res.maasId}/arrange`);
      })
      .catch(e => {
        message.error(e?.message || '创建失败');
      });
    setAddAgentTemplateLoading(false);
  };

  return (
    <div className={styles.create_modal}>
      {workflowImportModalVisible && (
        <WorkflowImportModal
          setWorkflowImportModalVisible={setWorkflowImportModalVisible}
        />
      )}
      <Modal
        open={visible}
        getContainer={false}
        width={'auto'}
        footer={false}
        centered
        onCancel={onCancel}
      >
        <Spin style={{ maxHeight: '654px' }} spinning={addAgentTemplateLoading}>
          <div className={styles.create_modal_wrap}>
            <div className={styles.wrapper_title}>
              <div className={styles.title_left}>
                <span style={{ fontWeight: 600 }}>
                  {t('createAgent1.workflowCreationTitle')}
                </span>
              </div>
            </div>
            <div className={styles.wrapper_container}>
              <div className={styles.wrapper_container_agentType}>
                <div className={styles.wrapper_agentType_content}>
                  <div
                    onClick={() => addAgentTemplate()}
                    className={`${styles.wrapper_agentType_Type} ${styles.wrapper_agentType_Type_only_hover}`}
                  >
                    <div
                      className={styles.iconBox}
                      style={{
                        width: '48px',
                        height: '48px',
                        backgroundColor: '#275EFF',
                        borderRadius: '50%',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        marginBottom: '21px',
                      }}
                    >
                      <PlusOutlined
                        style={{
                          fontSize: '20px',
                          color: 'white',
                        }}
                      />
                    </div>
                    <div
                      className={styles.iconTitle}
                      style={{
                        fontSize: '16px',
                        fontWeight: 'normal',
                        lineHeight: '24px',
                        color: '#000000',
                      }}
                    >
                      {t('createAgent1.customCreation')}
                    </div>
                  </div>

                  <div
                    onClick={() => setWorkflowImportModalVisible(true)}
                    className={`${styles.wrapper_agentType_Type} ${styles.wrapper_agentType_Type_only_hover}`}
                  >
                    <div
                      className={styles.iconBox}
                      style={{
                        width: '48px',
                        height: '48px',
                        backgroundColor: '#275EFF',
                        borderRadius: '50%',
                        display: 'flex',
                        justifyContent: 'center',
                        alignItems: 'center',
                        marginBottom: '21px',
                      }}
                    >
                      <ImportOutlined
                        style={{
                          fontSize: '20px',
                          color: 'white',
                        }}
                      />
                    </div>
                    <div
                      className={styles.iconTitle}
                      style={{
                        fontSize: '16px',
                        fontWeight: 'normal',
                        lineHeight: '24px',
                        color: '#000000',
                      }}
                    >
                      {t('createAgent1.importWorkflow')}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </Spin>
      </Modal>
    </div>
  );
};

export default MakeCreateModal;
