CREATE ROLE debezium
WITH
    LOGIN
    REPLICATION
    PASSWORD 'debezium';

GRANT CONNECT
ON DATABASE transport
TO debezium;