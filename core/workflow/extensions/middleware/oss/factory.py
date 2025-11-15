"""
OSS Service Factory module.

This module provides a factory class for creating OSS service instances
based on configuration environment variables.
"""

import os

from workflow.extensions.middleware.factory import ServiceFactory
from workflow.extensions.middleware.oss.base import BaseOSSService
from workflow.extensions.middleware.oss.manager import (
    IFlyGatewayStorageClient,
    S3Service,
)
from workflow.extensions.middleware.oss.minio_adapter import MinIOService
from workflow.extensions.middleware.utils import ServiceType


class OSSServiceFactory(ServiceFactory):
    """
    Factory class for creating OSS service instances.

    This factory creates appropriate OSS service implementations based on
    the OSS_TYPE environment variable. It supports both S3-compatible
    storage and iFly Gateway Storage services.
    """

    name = ServiceType.OSS_SERVICE

    def __init__(self) -> None:
        """
        Initialize the OSS service factory.

        Sets up the factory with the base OSS service class and initializes
        the client attribute to None.
        """
        super().__init__(BaseOSSService)
        self.client: BaseOSSService | None = None

    def create(self) -> BaseOSSService:
        """
        Create and configure an OSS service instance.

        Creates an OSS service instance based on the OSS_TYPE environment variable.
        Supports 's3' type for S3-compatible storage and defaults to iFly Gateway
        Storage for other values.
        
        Uses MinIOService (minio library) instead of boto3 for better compatibility
        with local MinIO installations.

        :return: Configured OSS service instance
        :raises AssertionError: If client creation fails
        """
        oss_type = os.getenv("OSS_TYPE", "ifly_gateway_storage")
        use_minio_lib = os.getenv("USE_MINIO_LIB", "1") == "1"  # 默认使用 minio 库
        
        if oss_type == "s3":
            if use_minio_lib:
                # 使用 minio 库 (兼容性更好)
                self.client = MinIOService(
                    endpoint=os.getenv("OSS_ENDPOINT") or "",
                    access_key_id=os.getenv("OSS_ACCESS_KEY_ID") or "",
                    access_key_secret=os.getenv("OSS_ACCESS_KEY_SECRET") or "",
                    bucket_name=os.getenv("OSS_BUCKET_NAME") or "",
                    oss_download_host=os.getenv("OSS_DOWNLOAD_HOST") or "",
                )
            else:
                # 使用 boto3 (适用于 AWS S3 和新版 MinIO)
                self.client = S3Service(
                    endpoint=os.getenv("OSS_ENDPOINT") or "",
                    access_key_id=os.getenv("OSS_ACCESS_KEY_ID") or "",
                    access_key_secret=os.getenv("OSS_ACCESS_KEY_SECRET") or "",
                    bucket_name=os.getenv("OSS_BUCKET_NAME") or "",
                    oss_download_host=os.getenv("OSS_DOWNLOAD_HOST") or "",
                )
        else:
            self.client = IFlyGatewayStorageClient(
                endpoint=os.getenv("OSS_ENDPOINT") or "",
                access_key_id=os.getenv("OSS_ACCESS_KEY_ID") or "",
                access_key_secret=os.getenv("OSS_ACCESS_KEY_SECRET") or "",
                bucket_name=os.getenv("OSS_BUCKET_NAME") or "",
                ttl=int(os.getenv("OSS_TTL") or "0"),
            )
        # Narrow type for mypy: client must be set
        assert self.client is not None
        return self.client
