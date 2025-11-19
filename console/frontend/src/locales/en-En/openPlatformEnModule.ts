import agentPage from './openPlatform-En/agentPage';
import prompt from './openPlatform-En/prompt';
import promption from './openPlatform-En/promption';
import shareModal from './openPlatform-En/shareModal';
import chatPage from './openPlatform-En/chatPage';
import commonModal from './openPlatform-En/commonModal';
// 导入其他模块
import global from './openPlatform-En/global';
import feedback1 from './openPlatform-En/feedback';
import orderManagement from './openPlatform-En/orderManagement';
import comboContrastModal from './openPlatform-En/comboContrastModal';
import systemMessage from './openPlatform-En/systemMessage';
import createAgent1 from './openPlatform-En/createAgent';
import configBase from './openPlatform-En/configBase';
import loginModal from './openPlatform-En/loginModal';

/** ## 开放平台的翻译配置 -- en
 * @description 注意模块名称不要跟星辰的重复
 */
export default {
  global,
  feedback1,
  orderManagement,
  comboContrastModal,
  systemMessage,
  createAgent1,
  configBase,
  // 添加其他模块
  agentPage,
  ...prompt,
  promption,
  shareModal,
  chatPage,
  commonModal,
  loginModal,
};
