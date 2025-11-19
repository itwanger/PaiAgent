FROM python:3.11-slim
WORKDIR /opt/core/agent

ENV PATH=$PATH:/opt/core/agent
ENV PYTHONPATH /opt/core/agent:/opt/core

# Install uv
RUN pip install uv --no-cache-dir -i https://mirrors.huaweicloud.com/repository/pypi/simple/

COPY core/common/ /opt/core/common/
COPY core/agent/ /opt/core/agent/
COPY core/agent/config.env.example /opt/core/agent/config.env

# Install dependencies using uv
# Export and install common dependencies
RUN cd /opt/core/common && uv export --format requirements-txt --no-dev > requirements.txt && \
    uv pip install -r requirements.txt --system  --index-url https://mirrors.huaweicloud.com/repository/pypi/simple

# Export and install agent dependencies
RUN cd /opt/core/agent && uv export --format requirements-txt --no-dev > requirements.txt && \
    uv pip install -r requirements.txt --system  --index-url https://mirrors.huaweicloud.com/repository/pypi/simple

CMD ["python", "main.py"]
