CREATE TABLE phone(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  number varchar(255) NOT NULL
);

CREATE TABLE phone_details(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  phone_id uuid NOT NULL,
  provider varchar(255) NOT NULL,
  technology varchar(255) NOT NULL,
  FOREIGN KEY (phone_id) REFERENCES phone
);
