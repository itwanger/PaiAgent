import qs from 'qs';
import { message } from 'antd';
import { localeConfig } from '@/locales/localeConfig';
import { getLanguageCode } from '@/utils/http';
import { v4 as uuid } from 'uuid';
import clsx, { ClassValue } from 'clsx';
import {
  ChatHistoryResponse,
  MessageListType,
  SourceInfoItem,
  ToolItemUnion,
  WebSearchOutput,
  UploadFileInfo,
} from '@/types/chat';
import Compressor from 'compressorjs';
import { getShareAgentKey } from '@/services/chat';
import { fileIconConfig } from '@/config/file-icon-config';
// 将对象转换为URL参数字符串
const objectToQueryString = (params: Record<string, any>): string => {
  if (!params || Object.keys(params).length === 0) {
    return '';
  }
  return '?' + qs.stringify(params);
};

// 图片转Base64的函数
const imageToBase64 = (
  imageUrl: string,
  isMounted: () => boolean,
  onPlaceholder: () => void
): Promise<string> => {
  return new Promise((resolve, reject) => {
    const img = new window.Image();
    const canvas = document.createElement('canvas');
    const ctx = canvas.getContext('2d');

    const timeout = window.setTimeout((): void => {
      if (isMounted()) {
        onPlaceholder();
      }
      reject(new Error('Image loading timeout'));
    }, 10000);

    img.onload = (): void => {
      try {
        window.clearTimeout(timeout);
        canvas.width = img.width;
        canvas.height = img.height;

        if (ctx) {
          ctx.drawImage(img, 0, 0);
          const base64 = canvas.toDataURL('image/png', 0.9);
          resolve(base64);
        } else {
          if (isMounted()) {
            onPlaceholder();
          }
          reject(new Error('Canvas context not available'));
        }
      } catch (error) {
        if (isMounted()) {
          onPlaceholder();
        }
        reject(error);
      }
    };

    img.onerror = (): void => {
      window.clearTimeout(timeout);
      if (isMounted()) {
        onPlaceholder();
      }
      reject(new Error('Image loading failed'));
    };

    img.crossOrigin = 'anonymous';
    const timestamp = Date.now();
    const random = Math.random().toString(36).substring(7);
    const separator = imageUrl.includes('?') ? '&' : '?';
    const noCacheUrl = `${imageUrl}${separator}_t=${timestamp}&_r=${random}`;
    img.src = noCacheUrl;
  });
};

/**
 * 复制文本
 * @param options
 */
const copyText = async (options: {
  text: string;
  origin?: boolean;
  successText?: string;
}) => {
  const languageCode = getLanguageCode();
  const props = { origin: true, ...options };
  const typeList = [
    'metadata',
    'plugin_debug_param',
    'plugin_debug_response',
    'plugin_cards',
    'plugin_chat_file',
  ];
  const regex = new RegExp('```(' + typeList.join('|') + ')\n(.*?)\n```', 'g');

  // 创建一个临时 div 来解码 HTML 实体
  const decodedText = props.text?.replace(regex, '');
  try {
    // 使用现代的 Clipboard API
    await navigator.clipboard.writeText(decodedText);

    if (!props.successText) {
      message.info(localeConfig?.[languageCode]?.copyDone);
    } else {
      message.info(props.successText);
    }
  } catch (err) {
    // 降级方案：如果 Clipboard API 不可用，使用传统方法
    const textarea = document.createElement('textarea');
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    textarea.value = decodedText;
    document.body.appendChild(textarea);
    textarea.select();
    try {
      document.execCommand('copy');
      if (!props.successText) {
        message.info(localeConfig?.[languageCode]?.copyDone);
      } else {
        message.info(props.successText);
      }
    } catch (e) {
      message.error('复制失败');
    } finally {
      document.body.removeChild(textarea);
    }
  }
};

function getCookie(cookieName: string): string {
  const name = cookieName + '=';
  const decodedCookie = decodeURIComponent(document.cookie);
  const cookieArray = decodedCookie.split(';');

  for (let i = 0; i < cookieArray.length; i++) {
    let cookie = cookieArray[i];
    if (!cookie) continue;
    while (cookie.charAt(0) === ' ') {
      cookie = cookie.substring(1);
    }
    if (cookie.indexOf(name) === 0) {
      return cookie.substring(name.length, cookie.length);
    }
  }

  return '';
}

/**
 * 判断字符串是否为 JSON
 */
const isJSON = (str: string): boolean => {
  try {
    const obj = JSON.parse(str);
    return typeof obj === 'object' && obj !== null;
  } catch {
    return false;
  }
};

/**
 * 文件类型获取
 */
const fileType = (file: {
  isFile: boolean;
  fileInfoV2?: { type?: string };
}): string => {
  return file.isFile ? (file.fileInfoV2?.type ?? 'unknown') : 'folder';
};

/**
 * 修改 chunks
 */
function modifyChunks(
  chunks: Array<{
    content: {
      content?: string;
      knowledge?: string;
      references?: Record<
        string,
        { format?: string; link?: string; content?: string }
      >;
      auditSuggest?: unknown;
      auditDetail?: Array<{ category_description: string }>;
    };
    [key: string]: unknown;
  }>
): Record<string, unknown>[] {
  return chunks.map(item => ({
    ...item,
    markdownContent: modifyContent(item.content),
    content: item.content?.content || item.content?.knowledge,
    auditSuggest: item.content?.auditSuggest,
    auditDetail: item.content?.auditDetail
      ?.map((d: Record<string, string>) => d['category_description'])
      ?.join(','),
  }));
}

/**
 * 修改内容，替换引用
 */
function modifyContent(chunk: {
  content?: string;
  knowledge?: string;
  references?: Record<
    string,
    { format?: string; link?: string; content?: string }
  >;
}): string {
  const regex = /[{<]unused.+?[>}]/g;
  let content = chunk?.content || chunk?.knowledge || '';
  const matches = content.match(regex);
  const references = chunk.references ?? {};

  if (matches && matches.length) {
    matches.forEach(item => {
      const refKey = item.slice(1, -1);
      const imageInfo = references[refKey];
      if (imageInfo?.format === 'image') {
        content = content.replace(item, `![image](${imageInfo.link})`);
      } else if (imageInfo?.content) {
        content = content.replace(item, imageInfo.content);
      }
    });
  }
  return content;
}

/**
 * className 拼接
 */
function cn(...inputs: ClassValue[]): string {
  return clsx(inputs);
}

/**
 * 根据类型生成默认值
 */
const generateTypeDefault = (
  type: string
): string | boolean | number | unknown[] => {
  if (type === 'string') return '';
  if (type === 'integer') return 0;
  if (type === 'boolean') return false;
  if (type === 'number') return 0;
  return [];
};

/**
 * 根据文件后缀生成类型
 */
const generateType = (suffix: string): string | undefined => {
  if (['pdf'].includes(suffix)) return 'pdf';
  if (['doc', 'docx'].includes(suffix)) return 'doc';
  if (['jpg', 'jpeg', 'png', 'bmp'].includes(suffix)) return 'image';
  if (['txt'].includes(suffix)) return 'txt';
  if (['md'].includes(suffix)) return 'md';
  if (['ppt', 'pptx'].includes(suffix)) return 'ppt';
  if (['xlsx', 'xls', 'csv'].includes(suffix)) return 'excel';
  if (['html'].includes(suffix)) return 'html';
  return undefined;
};

/**
 * 类型判断工具函数
 */
const getValueType = (value: unknown): string => {
  if (value === null) return 'string';
  if (Array.isArray(value)) return 'array';
  if (typeof value === 'number') {
    return Number.isInteger(value) ? 'integer' : 'number';
  }
  return typeof value;
};

/**
 * JSON 转换成数组结构
 */
const transformJsonToArray = (
  data: Record<string, unknown>
): Record<string, unknown>[] => {
  const processData = (
    input: unknown,
    parentType: string,
    isTopLevel = false
  ): Record<string, unknown> => {
    const baseAttributes = {
      description: '',
      from: 2,
      location: 'query',
      open: true,
      required: true,
      ...(!isTopLevel && parentType === 'array' && { arraySon: true }),
    };

    if (Array.isArray(input)) {
      const children = input.map(item => {
        const type = getValueType(item);

        if (type !== 'object' && type !== 'array') {
          return {
            id: uuid(),
            name: '[Array Item]',
            default: item,
            type,
            arraySon: true,
            fatherType: 'array',
            ...baseAttributes,
          };
        }

        return {
          id: uuid(),
          name: '[Array Item]',
          ...processData(item, 'array'),
          arraySon: true,
          fatherType: 'array',
          ...baseAttributes,
        };
      });

      return {
        id: uuid(),
        default: input,
        type: 'array',
        children,
        ...(!isTopLevel && { fatherType: parentType }),
        ...baseAttributes,
      };
    }

    if (typeof input === 'object' && input !== null) {
      const children = Object.entries(input).map(([key, value]) => {
        const type = getValueType(value);

        if (type === 'object' || type === 'array') {
          return {
            id: uuid(),
            name: key,
            ...processData(value, 'object'),
            fatherType: 'object',
            ...baseAttributes,
          };
        }

        return {
          id: uuid(),
          name: key,
          default: value,
          type,
          fatherType: 'object',
          ...baseAttributes,
        };
      });

      return {
        id: uuid(),
        type: 'object',
        children,
        ...(!isTopLevel && { fatherType: parentType }),
        ...baseAttributes,
      };
    }

    return {
      id: uuid(),
      default: input,
      type: getValueType(input),
      ...(!isTopLevel && { fatherType: parentType }),
      ...baseAttributes,
    };
  };

  if (typeof data !== 'object' || data === null || Array.isArray(data)) {
    return [];
  }

  return Object.entries(data).map(([key, value]) => {
    const type = getValueType(value);

    if (type === 'object' || type === 'array') {
      return {
        id: uuid(),
        name: key,
        ...processData(value, type, true),
        description: '',
        from: 2,
        location: 'query',
        open: true,
        required: true,
        ...(type !== 'object' && { startDisabled: false }),
      };
    }

    return {
      id: uuid(),
      name: key,
      default: value,
      type,
      description: '',
      from: 2,
      location: 'query',
      open: true,
      required: true,
      startDisabled: !value,
    };
  });
};

/**
 * 提取所有 ID
 */
const extractAllIdsOptimized = (data: unknown): string[] => {
  const ids: string[] = [];
  const stack = Array.isArray(data) ? [...data] : [data];

  while (stack.length) {
    const node = stack.pop();
    if (node.type === 'array' || node.type === 'object') {
      ids.push(node.id);
    }
    if (node.children) {
      stack.push(...node.children);
    }
  }

  return ids;
};

type PrimitiveType = 'string' | 'number' | 'boolean' | 'null' | 'undefined';
type ComplexType = 'object' | 'array';
type ValueType = PrimitiveType | ComplexType;

interface FormattedItem {
  id: string;
  name: string;
  description: string;
  type: ValueType;
  open: boolean;
  nameErrMsg: string;
  descriptionErrMsg: string;
  fatherType?: ValueType;
  arraySon?: boolean;
  children?: FormattedItem[];
}

/**
 * 把原始数据转换成需要的格式
 */
const convertToDesiredFormat = (
  data: Record<string, unknown>,
  parentType: ValueType | null = null,
  isArraySon = false
): FormattedItem[] => {
  return Object.entries(data).map(([key, value]) => {
    const type = getValueType(value);
    const isComplexType = type === 'object' || type === 'array';

    const baseItem: FormattedItem = {
      id: uuid(),
      name: key,
      description: isComplexType ? '' : value === null ? '' : String(value),
      type: type as ValueType,
      open: true,
      nameErrMsg: '',
      descriptionErrMsg: '',
    };

    if (parentType === 'array' || isArraySon) {
      baseItem.fatherType = parentType ?? undefined;
      baseItem.arraySon = true;
    }

    if (isComplexType) {
      if (type === 'array' && Array.isArray(value)) {
        baseItem.children = value.map((item): FormattedItem => {
          const itemType: ValueType =
            typeof item === 'object' && item !== null && !Array.isArray(item)
              ? 'object'
              : (typeof item as ValueType);

          return {
            id: uuid(),
            name: '[Array Item]',
            description: typeof item === 'object' ? '' : String(item),
            type: itemType,
            open: true,
            fatherType: 'array',
            arraySon: true,
            descriptionErrMsg: '',
            nameErrMsg: '',
            children:
              typeof item === 'object' && item !== null
                ? convertToDesiredFormat(
                    item as Record<string, unknown>,
                    'array',
                    true
                  )
                : undefined,
          };
        });
      } else if (
        type === 'object' &&
        typeof value === 'object' &&
        value !== null
      ) {
        baseItem.children = convertToDesiredFormat(
          value as Record<string, unknown>,
          'object',
          isArraySon
        );
      }
    }

    return baseItem;
  });
};

/**
 * 格式化对话历史为消息列表
 */
const formatHistoryToMessages = (chatHistoryList: ChatHistoryResponse[]) => {
  if (!chatHistoryList || chatHistoryList.length === 0) return [];

  const formattedMessages: MessageListType[] = [];
  chatHistoryList.forEach((history, index) => {
    if (history.historyList && Array.isArray(history.historyList)) {
      history.historyList.forEach((msg: any) => {
        const formattedMessage = {
          ...msg,
          reqType: msg.reqId ? 'BOT' : 'USER',
        };
        formattedMessages.push(formattedMessage);
      });
      if (index < chatHistoryList.length - 1) {
        formattedMessages.push({
          id: new Date().getTime(),
          reqType: 'START',
          message: '全新的开始',
        });
      }
    }
  });

  return formattedMessages;
};

//转换溯源数据
const getTraceList = (traceSource: string): SourceInfoItem[] => {
  try {
    const traceSourceObj: ToolItemUnion[] = JSON.parse(traceSource);
    const sourceInfoList: SourceInfoItem[] = [];

    traceSourceObj?.forEach((toolItem: ToolItemUnion) => {
      if (toolItem.web_search?.outputs) {
        toolItem.web_search.outputs.forEach((output: WebSearchOutput) => {
          sourceInfoList.push({
            index: output.index,
            url: output.url,
            title: output.title,
          });
        });
      }
    });

    return sourceInfoList;
  } catch (e) {
    console.error('Failed to parse traceSource:', e);
    return [];
  }
};

/**
 * 压缩图片工具
 * @param imageFile 图像文件
 * @param quality 质量，默认为0.6
 * @param convertSize 其文件类型包含在此列表中且其文件大小超过 ConvertSize 值的文件将转换为 JPEG， 默认值为3MB
 * @returns
 */
const compressImage = (
  imageFile: any,
  quality = 0.6,
  convertSize = 3000000
) => {
  return new Promise((resolve, reject) => {
    new Compressor(imageFile, {
      quality,
      convertSize,
      success(result) {
        if (result.size > 3 * 1024 * 1024) {
          reject('图片太大，请换个试试');
          return;
        }
        const newFile: any = new File([result], imageFile?.name, {
          type: result.type, // 设置图像的 MIME 类型，保持原始格式
          lastModified: imageFile?.lastModified, // 保持原始的修改时间
        });

        ((newFile.uid = imageFile?.uid), // 保持原始的 uid
          resolve(newFile));
      },
      error(err) {
        console.log(err.message);
        reject(err);
      },
    });
  });
};

/**
 * 分享智能体
 * @param botInfo 智能体信息
 * @param t 国际化函数
 * @returns Promise<void>
 */
export const handleShare = async (
  botName: string,
  botId: number,
  t: (key: string, options?: any) => string
): Promise<void> => {
  try {
    // 1. 获取分享key
    const res = await getShareAgentKey({
      relateType: 0,
      relateId: botId,
    });

    const shareUrl = t('shareModal.shareOriginModal.shareText', {
      botName: botName,
      origin: window.location.origin,
      botId: botId,
      shareKey: res.shareAgentKey,
    });

    // 2. 复制分享链接
    copyText({ text: shareUrl, successText: '分享链接已复制到剪贴板' });
  } catch (err) {
    message.error((err as Error)?.message || '分享失败，请稍后再试~');
  }
};

/**
 * 根据文件类型设置文件图标
 */
const getFileIcon = (file: UploadFileInfo, isLoading?: boolean) => {
  const extension = file?.fileName?.split('.')?.pop()?.toLowerCase();
  if (isLoading) {
    return fileIconConfig.loading;
  }
  // 遍历所有分类查找文件扩展名
  for (const category of Object.values(fileIconConfig)) {
    if (category[extension as keyof typeof category]) {
      return category[extension as keyof typeof category];
    }
  }
  return fileIconConfig.default;
};

/**
 * 格式化文件大小显示
 */
const formatFileSize = (bytes: number | string): string => {
  if (typeof bytes === 'string') {
    return bytes;
  }
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
};

/**
 * 获取状态显示文本
 */
const getStatusText = (file: UploadFileInfo): string => {
  switch (file.status) {
    case 'pending':
      return '等待中...';
    case 'uploading':
      return `上传中 ${file.progress || 0}%`;
    case 'completed':
      return formatFileSize(file.fileSize);
    case 'error':
      return `${file.error || '未知错误'}`;
    default:
      return file.fileSize.toString();
  }
};

export {
  objectToQueryString,
  imageToBase64,
  copyText,
  getCookie,
  isJSON,
  fileType,
  modifyChunks,
  modifyContent,
  cn,
  generateTypeDefault,
  generateType,
  transformJsonToArray,
  extractAllIdsOptimized,
  convertToDesiredFormat,
  formatHistoryToMessages,
  getTraceList,
  compressImage,
  getFileIcon,
  formatFileSize,
  getStatusText,
};
