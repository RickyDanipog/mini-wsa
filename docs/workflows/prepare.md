# Check prerequisites

Check the tools needed for the workflow you will run:

- Start the application with `docker compose version` and a running Docker daemon.
- Run the generator with JDK 21. Check it with `java -version`.
- Build and test with `java -version` (JDK 21), `mvn --version`, and Docker.

If a tool is missing, tell the user which workflow needs it.
