FROM ubuntu:latest
LABEL authors="khanh"

ENTRYPOINT ["top", "-b"]