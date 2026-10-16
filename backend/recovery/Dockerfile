FROM python:3.13-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN pip install --no-cache-dir \
    cryptography==46.0.4 \
    argon2-cffi==25.1.0

WORKDIR /opt/systempro-recovery

COPY systempro_recovery.py /opt/systempro-recovery/systempro_recovery.py
COPY tests /opt/systempro-recovery/tests

ENTRYPOINT ["python3", "/opt/systempro-recovery/systempro_recovery.py"]
