"""
测试使用 minio 库替代 boto3 访问 MinIO
"""
from minio import Minio
from minio.error import S3Error

class MinIOService:
    """使用 minio 库的服务"""
    
    def __init__(self, endpoint, access_key, secret_key, bucket_name):
        # 移除 http:// 前缀
        endpoint = endpoint.replace('http://', '').replace('https://', '')
        
        self.client = Minio(
            endpoint,
            access_key=access_key,
            secret_key=secret_key,
            secure=False  # HTTP 模式
        )
        self.bucket_name = bucket_name
        self._ensure_bucket_exists()
    
    def _ensure_bucket_exists(self):
        """确保 bucket 存在"""
        try:
            if not self.client.bucket_exists(self.bucket_name):
                self.client.make_bucket(self.bucket_name)
                print(f"✅ 创建 bucket: {self.bucket_name}")
            else:
                print(f"✅ Bucket 已存在: {self.bucket_name}")
        except S3Error as e:
            print(f"❌ Bucket 检查失败: {e}")
            raise

# 测试
service = MinIOService(
    endpoint="localhost:9000",
    access_key="minioadmin",
    secret_key="minioadmin",
    bucket_name="workflow"
)

print("✅ MinIO 服务初始化成功 (使用 minio 库)")
