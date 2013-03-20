--
-- dbSchema.sql - database schema to separate path (output point of model)
--                from collector (attached drainage pipes) and field
--                attribute of each output paket. In addition, index
--                numbers to distinguish components of a vector output
--                are introduced
--
--
drop database DATABASE_NAME;
create database DATABASE_NAME;

CREATE TABLE [DATABASE_NAME].[dbo].[single_value_result] (
	[id] [numeric](19, 0) IDENTITY(1,1) NOT NULL,
	[version] [numeric](19, 0) NOT NULL,
	[collector_id] [numeric](19, 0) NULL,
	[field_id] [numeric](19, 0) NULL,
	[iteration] [int] NOT NULL,
	[path_id] [numeric](19, 0) NOT NULL,
	[period] [int] NOT NULL,
	[simulation_run_id] [numeric](19, 0) NOT NULL,
	[value] [float] NOT NULL,
	[value_index] [int] NOT NULL,
	[date] [datetime],
PRIMARY KEY CLUSTERED ( [id] ASC ) WITH (PAD_INDEX  = OFF, IGNORE_DUP_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY];


CREATE NONCLUSTERED INDEX [single_value_result_x1] ON [DATABASE_NAME].[dbo].[single_value_result]
(
	[simulation_run_id] ASC,
	[period] ASC,
	[path_id] ASC,
	[collector_id] ASC,
	[field_id] ASC,
	[value] ASC,
	[iteration] ASC
) WITH (PAD_INDEX  = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF) ON [PRIMARY];

CREATE NONCLUSTERED INDEX [single_value_result_x2] ON [DATABASE_NAME].[dbo].[single_value_result]
(
	[simulation_run_id] ASC,
	[period] ASC,
	[path_id] ASC,
	[collector_id] ASC,
	[field_id] ASC,
	[iteration] ASC,
	[value] ASC
) WITH (PAD_INDEX  = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF) ON [PRIMARY];


CREATE TABLE [DATABASE_NAME].[dbo].[path_mapping](
	[id] [numeric](19, 0) IDENTITY(1,1) NOT NULL,
	[version] [numeric](19, 0) NOT NULL,
	[path_name] [varchar](255) COLLATE Latin1_General_CI_AS NOT NULL,
PRIMARY KEY CLUSTERED ( [id] ASC ) WITH (PAD_INDEX  = OFF, IGNORE_DUP_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY];

CREATE UNIQUE NONCLUSTERED INDEX [path_mapping_x1] ON [DATABASE_NAME].[dbo].[path_mapping]
(
	[path_name] ASC
) WITH (PAD_INDEX  = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF) ON [PRIMARY]

CREATE TABLE [DATABASE_NAME].[dbo].[collector_mapping](
	[id] [numeric](19, 0) IDENTITY(1,1) NOT NULL,
	[version] [numeric](19, 0) NOT NULL,
	[collector_name] [varchar](255) COLLATE Latin1_General_CI_AS NOT NULL,
PRIMARY KEY CLUSTERED ( [id] ASC ) WITH (PAD_INDEX  = OFF, IGNORE_DUP_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY];

CREATE UNIQUE NONCLUSTERED INDEX [collector_mapping_x1] ON [DATABASE_NAME].[dbo].[collector_mapping]
(
	[collector_name] ASC
) WITH (PAD_INDEX  = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF) ON [PRIMARY]

CREATE TABLE [DATABASE_NAME].[dbo].[field_mapping](
	[id] [numeric](19, 0) IDENTITY(1,1) NOT NULL,
	[version] [numeric](19, 0) NOT NULL,
	[field_name] [varchar](255) COLLATE Latin1_General_CI_AS NOT NULL,
PRIMARY KEY CLUSTERED ( [id] ASC ) WITH (PAD_INDEX  = OFF, IGNORE_DUP_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY];

CREATE UNIQUE NONCLUSTERED INDEX [field_mapping_x1] ON [DATABASE_NAME].[dbo].[field_mapping]
(
	[field_name] ASC
) WITH (PAD_INDEX  = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, IGNORE_DUP_KEY = OFF, ONLINE = OFF) ON [PRIMARY];

--
-- view to access all data via symbolic names
--
EXEC DATABASE_NAME..sp_executesql N'
CREATE VIEW [dbo].[symbolic_value_result]
AS
SELECT DATABASE_NAME.dbo.single_value_result.id, DATABASE_NAME.dbo.single_value_result.simulation_run_id, DATABASE_NAME.dbo.single_value_result.iteration, DATABASE_NAME.dbo.single_value_result.period,
                      DATABASE_NAME.dbo.path_mapping.path_name, DATABASE_NAME.dbo.field_mapping.field_name, DATABASE_NAME.dbo.collector_mapping.collector_name
FROM DATABASE_NAME.dbo.collector_mapping INNER JOIN
                      DATABASE_NAME.dbo.single_value_result ON DATABASE_NAME.dbo.collector_mapping.collector_name = DATABASE_NAME.dbo.single_value_result.collector_id INNER JOIN
                      DATABASE_NAME.dbo.field_mapping ON DATABASE_NAME.dbo.single_value_result.field_id = DATABASE_NAME.dbo.field_mapping.field_name INNER JOIN
                      DATABASE_NAME.dbo.path_mapping ON DATABASE_NAME.dbo.single_value_result.path_id = DATABASE_NAME.dbo.path_mapping.path_name';