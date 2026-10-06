-- Run once (pgAdmin Query Tool on the "postgres" database, or: psql -U postgres -f db/create-databases.sql)
CREATE DATABASE airline_db;
CREATE DATABASE bridge_db;
CREATE DATABASE ibs_db;
-- Tables are created automatically by each Spring Boot service (spring.jpa.hibernate.ddl-auto=update).
