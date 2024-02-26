CREATE TABLE phone(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  number varchar(255) NOT NULL
);

CREATE TABLE phone_details(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  phone_id uuid NOT NULL,
  provider varchar(255) NOT NULL,
  technology varchar(255) NOT NULL,
  FOREIGN KEY (phone_id) REFERENCES phone ON DELETE CASCADE
);

CREATE TABLE country(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  name varchar(255) NOT NULL
);

CREATE TABLE city(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  country_id uuid NOT NULL,
  name varchar(255) NOT NULL,
  FOREIGN KEY (country_id) REFERENCES country ON DELETE CASCADE
);

CREATE TABLE town(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  country_id uuid NOT NULL,
  name varchar(255) NOT NULL,
  FOREIGN KEY (country_id) REFERENCES country ON DELETE CASCADE
);

CREATE TABLE author(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  name varchar(255) NOT NULL
);

CREATE TABLE book(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  created_at timestamp(9) NOT NULL DEFAULT localtimestamp(),
  title varchar(255) NOT NULL
);

CREATE TABLE author_book(
  id uuid NOT NULL DEFAULT random_uuid() PRIMARY KEY,
  author_id uuid NOT NULL,
  book_id uuid NOT NULL,
  FOREIGN KEY (author_id) REFERENCES author ON DELETE CASCADE,
  FOREIGN KEY (book_id) REFERENCES book ON DELETE CASCADE,
  UNIQUE (author_id, book_id)
);
