DROP SEQUENCE result_id_sequence;

CREATE SEQUENCE result_id_sequence
START WITH 1
INCREMENT BY 1
NOMAXVALUE;

DROP TABLE single_value_result;

CREATE TABLE single_value_result (
id NUMBER(19) NOT NULL,
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
);

--TODO: path/field tables, indices, view, tablespace, partitions