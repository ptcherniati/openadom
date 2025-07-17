FROM alpine:3.20

RUN apk add --no-cache jq

COPY entrypoint.sh /entrypoint.sh
COPY nginx.conf.template /nginx.conf.template

RUN chmod +x /entrypoint.sh