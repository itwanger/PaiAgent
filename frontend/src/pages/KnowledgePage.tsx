import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, List, Modal, Popconfirm, Progress, Space, Table, Tabs, Tag, Upload, message } from 'antd';
import { ArrowLeftOutlined, CloudUploadOutlined, DatabaseOutlined, FileTextOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  KnowledgeBase,
  KnowledgeChunkPreview,
  KnowledgeDocument,
  KnowledgeSearchResult,
  createKnowledgeBase,
  deleteKnowledgeBase,
  getKnowledgeBase,
  getKnowledgeBases,
  importKnowledgeText,
  indexKnowledgeDocument,
  previewKnowledgeChunks,
  searchKnowledgeBase,
  uploadKnowledgeTextFile,
} from '../api/knowledge';

const statusColor: Record<string, string> = {
  DRAFT: 'default',
  IMPORTED: 'processing',
  CHUNKED: 'cyan',
  QUEUED: 'processing',
  RUNNING: 'processing',
  READY: 'success',
  SUCCESS: 'success',
  FAILED: 'error',
};

const statusText: Record<string, string> = {
  DRAFT: '草稿',
  IMPORTED: '已导入',
  CHUNKED: '已分片',
  QUEUED: '排队中',
  RUNNING: '索引中',
  READY: '可检索',
  SUCCESS: '完成',
  FAILED: '失败',
};

const KnowledgePage = () => {
  const navigate = useNavigate();
  const [bases, setBases] = useState<KnowledgeBase[]>([]);
  const [selectedBaseId, setSelectedBaseId] = useState<number | null>(null);
  const [selectedBase, setSelectedBase] = useState<KnowledgeBase | null>(null);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewChunks, setPreviewChunks] = useState<KnowledgeChunkPreview[]>([]);
  const [previewDocument, setPreviewDocument] = useState<KnowledgeDocument | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResult, setSearchResult] = useState<KnowledgeSearchResult | null>(null);
  const [createForm] = Form.useForm();
  const [importForm] = Form.useForm();

  useEffect(() => {
    loadBases();
  }, []);

  useEffect(() => {
    if (selectedBaseId) {
      loadBaseDetail(selectedBaseId);
    }
  }, [selectedBaseId]);

  useEffect(() => {
    if (!selectedBaseId || !selectedBase?.recentTasks?.some((task) => task.status === 'QUEUED' || task.status === 'RUNNING')) return;
    const timer = window.setInterval(() => {
      loadBaseDetail(selectedBaseId);
      loadBases();
    }, 1500);
    return () => window.clearInterval(timer);
  }, [selectedBaseId, selectedBase?.recentTasks]);

  const loadBases = async () => {
    setLoading(true);
    try {
      const result = await getKnowledgeBases();
      if (result.code === 200) {
        setBases(result.data);
        if (!selectedBaseId && result.data.length > 0) {
          setSelectedBaseId(result.data[0].id);
        }
      }
    } catch (error) {
      message.error(`加载知识库失败: ${error instanceof Error ? error.message : '网络错误'}`);
    } finally {
      setLoading(false);
    }
  };

  const loadBaseDetail = async (id: number) => {
    try {
      const result = await getKnowledgeBase(id);
      if (result.code === 200) {
        setSelectedBase(result.data);
      }
    } catch (error) {
      message.error(`加载知识库详情失败: ${error instanceof Error ? error.message : '网络错误'}`);
    }
  };

  const handleCreate = async () => {
    const values = await createForm.validateFields();
    const result = await createKnowledgeBase(values);
    if (result.code === 200) {
      message.success('知识库创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      await loadBases();
      setSelectedBaseId(result.data.id);
    }
  };

  const handleImportText = async () => {
    if (!selectedBaseId) return;
    const values = await importForm.validateFields();
    const result = await importKnowledgeText(selectedBaseId, values);
    if (result.code === 200) {
      message.success('文本已导入，索引任务已排队');
      setImportOpen(false);
      importForm.resetFields();
      await loadBaseDetail(selectedBaseId);
    }
  };

  const handleUploadFile = async () => {
    if (!selectedBaseId || !selectedFile) {
      message.warning('请选择 PDF、DOCX、TXT 或 MD 文件');
      return;
    }
    const result = await uploadKnowledgeTextFile(selectedBaseId, selectedFile);
    if (result.code === 200) {
      message.success('文件已导入，索引任务已排队');
      setImportOpen(false);
      setSelectedFile(null);
      await loadBaseDetail(selectedBaseId);
    }
  };

  const handlePreview = async (document: KnowledgeDocument) => {
    if (!selectedBaseId) return;
    const result = await previewKnowledgeChunks(selectedBaseId, document.id);
    if (result.code === 200) {
      setPreviewDocument(document);
      setPreviewChunks(result.data);
      setPreviewOpen(true);
    }
  };

  const handleIndex = async (document: KnowledgeDocument) => {
    if (!selectedBaseId) return;
    const result = await indexKnowledgeDocument(selectedBaseId, document.id);
    if (result.code === 200) {
      const task = result.data;
      if (task.status === 'FAILED') {
        message.error(task.errorMessage || '索引失败');
      } else {
        message.success(task.status === 'SUCCESS' ? '索引建立完成' : '重建任务已排队');
      }
      await loadBases();
      await loadBaseDetail(selectedBaseId);
    }
  };

  const handleSearch = async () => {
    if (!selectedBaseId || !searchQuery.trim()) {
      message.warning('请输入检索问题');
      return;
    }
    const result = await searchKnowledgeBase(selectedBaseId, {
      query: searchQuery,
      topK: 5,
      scoreThreshold: 0,
    });
    if (result.code === 200) {
      setSearchResult(result.data);
    }
  };

  const handleDeleteBase = async () => {
    if (!selectedBaseId) return;
    const result = await deleteKnowledgeBase(selectedBaseId);
    if (result.code === 200) {
      message.success('知识库已删除');
      setSelectedBase(null);
      setSelectedBaseId(null);
      await loadBases();
    }
  };

  const documentColumns = [
    {
      title: '文档',
      dataIndex: 'title',
      render: (title: string, record: KnowledgeDocument) => (
        <Space direction="vertical" size={2}>
          <span className="knowledge-doc-title">{title}</span>
          <span className="knowledge-muted">{record.sourceType} · {record.charCount || 0} 字符</span>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (status: string) => <Tag color={statusColor[status] || 'default'}>{statusText[status] || status}</Tag>,
    },
    {
      title: '操作',
      width: 220,
      render: (_: unknown, record: KnowledgeDocument) => (
        <Space>
          <Button size="small" onClick={() => handlePreview(record)}>分片预览</Button>
          <Button size="small" type="primary" onClick={() => handleIndex(record)}>建立索引</Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="knowledge-shell">
      <header className="knowledge-topbar">
        <div className="knowledge-title-group">
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/editor')}>工作流</Button>
          <div>
            <div className="knowledge-kicker">Resource</div>
            <h2>知识库</h2>
          </div>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建知识库
        </Button>
      </header>

      <div className="knowledge-layout">
        <aside className="knowledge-sidebar">
          <Input.Search placeholder="搜索知识库" allowClear />
          <List
            loading={loading}
            dataSource={bases}
            renderItem={(base) => (
              <List.Item
                className={`knowledge-base-item ${selectedBaseId === base.id ? 'is-active' : ''}`}
                onClick={() => setSelectedBaseId(base.id)}
              >
                <div className="knowledge-base-icon"><DatabaseOutlined /></div>
                <div className="knowledge-base-body">
                  <div className="knowledge-base-name">{base.name}</div>
                  <div className="knowledge-muted">{base.documentCount || 0} 文档 · {base.chunkCount || 0} 分片</div>
                </div>
                <Tag color={statusColor[base.status] || 'default'}>{statusText[base.status] || base.status}</Tag>
              </List.Item>
            )}
          />
        </aside>

        <main className="knowledge-main">
          {selectedBase ? (
            <>
              <section className="knowledge-detail-header">
                <div>
                  <h1>{selectedBase.name}</h1>
                  <p>{selectedBase.description || '暂无描述'}</p>
                </div>
                <Space>
                  <Button icon={<CloudUploadOutlined />} onClick={() => setImportOpen(true)}>导入数据</Button>
                  <Popconfirm title="确认删除该知识库？" onConfirm={handleDeleteBase}>
                    <Button danger>删除</Button>
                  </Popconfirm>
                </Space>
              </section>

              <section className="knowledge-metrics">
                <div><span>文档数</span><strong>{selectedBase.documentCount || 0}</strong></div>
                <div><span>分片数</span><strong>{selectedBase.chunkCount || 0}</strong></div>
                <div><span>字符数</span><strong>{selectedBase.charCount || 0}</strong></div>
                <div><span>切分策略</span><strong>{selectedBase.chunkSize} tokens</strong></div>
              </section>

              <Tabs
                items={[
                  {
                    key: 'documents',
                    label: '文档',
                    children: (
                      <Table
                        rowKey="id"
                        columns={documentColumns}
                        dataSource={selectedBase.documents || []}
                        pagination={false}
                      />
                    ),
                  },
                  {
                    key: 'search',
                    label: '检索测试',
                    children: (
                      <div className="knowledge-search-panel">
                        <Space.Compact className="knowledge-search-box">
                          <Input
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            onPressEnter={handleSearch}
                            placeholder="输入问题，测试召回效果"
                          />
                          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>检索</Button>
                        </Space.Compact>
                        {searchResult && (
                          <List
                            className="knowledge-search-results"
                            dataSource={searchResult.chunks || []}
                            renderItem={(chunk) => (
                              <List.Item>
                                <div>
                                  <div className="knowledge-doc-title">{chunk.title || `Chunk ${chunk.chunkId}`}</div>
                                  <div className="knowledge-muted">score {Number(chunk.score || 0).toFixed(3)}</div>
                                  <p>{chunk.content}</p>
                                </div>
                              </List.Item>
                            )}
                          />
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'tasks',
                    label: '索引任务',
                    children: (
                      <List
                        dataSource={selectedBase.recentTasks || []}
                        renderItem={(task) => (
                          <List.Item>
                            <Space direction="vertical" className="w-full">
                              <Space>
                                <Tag color={statusColor[task.status] || 'default'}>{statusText[task.status] || task.status}</Tag>
                                <span>文档 #{task.documentId}</span>
                                <span>{task.finishedChunks}/{task.totalChunks} 分片</span>
                              </Space>
                              <Progress percent={task.progress || 0} size="small" />
                              {task.errorMessage && <span className="knowledge-error">{task.errorMessage}</span>}
                            </Space>
                          </List.Item>
                        )}
                      />
                    ),
                  },
                ]}
              />
            </>
          ) : (
            <div className="knowledge-empty">
              <DatabaseOutlined />
              <p>暂无知识库，先新建一个用于 RAG 的知识库。</p>
            </div>
          )}
        </main>
      </div>

      <Modal title="新建知识库" open={createOpen} onOk={handleCreate} onCancel={() => setCreateOpen(false)} width={620}>
        <Form form={createForm} layout="vertical" initialValues={{
          chunkSize: 800,
          chunkOverlap: 0,
        }}>
          <Form.Item name="name" label="知识库名称" rules={[{ required: true, message: '请输入知识库名称' }]}>
            <Input maxLength={20} placeholder="例如：产品 FAQ" />
          </Form.Item>
          <Form.Item name="description" label="知识库描述">
            <Input.TextArea rows={3} maxLength={200} placeholder="说明内容范围、适用工作流和维护规则" />
          </Form.Item>
          <Space className="knowledge-form-row" align="start">
            <Form.Item name="chunkSize" label="分片长度（tokens）">
              <InputNumber min={100} max={4000} />
            </Form.Item>
          </Space>
        </Form>
      </Modal>

      <Modal title="导入数据" open={importOpen} onCancel={() => setImportOpen(false)} footer={null} width={720}>
        <Tabs
          items={[
            {
              key: 'text',
              label: '粘贴文本',
              children: (
                <Form form={importForm} layout="vertical">
                  <Form.Item name="title" label="标题">
                    <Input placeholder="未命名文本" />
                  </Form.Item>
                  <Form.Item name="tags" label="标签">
                    <Input placeholder="多个标签用逗号分隔" />
                  </Form.Item>
                  <Form.Item name="content" label="文本内容" rules={[{ required: true, message: '请输入文本内容' }]}>
                    <Input.TextArea rows={10} placeholder="粘贴 FAQ、说明文档、业务规则等内容" />
                  </Form.Item>
                  <Button type="primary" onClick={handleImportText}>导入文本</Button>
                </Form>
              ),
            },
            {
              key: 'file',
              label: '上传文件',
              children: (
                <div>
                  <Upload.Dragger
                    accept=".pdf,.docx,.txt,.md,.markdown"
                    beforeUpload={(file) => {
                      setSelectedFile(file);
                      return false;
                    }}
                    maxCount={1}
                  >
                    <p className="ant-upload-drag-icon"><FileTextOutlined /></p>
                    <p className="ant-upload-text">拖拽文本文件到这里，或点击选择文件</p>
                    <p className="ant-upload-hint">支持 PDF、DOCX、TXT、MD，单文件最大 10 MiB</p>
                  </Upload.Dragger>
                  <Button className="knowledge-upload-submit" type="primary" onClick={handleUploadFile}>
                    上传并导入
                  </Button>
                </div>
              ),
            },
          ]}
        />
      </Modal>

      <Modal title={`分片预览 ${previewDocument?.title || ''}`} open={previewOpen} onCancel={() => setPreviewOpen(false)} footer={null} width={820}>
        <List
          dataSource={previewChunks}
          renderItem={(chunk) => (
            <List.Item>
              <div className="knowledge-chunk-preview">
                <div className="knowledge-muted">#{chunk.chunkIndex + 1} · {chunk.charCount} 字符</div>
                <p>{chunk.content}</p>
              </div>
            </List.Item>
          )}
        />
      </Modal>
    </div>
  );
};

export default KnowledgePage;
