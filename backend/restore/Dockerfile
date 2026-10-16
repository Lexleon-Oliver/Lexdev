FROM postgres:16-alpine

RUN apk add --no-cache python3

WORKDIR /opt/systempro-restore

COPY systempro_restore.py /opt/systempro-restore/systempro_restore.py

ENTRYPOINT ["python3", "/opt/systempro-restore/systempro_restore.py"]
