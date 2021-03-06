drop table COP_WORKFLOW_INSTANCE_ERROR;
drop table COP_WAIT;
drop table COP_RESPONSE;
drop table COP_QUEUE;
drop table COP_AUDIT_TRAIL_EVENT;
drop table COP_ADAPTERCALL;
drop table COP_LOCK;
drop table COP_WORKFLOW_INSTANCE;

--
-- COP_WORKFLOW_INSTANCE
--
create table COP_WORKFLOW_INSTANCE  (
   ID           		VARCHAR(128) not null,
   STATE                TINYINT not null,
   PRIORITY             TINYINT not null,
   LAST_MOD_TS          TIMESTAMP not null,
   PPOOL_ID      		VARCHAR(32) not null,
   DATA					MEDIUMTEXT null,
   OBJECT_STATE			MEDIUMTEXT null,
   CS_WAITMODE			TINYINT,
   MIN_NUMB_OF_RESP		SMALLINT,
   NUMB_OF_WAITS		SMALLINT,
   TIMEOUT				TIMESTAMP,
   CREATION_TS			TIMESTAMP not null,
   CLASSNAME			VARCHAR(512) not null,
   PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
 
--
-- COP_WORKFLOW_INSTANCE_ERROR
--
create table COP_WORKFLOW_INSTANCE_ERROR (
   WORKFLOW_INSTANCE_ID		VARCHAR(128)	not null,
   EXCEPTION				TEXT			not null,
   ERROR_TS     	   		TIMESTAMP       not null
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

create index IDX_COP_WFID_WFID on COP_WORKFLOW_INSTANCE_ERROR (
   WORKFLOW_INSTANCE_ID
);

ALTER TABLE COP_WORKFLOW_INSTANCE_ERROR ADD FOREIGN KEY (WORKFLOW_INSTANCE_ID) REFERENCES COP_WORKFLOW_INSTANCE (ID) ON DELETE CASCADE;

--
-- COP_RESPONSE
--
create table COP_RESPONSE  (
   RESPONSE_ID		VARCHAR(128) not null,
   CORRELATION_ID	VARCHAR(128) not null,
   RESPONSE_TS		TIMESTAMP not null,
   RESPONSE			MEDIUMTEXT,
   RESPONSE_TIMEOUT	 TIMESTAMP,
   RESPONSE_META_DATA VARCHAR(4000),
   PRIMARY KEY (RESPONSE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

 
create index IDX_COP_RESP_CID on COP_RESPONSE (
   CORRELATION_ID
);

--
-- COP_WAIT
--
create table COP_WAIT (
   	CORRELATION_ID			VARCHAR(128) not null,
   	WORKFLOW_INSTANCE_ID  	VARCHAR(128) not null,
	MIN_NUMB_OF_RESP		SMALLINT not null,
	TIMEOUT_TS				TIMESTAMP null,
   	STATE					TINYINT not null,
    PRIORITY            	TINYINT not null,
    PPOOL_ID      			VARCHAR(32) not null,
    PRIMARY KEY (CORRELATION_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


create index IDX_COP_WAIT_WFI_ID on COP_WAIT (
   WORKFLOW_INSTANCE_ID
);

ALTER TABLE COP_WAIT ADD FOREIGN KEY (WORKFLOW_INSTANCE_ID) REFERENCES COP_WORKFLOW_INSTANCE (ID);

--
-- COP_QUEUE
--
create table COP_QUEUE (
   PPOOL_ID      		VARCHAR(32)					not null,
   PRIORITY             TINYINT                         not null,
   LAST_MOD_TS          TIMESTAMP                       not null,
   WORKFLOW_INSTANCE_ID	VARCHAR(128) 					not null,
   ENGINE_ID            VARCHAR(16) NULL,
   PRIMARY KEY (WORKFLOW_INSTANCE_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

ALTER TABLE COP_QUEUE ADD FOREIGN KEY (WORKFLOW_INSTANCE_ID) REFERENCES COP_WORKFLOW_INSTANCE (ID);

--
-- COP_AUDIT_TRAIL_EVENT
--
create table COP_AUDIT_TRAIL_EVENT (
	SEQ_ID 					BIGINT NOT NULL AUTO_INCREMENT,
	OCCURRENCE				TIMESTAMP NOT NULL,
	CONVERSATION_ID 		VARCHAR(64) NOT NULL,
	LOGLEVEL				TINYINT NOT NULL,
	CONTEXT					VARCHAR(128) NOT NULL,
	INSTANCE_ID				VARCHAR(128) NULL,
	CORRELATION_ID 			VARCHAR(128) NULL,
	TRANSACTION_ID 			VARCHAR(128) NULL,
	LONG_MESSAGE 			LONGTEXT NULL,
	MESSAGE_TYPE			VARCHAR(256) NULL,
    PRIMARY KEY (SEQ_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- 
-- COP_ADAPTERCALL
--
CREATE TABLE COP_ADAPTERCALL (WORKFLOWID  VARCHAR(128) NOT NULL,
                          ENTITYID    VARCHAR(128) NOT NULL,
                          ADAPTERID   VARCHAR(128) NOT NULL,
                          PRIORITY    BIGINT NOT NULL,
                          DEFUNCT     CHAR(1) DEFAULT '0' NOT NULL ,
                          DEQUEUE_TS  TIMESTAMP , 
                          METHODDECLARINGCLASS VARCHAR(1024)  NOT NULL,
                          METHODNAME VARCHAR(1024)  NOT NULL,
                          METHODSIGNATURE VARCHAR(2048)  NOT NULL,
                          ARGS LONGTEXT,
                          PRIMARY KEY (ADAPTERID, WORKFLOWID, ENTITYID))
 ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE INDEX COP_IDX_ADAPTERCALL ON COP_ADAPTERCALL(ADAPTERID, PRIORITY);

--
-- COP_LOCK
--
create table COP_LOCK (
	LOCK_ID 				VARCHAR(128) NOT NULL, 
	CORRELATION_ID 			VARCHAR(128) NOT NULL, 
	WORKFLOW_INSTANCE_ID 	VARCHAR(128) NOT NULL, 
	INSERT_TS 				TIMESTAMP NOT NULL, 
	REPLY_SENT 				CHAR(1) NOT NULL,
    PRIMARY KEY (LOCK_ID,WORKFLOW_INSTANCE_ID)
)
 ENGINE=InnoDB DEFAULT CHARSET=utf8;