--
-- dbSchema.sql - database schema to separate path (output point of model)
--                from collector (attached drainage pipes) and field
--                attribute of each output paket. In addition, index
--                numbers to distinguish components of a vector output
--                are introduced
--
--;
drop database if exists DATABASE_NAME;
create database DATABASE_NAME;

create table DATABASE_NAME.single_value_result (
id bigint not null auto_increment,
version bigint not null,
iteration int not null,
path_id bigint not null,
collector_id bigint not null,
value_index int not null,
field_id bigint not null,
period int not null,
simulation_run_id bigint not null,
value double not null,
date bigint DEFAULT null,
primary key(id, simulation_run_id)
) engine MyISAM
partition by list(simulation_run_id)
( partition dummy values in (-1) );
create index single_value_result_x1 on DATABASE_NAME.single_value_result(simulation_run_id, period, path_id, collector_id, field_id, value, iteration);
create index single_value_result_x2 on DATABASE_NAME.single_value_result(simulation_run_id, period, path_id, collector_id, field_id, iteration, value);


create table DATABASE_NAME.post_simulation_calculation (
id bigint not null auto_increment,
version bigint not null,
path_id bigint not null,
collector_id bigint not null,
field_id bigint not null,
period int not null,
run_id bigint not null,
result double not null,
key_figure varchar(24) not null,
key_figure_parameter decimal(19,2) null,
primary key(id, run_id)
) engine MyISAM
partition by list(run_id)
( partition dummy values in (-1) );
create index post_simulation_calculation_x1 on DATABASE_NAME.post_simulation_calculation(run_id,key_figure, period, path_id, collector_id, field_id, key_figure_parameter, result);

create table DATABASE_NAME.path_mapping (
id bigint not null auto_increment primary key,
version bigint not null,
path_name varchar(255) binary not null
) engine MyISAM;
create unique index path_mapping_x1 on DATABASE_NAME.path_mapping(path_name);

create table DATABASE_NAME.collector_mapping (
id bigint not null auto_increment primary key,
version bigint not null,
collector_name varchar(255) binary not null
) engine MyISAM;
create unique index collector_mapping_x1 on DATABASE_NAME.collector_mapping(collector_name);

create table DATABASE_NAME.field_mapping (
id bigint not null auto_increment primary key,
version bigint not null,
field_name varchar(255) binary not null
) engine MyISAM;
create unique index field_x1 on DATABASE_NAME.field_mapping(field_name);

--
-- view to access all data via symbolic names
--;
create view DATABASE_NAME.symbolic_value_result as
select s.id, p.path_name as 'path', c.collector_name as 'collector', f.field_name as 'field', s.simulation_run_id, s.period, s.value, s.date, s.iteration
from DATABASE_NAME.single_value_result s, DATABASE_NAME.path_mapping p, DATABASE_NAME.collector_mapping c, DATABASE_NAME.field_mapping f
where s.path_id = p.id
and s.collector_id = c.id
and s.field_id = f.id;