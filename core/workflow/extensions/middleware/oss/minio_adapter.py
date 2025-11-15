"""
MinIO 客户端适配器 - 使用 minio 库替代 boto3
解决本地 MinIO 不兼容 boto3 的问题
"""

from minio import Minio
from minio.error import S3Error
from loguru import logger

from workflow.extensions.middleware.base import Service
from workflow.extensions.middleware.oss.base import BaseOSSService


class MinIOService(BaseOSSService, Service):
    """
    使用 minio 库的 S3 兼容存储服务实现
    
    相比 boto3 的优势:
    - 对旧版本 MinIO 有更好的兼容性
    - 不需要复杂的签名配置
    - API 更简洁直观
    """

    def __init__(
        self,
        endpoint: str,
        access_key_id: str,
        access_key_secret: str,
        bucket_name: str,
        oss_download_host: str,
    ):
        """
        初始化 MinIO 客户端
        
        :param endpoint: MinIO 服务端点 (格式: http://host:port)
        :param access_key_id: 访问密钥 ID
        :param access_key_secret: 访问密钥
        :param bucket_name: 默认 bucket 名称
        :param oss_download_host: 下载链接的主机地址
        """
        # 移除 http:// 或 https:// 前缀
        endpoint_clean = endpoint.replace('http://', '').replace('https://', '')
        
        # 判断是否使用 HTTPS
        secure = endpoint.startswith('https://')
        
        logger.info(f"🔧 初始化 MinIO 客户端: endpoint={endpoint_clean}, secure={secure}")
        
        self.client = Minio(
            endpoint_clean,
            access_key=access_key_id,
            secret_key=access_key_secret,
            secure=secure
        )
        self.bucket_name = bucket_name
        self.oss_download_host = oss_download_host
        self._ensure_bucket_exists(bucket_name)

    def _ensure_bucket_exists(self, bucket_name: str) -> None:
        """
        确保 bucket 存在,如果不存在则创建
        
        :param bucket_name: bucket 名称
        :raise Exception: 如果 bucket 创建失败
        """
        try:
            if not self.client.bucket_exists(bucket_name):
                logger.info(f"⚠️ Bucket '{bucket_name}' 不存在,正在创建...")
                self.client.make_bucket(bucket_name)
                logger.info(f"✅ Bucket '{bucket_name}' 创建成功")
            else:
                logger.debug(f"✅ Bucket '{bucket_name}' 已存在")
        except S3Error as e:
            logger.error(f"❌ Bucket 操作失败: {e}")
            raise Exception(f"Failed to ensure bucket exists: {e}") from e

    def upload_file(
        self,
        file_path: str,
        file_content: bytes,
        content_type: str = "application/octet-stream",
    ) -> str:
        """
        上传文件到 MinIO
        
        :param file_path: 文件在 bucket 中的路径
        :param file_content: 文件内容 (字节)
        :param content_type: 文件 MIME 类型
        :return: 文件的访问 URL
        """
        try:
            from io import BytesIO
            
            # 使用 BytesIO 包装文件内容
            file_data = BytesIO(file_content)
            file_size = len(file_content)
            
            logger.debug(f"📤 上传文件: {file_path}, 大小: {file_size} 字节")
            
            # 上传文件
            self.client.put_object(
                bucket_name=self.bucket_name,
                object_name=file_path,
                data=file_data,
                length=file_size,
                content_type=content_type
            )
            
            # 生成访问 URL
            file_url = f"{self.oss_download_host}/{self.bucket_name}/{file_path}"
            logger.debug(f"✅ 文件上传成功: {file_url}")
            
            return file_url
            
        except S3Error as e:
            logger.error(f"❌ 文件上传失败: {e}")
            raise Exception(f"Failed to upload file: {e}") from e
