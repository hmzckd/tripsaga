CREATE USER flight WITH PASSWORD 'flight';
CREATE DATABASE flight OWNER flight;

CREATE USER hotel WITH PASSWORD 'hotel';
CREATE DATABASE hotel OWNER hotel;

CREATE USER payment WITH PASSWORD 'payment';
CREATE DATABASE payment OWNER payment;

CREATE USER booking WITH PASSWORD 'booking';
CREATE DATABASE booking OWNER booking;

REVOKE CONNECT ON DATABASE flight, hotel, payment, booking FROM PUBLIC;
