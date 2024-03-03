[![CI](https://github.com/JoseLion/spring-r2dbc-relationships/actions/workflows/ci.yml/badge.svg)](https://github.com/JoseLion/spring-r2dbc-relationships/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/JoseLion/spring-r2dbc-relationships)](https://github.com/JoseLion/spring-r2dbc-relationships/blob/main/LICENSE)
[![Known Vulnerabilities](https://snyk.io/test/github/JoseLion/spring-r2dbc-relationships/badge.svg)](https://snyk.io/test/github/JoseLion/spring-r2dbc-relationships)

# Spring R2DBC Relationships

A set of annotations to handle entity relationships when working on [Spring Data R2DBC](https://docs.spring.io/spring-data/relational/reference/r2dbc.html).

As of the release date of this library, Spring R2DBC does not yet offer any mechanism to manage entity relationships. Providing relations is not a trivial problem for R2DBC, as the correct way to populate the relations is by converting and emitting results as received in a single connection. Simply put, it should not collect the results into a `List` to issue queries that populate the relations. However, until R2DBC offers a proper solution, collecting the results is the only option on userland.

Spring R2DBC Relationships leverages the [Entity Callback API](https://docs.spring.io/spring-data/relational/reference/r2dbc/entity-callbacks.html) to intercept the entity mapping and persistence lifecycle. Looking for field annotations and their configuration, it abstracts and generalizes the process to populate, persist, and link the relationships.

## Features

- Simple and intuitive.
- Ultrafast reflection based on Lambda Metafactory.
- Flexible--Works with both mutable and immutable entities:
  - Java Records are supported. You can use getter methods either with or without the `get` prefix.
  - You can either use traditional setters or immutable withers to make updates.
- Support for entity projections.
- Considers any custom R2DBC Repositories.

## Table of Contents

- [Install](#install)
- [Requirements](#requirements)
- [Usage](#usage)
  - [@OneToOne](#onetoone)
  - [@OneToMany](#onetomany)
  - [@ManyToOne](#manytoone)
  - [@ManyToMany](#manytomany)
  - [Projections](#projections)
- [Contributing](#contributing)
- [License](#license)

## Install

_Spring R2DBC Relationships_ is available in [Maven Central](https://central.sonatype.com/). You can also find a link and the latest version on the badge above.

**Gradle**

```gradle
implementation('io.github.joselion:spring-r2dbc-relationships:x.y.z')
```

**Maven**

```xml
<dependency>
  <groupId>io.github.joselion</groupId>
  <artifactId>spring-r2dbc-relationships</artifactId>
  <version>x.y.z</version>
</dependency>
```

## Requirements

_Spring R2DBC Relationships_ requires:

- JDK level 17 and above
- Spring Data R2DBC 3.x and above

## Usage

To add the relational entity callbacks, you must register an `R2dbcRelationshipsCallbacks<T>` bean in your application context. However, _Spring R2DBC Relationships_ provides the `R2dbcRelationshipsAutoConfiguration` configuration, so Spring Boot users only need to add the dependency, and the bean will be automatically registered. If you register the bean manually, ensure the bean remains generic to intercept any entity and ensure your injected `R2dbcTemplate` bean is lazy to avoid dependency cycles.

```java
@Configuration
public class MyApplicationConfiguration {

  @Bean
  public <T> R2dbcRelationshipsCallbacks<T> relationshipsCallbacks(
    final @Lazy R2dbcEntityTemplate template,
    final ApplicationContext context
  ) {
    return new R2dbcRelationshipsCallbacks(templete, context);
  }
}
```

Spring R2DBC is not an ORM, and _Spring R2DBC Relationships_ abides by the same philosophy. In that sense, the library will persist entities when necessary and link them to their relations, but you still need to have all fields that map those links in your entities, a.k.a. the "foreign key" column fields.

### @OneToOne

The `@OneToOne` annotation lets you mark fields to have a one-to-one relationship. The default behavior of the annotation is to populate the field after mapping the entity object, create/update the associated entity, and link the relation by setting the "foreign key" field in the proper entity.

You can use the annotation on both sides of the relationship to achieve a bidirectional association. However, doing so can also lead to unexpected updates when persisting the backreference. To solve this problem, you can set `backreference = true` in the annotation parameters.

#### Example

Given the following tables present in your database:

```sql
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
```

You can use the `@OneToOne` annotation in both `Phone` and `PhoneDetails` entities:

```java
@With
public record Phone(
  @Id UUID id,
  LocalDateTime createdAt,
  String number,
  @OneToOne PhoneDetails details
) {

 // implementation omitted...
}

@With
public record PhoneDetails(
  @Id UUID id,
  LocalDateTime createdAt,
  UUID phoneId,
  @OneToOne(backreference = true) Phone phone,
  String provider,
  String technology
) {

  // implementation omitted...
}
```

> [!NOTE] 
> Notice that the `PhoneDetails` annotation is a backreference to `Phone`. Setting `backreference = true` will also make the annotated field entity read-only by default, meaning it's never persisted or linked. This behavior lets us safely persist a `Phone` instance containing a `PhoneDetails` field that, in turn, includes a `Phone` field, which might not be the same as the first `Phone` instance.

### @OneToMany

The `@OneToMany` annotation lets you mark fields to have a one-to-many relationship. The default behavior of the annotation is to populate the field after mapping the entity object, create/update the children entities, and link the relations by setting the "foreign key" field in each child entity.

You can achieve bidirectional one-to-many relationships using the `@ManyToOne` annotation on the children's side of the relationship. Check the next section for more details on that. There's also a different use case where the children entities already exist, and you only need to link them to the parent without changing the existing entities. You can set `linkOnly = true` in the annotation parameters to achieve said behavior. However, link-only associations will fail if the linked entity does not exist when you create/update the parent.

> [!IMPORTANT]
> The annotation only supports `List<T>` types for now. We'll consider support for more collection types as the use cases present.

#### Example

Given the following tables present in your database:

```sql
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
```

You can use the `@OneToMany` annotation in the `Country` entity:

```java
public record Country(
  @Id UUID id,
  LocalDateTime createdAt,
  String name,
  @OneToMany List<City> cities
) {

 // implementation omitted...
}
```

By default, the annotations will sort the populated list of cities by the `created_at` column in a descendant direction. You can customize the sorting column and direction using the annotation parameters.

#### Handling orphans

The `@OneToMany` annotation handles orphan children removal for you. Meaning it will delete any entities that no longer exist in the list whenever the entity is updated. So, if you want to remove all children, you can pass an empty list to the field and update the entity. Finally, if you don't want to modify the children upon update, you can set the field to `null` to tell the annotation to ignore the field. You can change the orphan removal behavior by setting `keepOrphans = true` in the annotation parameters.

### @ManyToOne

The `@ManyToOne` annotation lets you mark fields to have a many-to-one relationship. As mentioned in the previous section, the many-to-one relationship is the backreference of a one-to-many relationship. Simply put, this annotation lets you have a reference to the parent entity on each child. That said, the default behavior of the annotation is to populate the field after mapping the entity object, but it will not create, update, or link the parent entity. By default, this side of the relationship is read-only. You can change this behavior by setting `persist = true` in the annotation parameters, but remember that changing a single child's parent will affect all the children.

As mentioned above, you can achieve bidirectional many-to-one relationships using the `@OneToMany` annotation on the parent side of the relationship. Check the previous section for more details on that.

#### Example

Using the same database tables as in the [one-to-many example](#example-1), you can use the `@ManyToOne` annotation in the `City` entity:

```java
@With
public record City(
  @Id UUID id,
  LocalDateTime createdAt,
  UUID countryId,
  @ManyToOne Country country,
  String name,
) {

  // implementation omitted...
}
```

> [!Note]
> Notice that having the `countryId` field, which maps to the foreign key column, is required for the relationship to work properly.

### ManyToMany

The `@ManyToMany` annotation lets you mark fields to have a many-to-many relationship. The default behavior of the annotation is to populate the field after mapping the entity object, create/update the associated entities, and link the relations on the join table. The annotation uses the join table transparently, meaning you **don't need** to create an entity type for the join table on your codebase.

You can use the annotation on both sides of the relationship to achieve a bidirectional association. Many-to-many relationships keep track of their associations in a separate join table, so updates to one entity do not impact the others. There's also a different use case where the associated entities already exist, and you only need to link them together without changing the existing entities. You can set `linkOnly = true` in the annotation parameter to achieve said behavior. However, link-only associations will fail if the linked entity does not exist when you create/update the current entity.

> [!IMPORTANT]
> The annotation only supports `List<T>` types for now. We'll consider support for more collection types as the use cases present.

#### Example

Given the following tables present in your database:

```sql
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
```

You can use the `@ManyToMany` annotation in both `Author` and `Book` entities:

```java
@With
public record Author(
  @Id UUID id,
  LocalDateTime createdAt,
  String name
  @ManyToMany List<Book> books
) {

  // implementation omitted...
}

@With
public record Book(
  @Id UUID id,
  LocalDateTime createdAt,
  String title
  @ManyToMany List<Author> authors
) {

  // implementation omitted...
}
```

By default, the annotations will sort the populated list of books/authors by the `created_at` column in a descendant direction. You can customize the sorting column and direction using the annotation parameters.

#### Handling orphans

Usually, many-to-many relationships are not mutually exclusive to each other, meaning that one can exist without the other even when not linked by the join table. In this context, "orphans" refers to all entities no longer associated with the current entity. By default, the `@ManyToMany` annotation will only delete the links to the "orphan" entities in the join table. Similarly to `@OneToMany`, if you want to remove all associations, you can pass an empty list to the field and update the entity. If you don't want to modify the children upon update, you can set the field to `null` to tell the annotation to ignore the field.

However, there's also the case where you manage the associations from one side of the relationship. In this case, you may want the annotation to delete "orphan" entities for you instead of only removing their link. You can achieve said behavior by setting `deleteOrphans = true` in the annotation parameters.

### Projections

Spring Data allows us to use [Entity Projections](https://docs.spring.io/spring-data/relational/reference/repositories/projections.html) right out of the box--there's no need to add anything to the projected type. However, _Spring R2DBC Relationships_ needs the complete entity information so the relationship processors can obtain accurate metadata hidden in the projection.

To use projections on relationship types, you can annotate the type with `@ProjectionOf(..)` and provide the projected type its value parameter. For example, given a `Person` entity that contains a large number of properties, you can create a projection named `PersonMin` with minimum properties:

```java
@With
@ProjectionOf(Person.class)
public record PersonMin(
  UUID id,
  String firstName,
  String lastName,
  Integer age
) {

  // implementation omitted...
}
```

## Contributing

### Something's missing?

Suggestions are always welcome! Please create an [issue](https://github.com/JoseLion/spring-r2dbc-relationships/issues/new) describing the request, feature, or bug. Opening meaningful issues is as helpful as opening Pull Requests.

### Contributions

Pull Requests are very welcome as well! Please fork this repository and open your PR against the `main` branch.

## License

[MIT License](https://github.com/JoseLion/spring-r2dbc-relationships/blob/main/LICENSE)
