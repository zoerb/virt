CREATE EXTENSION postgis;

CREATE SEQUENCE users_id_seq;
CREATE TABLE users (
    id integer DEFAULT nextval('users_id_seq') PRIMARY KEY,
    username text,
    password char(60)
);
ALTER SEQUENCE users_id_seq OWNED BY users.id;

CREATE SEQUENCE channels_id_seq;
CREATE TABLE channels (
    id integer DEFAULT nextval('channels_id_seq') PRIMARY KEY,
    name text,
    location geography(POINT,4326)
);
ALTER SEQUENCE channels_id_seq OWNED BY channels.id;

CREATE INDEX channels_gix
  ON channels
  USING GIST (location);

CREATE SEQUENCE messages_id_seq;
CREATE TABLE messages (
    id integer DEFAULT nextval('messages_id_seq') PRIMARY KEY,
    channel_id integer references channels,
    timestamp timestamp default current_timestamp,
    --user_id integer references users,
    username text,
    message text
);
ALTER SEQUENCE messages_id_seq OWNED BY messages.id;
