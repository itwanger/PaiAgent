import styles from './index.module.scss';
import personalCenterIcon from '@/assets/imgs/sidebar/person-center.svg';
import logoutIcon from '@/assets/imgs/sidebar/logout.svg';
import classNames from 'classnames';
import { useTranslation } from 'react-i18next';
import { handleLogout } from '@/utils/auth';
import LanguageSwitcher from '@/components/language-switcher';

const ControlModal = ({
  onClose,
  setIsPersonCenterOpen,
}: {
  onClose?: () => void;
  isPersonCenterOpen: boolean;
  setIsPersonCenterOpen: (visible: boolean) => void;
}) => {
  const { t } = useTranslation();

  //个人中心点击
  const handlePersonalCenter = () => {
    setIsPersonCenterOpen(true);
    onClose?.();
  };

  return (
    <div className={styles.control_modal}>
      <div className={styles.content}>
        <div className={styles.content_item} onClick={handlePersonalCenter}>
          <img src={personalCenterIcon} alt="" />
          <div>{t('sidebar.personalCenter')}</div>
        </div>

        <div className={styles.content_item}>
          <LanguageSwitcher />
        </div>

        <div
          className={classNames(styles.content_item, styles.logout)}
          onClick={handleLogout}
        >
          <img src={logoutIcon} alt="" />
          <div>{t('sidebar.logout')}</div>
        </div>
      </div>
    </div>
  );
};

export default ControlModal;
