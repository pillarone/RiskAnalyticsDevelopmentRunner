-- These commands need to be executed by the DBA (path to tablespace can change):
-- CREATE TABLESPACE pillarone DATAFILE '/u01/app/oracle/oradata/XE/pillarone.dbf' SIZE 100M AUTOEXTEND ON NEXT 100M MAXSIZE 10G;
-- CREATE USER p1rat IDENTIFIED BY p1rat DEFAULT TABLESPACE pillarone;
-- GRANT connect, resource, create any directory, drop any directory TO p1rat;
-- Only required for development:
--  GRANT create tablespace, drop tablespace, create view TO p1rat;
DROP TABLESPACE pillarone INCLUDING CONTENTS AND DATAFILES;
CREATE TABLESPACE pillarone DATAFILE '/u01/app/oracle/oradata/XE/pillarone.dbf' SIZE 100M AUTOEXTEND ON NEXT 100M MAXSIZE 10G;

DROP SEQUENCE result_id_sequence;

CREATE SEQUENCE result_id_sequence
START WITH 1
INCREMENT BY 1
NOMAXVALUE;

CREATE TABLE single_value_result (
id NUMBER(19) NOT NULL PRIMARY KEY,
version NUMBER(19) NOT NULL,
iteration int NOT NULL,
path_id NUMBER(19) NOT NULL,
collector_id NUMBER(19) NOT NULL,
value_index int NOT NULL,
field_id NUMBER(19) NOT NULL,
period int NOT NULL,
simulation_run_id NUMBER(19) NOT NULL,
value FLOAT(126) NOT NULL,
date_time NUMBER(19) DEFAULT NULL
) NOLOGGING;

CREATE INDEX single_value_result_x1 on single_value_result(simulation_run_id, period, path_id, collector_id, field_id, value, iteration);
CREATE INDEX single_value_result_x2 on single_value_result(simulation_run_id, period, path_id, collector_id, field_id, iteration, value);

CREATE TABLE path_mapping (
id NUMBER(19) NOT NULL PRIMARY KEY,
version NUMBER(19) NOT NULL,
path_name VARCHAR2(255) NOT NULL
);
CREATE UNIQUE INDEX path_mapping_x1 on path_mapping(path_name);

CREATE TABLE collector_mapping (
id NUMBER(19) NOT NULL PRIMARY KEY,
version NUMBER(19) NOT NULL,
collector_name VARCHAR2(255) NOT NULL
);
CREATE UNIQUE INDEX  collector_mapping_x1 on collector_mapping(collector_name);

CREATE TABLE field_mapping (
id NUMBER(19) NOT NULL PRIMARY KEY,
version NUMBER(19) NOT NULL,
field_name VARCHAR2(255) NOT NULL
);
CREATE UNIQUE INDEX field_mapping_x1 on field_mapping(field_name);

-- CREATE VIEW symbolic_value_result AS (
-- SELECT s.id, p.path_name AS "path", c.collector_name AS "collector", f.field_name AS "field", s.simulation_run_id, s.period, s.value, s.date_time, s.iteration
-- FROM single_value_result s, path_mapping p, collector_mapping c, field_mapping f
-- WHERE s.path_id = p.id
-- AND s.collector_id = c.id
-- AND s.field_id = f.id);

--TODO: partitions, view