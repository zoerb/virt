CREATE EXTENSION postgis;

CREATE SEQUENCE channels_id_seq;
CREATE TABLE channels (
    id integer DEFAULT nextval('channels_id_seq') PRIMARY KEY,
    name varchar(255),
    location geography(POINT,4326)
);
ALTER SEQUENCE channels_id_seq OWNED BY channels.id;

CREATE INDEX channels_gix
  ON channels
  USING GIST (location);

CREATE SEQUENCE threads_id_seq;
CREATE TABLE threads (
    id integer DEFAULT nextval('threads_id_seq') PRIMARY KEY,
    channel_id integer references channels,
    description varchar(255)
);
ALTER SEQUENCE threads_id_seq OWNED BY threads.id;

CREATE SEQUENCE messages_id_seq;
CREATE TABLE messages (
    id integer DEFAULT nextval('messages_id_seq') PRIMARY KEY,
    channel_id integer references channels,
    thread_id integer references threads,
    timestamp timestamp default current_timestamp,
    userid varchar(31),
    message varchar(1023)
);
ALTER SEQUENCE messages_id_seq OWNED BY messages.id;

--DROP TABLE messages;
--DROP TABLE threads;
--DROP TABLE channels;
