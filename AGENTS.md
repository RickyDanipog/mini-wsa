# Mini WSA Engineering Guidelines

Mini WSA is an Akamai backend take-home. The required HTTP contracts and scoring
matrix are described in [README.md](README.md). The previous distributed
implementation is preserved on the `microservices` branch.

- Keep one Java 21 / Spring Boot application and one PostgreSQL database.
- Validate the full input batch before starting the write transaction.
- Keep scoring deterministic and independent of Spring and storage.
- Use parameterized SQL. Keep filtering, aggregation, and pagination in the database.
- Keep the generator independent so it can produce input files or exercise the public API.
- Run `mvn clean verify` with Docker for the PostgreSQL integration tests.
- Use small, logical commits with meaningful messages.
