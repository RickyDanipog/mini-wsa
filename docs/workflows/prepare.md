# Check prerequisites

Check the tools needed for the workflow you will run:

- Start the application with `docker compose version` and a running Docker daemon.
- Run the generator with `python3 --version` (Python 3.10 or newer).
- Build and test with `java -version` (JDK 21), `mvn --version`, and Docker.

If a tool is missing, tell the user which workflow needs it.
